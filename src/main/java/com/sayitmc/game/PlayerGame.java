package com.sayitmc.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One player's round of the game, server thread only: every new item they get must be said in their language
 * before its timer runs out, or it is cleared. Dropping the item stops (resets) its timer.
 */
public final class PlayerGame {
	/** What the game does to the player; backed by the real player on the server and by a fake in tests. */
	public interface Actions {
		void startChallenge(int id, String itemId, int seconds);

		void endChallenge(int id, Outcome outcome);

		void clearItem(String itemId);
	}

	/** Covers network delay and the recognizer finishing the last words. */
	static final int GRACE_TICKS = 30;
	private static final int MAX_QUEUED = 16;

	private final Deque<String> queue = new ArrayDeque<>();
	/** Item ids held on the previous tick; null means the next tick only takes a baseline. */
	private Set<String> previous;
	private String language;
	private boolean enabled;
	private Running running;
	private int nextId = 1;

	private static final class Running {
		final int id;
		final String itemId;
		/** False while waiting for the client to load speech and open the microphone. */
		boolean counting;
		long since;

		Running(int id, String itemId, long since) {
			this.id = id;
			this.itemId = itemId;
			this.since = since;
		}
	}

	/** The client said which language it is learning and whether it wants to play. */
	public void hello(String language, boolean enabled, Actions actions) {
		if (!Objects.equals(this.language, language) || !enabled) {
			reset(actions);
		}

		this.language = language;
		this.enabled = enabled;
	}

	public String language() {
		return language;
	}

	public boolean isPlaying() {
		return enabled && language != null;
	}

	/**
	 * @param held item ids currently in the player's inventory (including the stack on the cursor)
	 * @param learned whether an item is already learned in this player's language
	 */
	public void tick(long now, Set<String> held, Predicate<String> learned, ServerConfig config, Actions actions) {
		if (!isPlaying()) {
			previous = null;
			return;
		}

		boolean baseline = previous == null;

		for (String id : held) {
			boolean isNew = baseline ? config.quizItemsAlreadyInInventory : !previous.contains(id);

			if (isNew && !learned.test(id) && !queue.contains(id) && !isRunning(id) && queue.size() < MAX_QUEUED) {
				queue.add(id);
			}
		}

		previous = held;

		if (running != null) {
			if (!held.contains(running.itemId)) {
				finish(Outcome.DROPPED, actions);
			} else if (!running.counting && now - running.since > config.waitForClientSeconds * 20L) {
				finish(Outcome.CANCELLED, actions);
			} else if (running.counting && now - running.since > config.secondsToAnswer * 20L + GRACE_TICKS) {
				if (config.clearItemsOnTimeout) {
					actions.clearItem(running.itemId);
					finish(Outcome.CLEARED, actions);
				} else {
					finish(Outcome.TIMED_OUT, actions);
				}
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

	/** The client is listening now, so the countdown starts. */
	public void listening(int id, long now) {
		if (running != null && running.id == id && !running.counting) {
			running.counting = true;
			running.since = now;
		}
	}

	/**
	 * The client's verdict: {@code correct}, or that it cannot judge (no microphone, no speech model,
	 * no translation), which ends the challenge without clearing anything.
	 */
	public void answer(int id, boolean correct, Consumer<String> markLearned, Actions actions) {
		if (running == null || running.id != id) {
			return;
		}

		if (correct) {
			markLearned.accept(running.itemId);
		}

		finish(correct ? Outcome.CORRECT : Outcome.CANCELLED, actions);
	}

	/** Drops the running challenge and the queue; the current inventory becomes the new baseline. */
	public void reset(Actions actions) {
		if (running != null) {
			finish(Outcome.CANCELLED, actions);
		}

		queue.clear();
		previous = null;
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
