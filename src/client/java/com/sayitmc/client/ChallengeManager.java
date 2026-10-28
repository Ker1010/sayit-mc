package com.sayitmc.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.sayitmc.client.speech.SpeechService;
import com.sayitmc.game.Outcome;
import com.sayitmc.network.SayItPayloads;

/**
 * Shows one challenge card at a time. On a server with the mod the server decides which item to ask and when;
 * otherwise (practice mode) new items are queued here. Render thread only.
 */
public final class ChallengeManager {
	private static final int MAX_QUEUED = 8;

	private final SpeechService speech;
	private final Deque<ItemStack> practiceQueue = new ArrayDeque<>();
	/** When each item was last asked in practice mode, so a missed word is not asked again right away. */
	private final Map<String, Long> lastAsked = new HashMap<>();
	private Challenge current;
	/** A server challenge that arrived while the previous card was still showing its result. */
	private SayItPayloads.Challenge pendingServerChallenge;

	public ChallengeManager(SpeechService speech) {
		this.speech = speech;
	}

	/** Practice mode: asks for this item unless it is already learned, queued, or was asked very recently. */
	public void offerPractice(ItemStack stack, boolean force) {
		ClientConfig config = ClientConfig.get();
		String id = itemId(stack);
		Long asked = lastAsked.get(id);
		long now = System.currentTimeMillis();

		if (!force && (LocalLearned.contains(config.language, id)
				|| asked != null && now - asked < config.retryCooldownSeconds * 1000L)) {
			return;
		}

		boolean pending = current != null && current.itemId.equals(id) || practiceQueue.stream().anyMatch(s -> itemId(s).equals(id));

		if (!pending && practiceQueue.size() < MAX_QUEUED) {
			ItemStack copy = stack.copy();
			copy.setCount(1);
			practiceQueue.add(copy);
			lastAsked.put(id, now);
		}
	}

	public void serverChallenge(SayItPayloads.Challenge payload) {
		if (current != null && !current.isServerGame()) {
			current.cancel();
			current = null;
		}

		pendingServerChallenge = payload;
	}

	public void serverResult(int id, Outcome outcome) {
		if (pendingServerChallenge != null && pendingServerChallenge.id() == id) {
			pendingServerChallenge = null;
		} else if (current != null && current.serverId == id) {
			current.serverResult(outcome, System.currentTimeMillis());
		}
	}

	public void tick(Player player) {
		ClientConfig config = ClientConfig.get();
		long now = System.currentTimeMillis();

		if (current != null) {
			current.tick(speech, config, now);

			if (current.isOver(now)) {
				if (current.wasCorrect() && !current.isServerGame()) {
					LocalLearned.add(current.languageCode, current.itemId);
				}

				current = null;
			}
		}

		if (current == null && pendingServerChallenge != null) {
			SayItPayloads.Challenge payload = pendingServerChallenge;
			pendingServerChallenge = null;
			start(payload.id(), stackFor(player, payload.itemId()), payload.itemId(), payload.seconds(), config);
		}

		while (current == null && !practiceQueue.isEmpty()) {
			ItemStack next = practiceQueue.poll();
			start(Challenge.PRACTICE, next, itemId(next), config.secondsToAnswer, config);
		}
	}

	private void start(int serverId, ItemStack stack, String itemId, int seconds, ClientConfig config) {
		if (stack == null) {
			answerUnjudgeable(serverId);
			return;
		}

		current = Challenge.create(serverId, stack, itemId, config.language, seconds);

		if (current == null) {
			// No translation for this item (e.g. a modded item), so it cannot be asked.
			answerUnjudgeable(serverId);
		} else {
			speech.prepare(config.language);
		}
	}

	private static void answerUnjudgeable(int serverId) {
		if (serverId != Challenge.PRACTICE) {
			ClientPlayNetworking.send(new SayItPayloads.Answer(serverId, false));
		}
	}

	public Challenge current() {
		return current;
	}

	/** Ends a practice challenge. Server challenges cannot be skipped (dropping the item resets them). */
	public boolean skipPractice() {
		if (current == null || current.isServerGame()) {
			return false;
		}

		current.cancel();
		current = null;
		return true;
	}

	public void clear() {
		if (current != null) {
			current.cancel();
			current = null;
		}

		pendingServerChallenge = null;
		practiceQueue.clear();
		lastAsked.clear();
	}

	/** The matching stack from the player's inventory (keeps enchantment glint etc.), or a plain one. */
	private static ItemStack stackFor(Player player, String itemId) {
		if (player != null) {
			Container inventory = player.getInventory();

			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);

				if (!stack.isEmpty() && itemId(stack).equals(itemId)) {
					return stack.copy();
				}
			}
		}

		Identifier id = Identifier.tryParse(itemId);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		return item == null ? null : new ItemStack(item);
	}

	static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
