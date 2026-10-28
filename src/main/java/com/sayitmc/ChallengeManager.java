package com.sayitmc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.sayitmc.speech.SpeechService;

/** Queues new items and runs their challenges one at a time. Main thread only. */
public final class ChallengeManager {
	private static final int MAX_QUEUED = 8;

	private final SpeechService speech;
	private final Deque<ItemStack> queue = new ArrayDeque<>();
	/** When each item was last asked, so a missed word is not asked again right away. */
	private final Map<String, Long> lastAsked = new HashMap<>();
	private Challenge current;

	public ChallengeManager(SpeechService speech) {
		this.speech = speech;
	}

	/** Asks for this item unless it is already learned, queued, or was asked very recently. */
	public void offer(ItemStack stack, boolean force) {
		SayItConfig config = SayItConfig.get();
		String id = itemId(stack);
		Long asked = lastAsked.get(id);
		long now = System.currentTimeMillis();

		if (!force && (LearnedItems.contains(config.language, id)
				|| asked != null && now - asked < config.retryCooldownSeconds * 1000L)) {
			return;
		}

		boolean pending = current != null && current.itemId.equals(id) || queue.stream().anyMatch(s -> itemId(s).equals(id));

		if (!pending && queue.size() < MAX_QUEUED) {
			queue.add(stack.copyWithCount(1));
			lastAsked.put(id, now);
		}
	}

	public void tick() {
		SayItConfig config = SayItConfig.get();
		long now = System.currentTimeMillis();

		if (current != null) {
			current.tick(speech, config, now);

			if (current.isOver(now)) {
				if (current.wasCorrect()) {
					LearnedItems.add(current.languageCode, current.itemId);
				}

				current = null;
			}
		}

		while (current == null && !queue.isEmpty()) {
			ItemStack next = queue.poll();
			current = Challenge.create(next, itemId(next), config);

			if (current != null) {
				speech.prepare(config.language);
			}
		}
	}

	public Challenge current() {
		return current;
	}

	public void skip() {
		if (current != null) {
			current.cancel();
			current = null;
		}
	}

	public void clear() {
		skip();
		queue.clear();
		lastAsked.clear();
	}

	static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
