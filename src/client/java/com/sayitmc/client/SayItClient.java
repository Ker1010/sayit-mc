package com.sayitmc.client;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;

import com.sayitmc.SayItMod;
import com.sayitmc.client.speech.SpeechService;
import com.sayitmc.game.Outcome;
import com.sayitmc.network.SayItPayloads;

public final class SayItClient implements ClientModInitializer {
	private static SpeechService speech;
	private static ChallengeManager challenges;
	/** True while connected to a server (or singleplayer world) that runs the game. */
	private static boolean serverGame;

	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		LocalLearned.load();

		speech = new SpeechService(FabricLoader.getInstance().getGameDir().resolve(SayItMod.MOD_ID).resolve("models"));
		challenges = new ChallengeManager(speech);
		ItemWatcher watcher = new ItemWatcher(challenges);

		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Challenge.TYPE, (payload, context) -> challenges.serverChallenge(payload));
		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Result.TYPE,
				(payload, context) -> challenges.serverResult(payload.id(), Outcome.byId(payload.outcome())));

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			watcher.reset();
			serverGame = ClientPlayNetworking.canSend(SayItPayloads.Hello.TYPE);
			sendHello();

			if (ClientConfig.get().enabled) {
				speech.prepare(ClientConfig.get().language);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			serverGame = false;
			challenges.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!serverGame) {
				watcher.tick(client.player);
			}

			challenges.tick(client.player);
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SayItMod.MOD_ID, "challenge"), ChallengeHud::extract);
		ClientCommandRegistrationCallback.EVENT.register(SayItCommands::register);
	}

	/** Tells the server which language this player is learning and whether they play. */
	static void sendHello() {
		if (serverGame) {
			ClientPlayNetworking.send(new SayItPayloads.Hello(ClientConfig.get().language, ClientConfig.get().enabled));
		}
	}

	static boolean isServerGame() {
		return serverGame;
	}

	public static SpeechService speech() {
		return speech;
	}

	public static ChallengeManager challenges() {
		return challenges;
	}
}
