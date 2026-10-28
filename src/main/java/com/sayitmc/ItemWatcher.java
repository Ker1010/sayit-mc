package com.sayitmc;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Notices when an item type shows up in the player's inventory that was not there on the previous tick. */
public final class ItemWatcher {
	private final ChallengeManager challenges;
	/** Item ids held on the previous tick; null until the first tick in a world. */
	private Set<String> previous;

	public ItemWatcher(ChallengeManager challenges) {
		this.challenges = challenges;
	}

	public void tick(LocalPlayer player) {
		if (player == null) {
			previous = null;
			return;
		}

		SayItConfig config = SayItConfig.get();
		boolean firstTick = previous == null;
		Inventory inventory = player.getInventory();
		Set<String> now = new HashSet<>();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			String id = ChallengeManager.itemId(stack);

			if (now.add(id) && config.enabled && (firstTick ? config.quizItemsAlreadyInInventory : !previous.contains(id))) {
				challenges.offer(stack, false);
			}
		}

		previous = now;
	}

	/** Call when joining a world, so the starting inventory is not treated as newly picked up. */
	public void reset() {
		previous = null;
	}
}
