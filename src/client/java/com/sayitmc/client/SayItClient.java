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
	/** The language the server makes everyone learn; null until its rules arrive. */
	private static String serverLanguage;
	private static int serverSeconds;
	/** The server does not let players turn the game off. */
	private static boolean serverMandatory;

	@Override
	public void onInitializeClient() {
		ClientConfig.load();

		speech = new SpeechService(FabricLoader.getInstance().getGameDir().resolve(SayItMod.MOD_ID).resolve("models"));
		challenges = new ChallengeManager(speech);

		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Rules.TYPE, (payload, context) -> applyRules(payload));
		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Challenge.TYPE, (payload, context) -> challenges.serverChallenge(payload));
		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Result.TYPE,
				(payload, context) -> challenges.serverResult(payload.id(), Outcome.byId(payload.outcome())));

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			serverGame = ClientPlayNetworking.canSend(SayItPayloads.Hello.TYPE);
			serverLanguage = null;
			sendHello();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			serverGame = false;
			serverLanguage = null;
			serverMandatory = false;
			challenges.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> challenges.tick(client.player));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SayItMod.MOD_ID, "challenge"), ChallengeHud::extract);
		ClientCommandRegistrationCallback.EVENT.register(SayItCommands::register);
	}

	private static void applyRules(SayItPayloads.Rules rules) {
		if (!rules.language().equals(serverLanguage)) {
			challenges.clear();
			TargetLanguage.invalidate();
			speech.unload();
		}

		serverLanguage = rules.language();
		serverSeconds = rules.seconds();
		serverMandatory = rules.mandatory();

		// Start downloading the speech model now, so it is ready by the first new item.
		if (ClientConfig.get().enabled || serverMandatory) {
			speech.prepare(serverLanguage);
		}
	}

	/** Tells the server this player has the mod and whether they play. */
	static void sendHello() {
		if (serverGame) {
			ClientPlayNetworking.send(new SayItPayloads.Hello(ClientConfig.get().enabled));
		}
	}

	static boolean isServerGame() {
		return serverGame;
	}

	static String serverLanguage() {
		return serverLanguage;
	}

	static int serverSeconds() {
		return serverSeconds;
	}

	static boolean isMandatory() {
		return serverGame && serverMandatory;
	}

	public static SpeechService speech() {
		return speech;
	}

	public static ChallengeManager challenges() {
		return challenges;
	}
}
