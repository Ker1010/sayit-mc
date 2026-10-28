package com.sayitmc.game;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import com.sayitmc.SayItMod;
import com.sayitmc.network.SayItPayloads;

/** Runs a {@link PlayerGame} for every player whose client has the mod, all in the server's language. */
public final class SayItServer {
	private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("sayitmc-server.json");
	private final Map<UUID, PlayerGame> games = new HashMap<>();
	/** When each online player joined, to find the ones whose client never says hello. */
	private final Map<UUID, Long> joinedAt = new HashMap<>();
	/** Item ids that appeared on the ground next to each player during this tick. */
	private final Map<UUID, Set<String>> droppedThisTick = new HashMap<>();
	private static final int HELLO_TIMEOUT_TICKS = 10 * 20;
	/** A dropped item spawns at the player's eyes, so one appearing this close was dropped by them (or on death). */
	private static final double DROP_DISTANCE_SQR = 3 * 3;

	private ServerConfig config = new ServerConfig();
	private LearnedStore learned;
	private MinecraftServer server;
	private long ticks;

	public void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(this::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
			games.clear();
			server = null;
		});
		// Lets server owners edit config/sayitmc-server.json and apply it with /reload.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((reloaded, resourceManager, success) -> apply(ServerConfig.load(configFile)));
		ServerPlayConnectionEvents.JOIN.register((listener, sender, joined) -> joinedAt.put(uuid(listener.player), ticks));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, disconnected) -> {
			games.remove(uuid(listener.player));
			joinedAt.remove(uuid(listener.player));
		});
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				noticeDrop(item);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> SayItAdminCommand.register(dispatcher, this));

		ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Hello.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			// On a server that requires the mod, nobody can opt out.
			boolean playing = payload.enabled() || config.requireMod;
			games.computeIfAbsent(uuid(player), id -> new PlayerGame()).hello(playing, actions(player));
			sendRules(player);
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
				String language = config.language;
				Verdict verdict = Verdict.byId(payload.verdict());
				boolean held = heldItems(player).contains(game.runningItem());
				game.answer(payload.id(), verdict, held, item -> learned.add(uuid(player), language, item), ticks, config, actions(player));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SayItPayloads.Forget.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PlayerGame game = games.get(uuid(player));

			if (game != null && learned != null) {
				learned.clear(uuid(player), config.language);
				game.reset(actions(player));
			}
		});
	}

	public ServerConfig config() {
		return config;
	}

	/** Switches to new rules; a new language starts every player over (their progress in it is kept per language). */
	public void apply(ServerConfig next) {
		boolean newLanguage = !next.language.equals(config.language);
		config = next;

		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerGame game = games.get(uuid(player));

			if (game != null) {
				if (config.requireMod) {
					game.hello(true, actions(player));
				}

				if (newLanguage) {
					game.reset(actions(player));
				}

				sendRules(player);
			}
		}

		SayItMod.LOGGER.info("Say It! language is {}, {} seconds per word", config.language, config.secondsToAnswer);
	}

	public void save() {
		config.save(configFile);
	}

	private void start(MinecraftServer started) {
		server = started;
		games.clear();
		joinedAt.clear();
		config = ServerConfig.load(configFile);
		learned = new LearnedStore(started.getWorldPath(LevelResource.DATA).resolve("sayitmc").resolve("learned.json"));
		learned.load();
	}

	private void tick(MinecraftServer ticking) {
		ticks++;

		if (learned == null) {
			return;
		}

		List<ServerPlayer> players = ticking.getPlayerList().getPlayers();
		String language = config.language;

		for (ServerPlayer player : players) {
			UUID id = uuid(player);
			PlayerGame game = games.get(id);

			if (game == null) {
				// A client with the mod says hello right after joining; one that stays silent does not have it.
				Long joined = joinedAt.get(id);

				if (config.requireMod && joined != null && ticks - joined > HELLO_TIMEOUT_TICKS) {
					player.connection.disconnect(Component.literal(config.requireModMessage));
				}

				continue;
			}

			if (!game.isPlaying()) {
				continue;
			}

			// Creative players get every item for free, so by default they sit the game out; their inventory
			// on returning to survival becomes the new baseline instead of a flood of challenges.
			if (isCreative(player) && !config.includeCreativePlayers) {
				game.reset(actions(player));
				continue;
			}

			Set<String> dropped = droppedThisTick.getOrDefault(id, Set.of());
			game.tick(ticks, heldItems(player), item -> learned.contains(id, language, item), dropped::contains, config, actions(player));
		}

		droppedThisTick.clear();
	}

	private void noticeDrop(ItemEntity item) {
		String itemId = id(item.getItem());

		if (server == null || itemId == null || games.isEmpty()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (games.containsKey(uuid(player)) && player.distanceToSqr(item) <= DROP_DISTANCE_SQR) {
				droppedThisTick.computeIfAbsent(uuid(player), id -> new HashSet<>()).add(itemId);
			}
		}
	}

	private void sendRules(ServerPlayer player) {
		ServerPlayNetworking.send(player, new SayItPayloads.Rules(config.language, config.secondsToAnswer, config.requireMod,
				(int) Math.round(config.matchThreshold * 100), (int) Math.round(config.minConfidence * 100)));
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

			@Override
			public void clearStashedItem(String itemId) {
				removeAll(player, itemId);
				player.sendSystemMessage(Component.literal("Say It!: time ran out for " + itemId
						+ " while you had it put away, so it was cleared. Only dropping an item on the ground resets its timer."));
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
