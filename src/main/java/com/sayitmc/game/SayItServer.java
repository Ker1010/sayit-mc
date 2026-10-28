package com.sayitmc.game;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import com.sayitmc.network.SayItPayloads;

/** Runs a {@link PlayerGame} for every player whose client has the mod. */
public final class SayItServer {
	private final Map<UUID, PlayerGame> games = new HashMap<>();
	private ServerConfig config = new ServerConfig();
	private LearnedStore learned;
	private long ticks;

	public void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(this::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> games.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> games.remove(uuid(listener.player)));
		ServerTickEvents.END_SERVER_TICK.register(this::tick);

		ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Hello.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			games.computeIfAbsent(uuid(player), id -> new PlayerGame()).hello(payload.language(), payload.enabled(), actions(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Listening.TYPE, (payload, context) -> {
			PlayerGame game = games.get(uuid(context.player()));

			if (game != null) {
				game.listening(payload.id(), ticks);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Answer.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PlayerGame game = games.get(uuid(player));

			if (game != null && learned != null) {
				game.answer(payload.id(), payload.correct(), item -> learned.add(uuid(player), game.language(), item), actions(player));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Forget.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PlayerGame game = games.get(uuid(player));

			if (game != null && game.language() != null && learned != null) {
				learned.clear(uuid(player), game.language());
				game.reset(actions(player));
			}
		});
	}

	private void start(MinecraftServer server) {
		games.clear();
		config = ServerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("sayitmc-server.json"));
		Path file = server.getWorldPath(LevelResource.DATA).resolve("sayitmc").resolve("learned.json");
		learned = new LearnedStore(file);
		learned.load();
	}

	private void tick(MinecraftServer server) {
		ticks++;

		if (learned == null || games.isEmpty()) {
			return;
		}

		List<ServerPlayer> players = server.getPlayerList().getPlayers();

		for (ServerPlayer player : players) {
			UUID id = uuid(player);
			PlayerGame game = games.get(id);

			if (game == null || !game.isPlaying()) {
				continue;
			}

			// Creative players get every item for free, so by default they sit the game out; their inventory
			// on returning to survival becomes the new baseline instead of a flood of challenges.
			if (isCreative(player) && !config.includeCreativePlayers) {
				game.reset(actions(player));
				continue;
			}

			String language = game.language();
			game.tick(ticks, heldItems(player), item -> learned.contains(id, language, item), config, actions(player));
		}
	}

	private PlayerGame.Actions actions(ServerPlayer player) {
		return new PlayerGame.Actions() {
			@Override
			public void startChallenge(int id, String itemId, int seconds) {
				ServerPlayNetworking.send(player, new SayItPayloads.Challenge(id, itemId, seconds));
			}

			@Override
			public void endChallenge(int id, Outcome outcome) {
				ServerPlayNetworking.send(player, new SayItPayloads.Result(id, outcome.ordinal()));
			}

			@Override
			public void clearItem(String itemId) {
				removeAll(player, itemId);
			}
		};
	}

	/** Item ids in the inventory, plus the stack held on the cursor while a screen is open. */
	private static Set<String> heldItems(Player player) {
		Set<String> held = new HashSet<>();
		Container inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			addId(held, inventory.getItem(slot));
		}

		addId(held, carried(player).getCarried());
		return held;
	}

	private static void removeAll(Player player, String itemId) {
		Container inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (itemId.equals(id(inventory.getItem(slot)))) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		inventory.setChanged();
		AbstractContainerMenu menu = carried(player);

		if (itemId.equals(id(menu.getCarried()))) {
			menu.setCarried(ItemStack.EMPTY);
		}
	}

	private static AbstractContainerMenu carried(Player player) {
		return player.containerMenu;
	}

	private static void addId(Set<String> ids, ItemStack stack) {
		String id = id(stack);

		if (id != null) {
			ids.add(id);
		}
	}

	private static String id(ItemStack stack) {
		return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static boolean isCreative(Player player) {
		return player.getAbilities().instabuild;
	}

	private static UUID uuid(Entity entity) {
		return entity.getUUID();
	}
}
