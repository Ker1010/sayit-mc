package com.sayitmc.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One player's round of the game, server thread only: every new item they get must be said in the server's
 * language before its timer runs out, or it is cleared. Dropping the item on the ground stops (resets) its timer;
 * putting it anywhere else (a chest, a bundle, the crafting grid) does not save it.
 */
public final class PlayerGame {
	/** What the game does to the player; backed by the real player on the server and by a fake in tests. */
	public interface Actions {
		void startChallenge(int id, String itemId, int seconds);

		void endChallenge(int id, Outcome outcome);

		void clearItem(String itemId);

		/** A stashed item whose time ran out came back into the inventory. */
		void clearStashedItem(String itemId);
	}

	/** Covers network delay and the recognizer finishing the last words. */
	static final int GRACE_TICKS = 30;
	private static final int MAX_QUEUED = 16;

	private final Deque<String> queue = new ArrayDeque<>();
	/** Item ids held on the previous tick; null means the next tick only takes a baseline. */
	private Set<String> previous;
	private boolean enabled;
	private Running running;
	/** Items whose time ran out while stashed away, until when they are cleared on coming back. */
	private final Map<String, Long> stashedOverdue = new HashMap<>();
	private int nextId = 1;

	private static final class Running {
		final int id;
		final String itemId;
		/** False while waiting for the client to load speech and open the microphone. */
		boolean counting;
		long since;
		/** The word as the client shows it, for the name tag. */
		String word = "";

		Running(int id, String itemId, long since) {
			this.id = id;
			this.itemId = itemId;
			this.since = since;
		}
	}

	/** The client has the mod; {@code enabled} is whether this player plays. */
	public void hello(boolean enabled, Actions actions) {
		if (!enabled) {
			reset(actions);
		}

		this.enabled = enabled;
	}

	/** The item of the running challenge, or null. */
	public String runningItem() {
		return running == null ? null : running.itemId;
	}

	public boolean isPlaying() {
		return enabled;
	}

	/**
	 * @param held item ids currently in the player's inventory (including the stack on the cursor)
	 * @param learned whether this player already learned an item in the server's language
	 * @param droppedNow whether the player just dropped this item on the ground (or died)
	 */
	public void tick(long now, Set<String> held, Predicate<String> learned, Predicate<String> droppedNow,
			ServerConfig config, Actions actions) {
		if (!isPlaying()) {
			previous = null;
			return;
		}

		stashedOverdue.values().removeIf(until -> until < now);
		boolean baseline = previous == null;

		if (!baseline) {
			for (String id : previous) {
				if (held.contains(id)) {
					continue;
				}

				// Gone from the inventory. Only dropping it on the ground resets its timer.
				boolean dropped = droppedNow.test(id);

				if (isRunning(id)) {
					if (dropped) {
						finish(Outcome.DROPPED, actions);
					}
				} else if (queue.remove(id) && !dropped) {
					stash(id, now, config);
				}
			}
		}

		for (String id : held) {
			boolean isNew = baseline ? config.quizItemsAlreadyInInventory : !previous.contains(id);

			if (!isNew || isRunning(id)) {
				continue;
			}

			if (stashedOverdue.remove(id) != null) {
				actions.clearStashedItem(id);
			} else if (!learned.test(id) && !queue.contains(id) && queue.size() < MAX_QUEUED) {
				queue.add(id);
			}
		}

		previous = held;

		if (running != null) {
			boolean away = !held.contains(running.itemId);

			if (!running.counting && now - running.since > config.waitForClientSeconds * 20L) {
				finish(Outcome.CANCELLED, actions);
			} else if (running.counting && now - running.since > config.secondsToAnswer * 20L + GRACE_TICKS) {
				timeUp(!away, now, config, actions);
			}
		}

		while (running == null && !queue.isEmpty()) {
			String id = queue.poll();

			if (held.contains(id) && !learned.test(id)) {
				running = new Running(nextId++, id, now);
				actions.startChallenge(running.id, id, config.secondsToAnswer);
			}
		}
	}

	/** The client is listening now, so the countdown starts. {@code word} is the item's name the player must say. */
	public void listening(int id, long now, String word) {
		if (running != null && running.id == id && !running.counting) {
			running.counting = true;
			running.since = now;
			running.word = cleanWord(word);
		}
	}

	/**
	 * The word comes from the player's client and is shown to everyone, so a modified client must not be able to
	 * slip in formatting codes, line breaks or a long message.
	 */
	static String cleanWord(String word) {
		String plain = word.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").strip();
		return plain.length() > 32 ? plain.substring(0, 32) : plain;
	}

	/** Seconds left on the running countdown, or -1 when nothing is counting down. */
	public int secondsLeft(long now, ServerConfig config) {
		if (running == null || !running.counting) {
			return -1;
		}

		long ticksLeft = config.secondsToAnswer * 20L - (now - running.since);
		return (int) Math.max(0, (ticksLeft + 19) / 20);
	}

	/** The word the player is being asked, as their client named it. */
	public String runningWord() {
		return running == null ? "" : running.word;
	}

	/**
	 * The client's verdict. A wrong quiz answer counts like running out of time; an unjudgeable one
	 * (e.g. no translation) ends the challenge without touching the item.
	 *
	 * @param itemHeld whether the item is in the player's inventory right now
	 */
	public void answer(int id, Verdict verdict, boolean itemHeld, Consumer<String> markLearned, long now,
			ServerConfig config, Actions actions) {
		if (running == null || running.id != id) {
			return;
		}

		switch (verdict) {
			case CORRECT -> {
				markLearned.accept(running.itemId);
				finish(Outcome.CORRECT, actions);
			}
			case WRONG -> timeUp(itemHeld, now, config, actions);
			case UNJUDGEABLE -> finish(Outcome.CANCELLED, actions);
		}
	}

	/** Drops the running challenge and the queue; the current inventory becomes the new baseline. */
	public void reset(Actions actions) {
		if (running != null) {
			finish(Outcome.CANCELLED, actions);
		}

		queue.clear();
		stashedOverdue.clear();
		previous = null;
	}

	/** Time ran out (or the quiz answer was wrong): the item is cleared, now or when it comes back. */
	private void timeUp(boolean itemHeld, long now, ServerConfig config, Actions actions) {
		if (!config.clearItemsOnTimeout) {
			finish(Outcome.TIMED_OUT, actions);
			return;
		}

		if (itemHeld) {
			actions.clearItem(running.itemId);
		} else {
			stash(running.itemId, now, config);
		}

		finish(Outcome.CLEARED, actions);
	}

	/** Remembers an item that left without being dropped, so it is cleared when it comes back. */
	private void stash(String itemId, long now, ServerConfig config) {
		if (config.clearItemsOnTimeout) {
			stashedOverdue.put(itemId, now + config.rememberStashedItemsMinutes * 60L * 20L);
		}
	}

	private boolean isRunning(String itemId) {
		return running != null && running.itemId.equals(itemId);
	}

	private void finish(Outcome outcome, Actions actions) {
		int id = running.id;
		running = null;
		actions.endChallenge(id, outcome);
	}
}
