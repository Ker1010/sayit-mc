package com.sayitmc.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.sayitmc.SayItMod;
import com.sayitmc.client.speech.SpeechService;
import com.sayitmc.game.Outcome;
import com.sayitmc.network.SayItPayloads;

/**
 * Shows one challenge card at a time. The server decides which item to ask, in which language and for how long;
 * {@code /sayit test} adds practice cards that are never cleared. Render thread only.
 */
public final class ChallengeManager {
	private final SpeechService speech;
	private Challenge current;
	/** A server challenge that arrived while the previous card was still showing its result. */
	private SayItPayloads.Challenge pendingServerChallenge;

	public ChallengeManager(SpeechService speech) {
		this.speech = speech;
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

	/** Practice card for {@code /sayit test}: same language and time as the server's game, but nothing is at stake. */
	public boolean practice(ItemStack stack, String language, int seconds) {
		if (current != null || pendingServerChallenge != null) {
			return false;
		}

		ItemStack copy = stack.copy();
		copy.setCount(1);
		start(Challenge.PRACTICE, copy, itemId(copy), language, seconds);
		return current != null;
	}

	public void tick(Player player) {
		ClientConfig config = ClientConfig.get();
		long now = System.currentTimeMillis();

		if (current != null) {
			current.tick(speech, config, now);

			if (current.isOver(now)) {
				current = null;
			}
		}

		if (current == null && pendingServerChallenge != null) {
			SayItPayloads.Challenge payload = pendingServerChallenge;
			pendingServerChallenge = null;
			String language = SayItClient.serverLanguage();

			if (language == null) {
				answerUnjudgeable(payload.id());
			} else {
				start(payload.id(), stackFor(player, payload.itemId()), payload.itemId(), language, payload.seconds());
			}
		}
	}

	private void start(int serverId, ItemStack stack, String itemId, String language, int seconds) {
		current = stack == null ? null : Challenge.create(serverId, stack, itemId, language, seconds);

		if (current == null) {
			// No translation for this item in that language (e.g. a modded item), so it cannot be asked.
			SayItMod.LOGGER.info("No {} name for {}, skipping it", language, itemId);
			answerUnjudgeable(serverId);
		} else {
			speech.prepare(language);
		}
	}

	private static void answerUnjudgeable(int serverId) {
		if (serverId != Challenge.PRACTICE) {
			ClientPlayNetworking.send(new SayItPayloads.Answer(serverId, false));
		}
	}

	public void clearHeard() {
		if (current != null) {
			current.clearHeard();
		}
	}

	public Challenge current() {
		return current;
	}

	/** Ends a practice card. Server challenges cannot be skipped (dropping the item resets them). */
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
