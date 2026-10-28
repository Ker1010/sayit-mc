package com.sayitmc.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Practice mode only: notices when an item type shows up in the inventory that was not there on the previous tick.
 * (On a server with the mod, the server does this.)
 */
public final class ItemWatcher {
	private final ChallengeManager challenges;
	/** Item ids held on the previous tick; null until the first tick in a world. */
	private Set<String> previous;

	public ItemWatcher(ChallengeManager challenges) {
		this.challenges = challenges;
	}

	public void tick(Player player) {
		if (player == null) {
			previous = null;
			return;
		}

		ClientConfig config = ClientConfig.get();
		boolean firstTick = previous == null;
		Container inventory = player.getInventory();
		Set<String> now = new HashSet<>();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			String id = ChallengeManager.itemId(stack);

			if (now.add(id) && config.enabled && (firstTick ? config.quizItemsAlreadyInInventory : !previous.contains(id))) {
				challenges.offerPractice(stack, false);
			}
		}

		previous = now;
	}

	/** Call when joining a world, so the starting inventory is not treated as newly picked up. */
	public void reset() {
		previous = null;
	}
}
