package com.sayitmc.client;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
	private static double matchThreshold = 0.85;
	private static double minConfidence = 0.8;
	private static KeyMapping clearKey;
	private static final KeyMapping[] answerKeys = new KeyMapping[QuizOptions.COUNT];

	@Override
	public void onInitializeClient() {
		ClientConfig.load();

		speech = new SpeechService(FabricLoader.getInstance().getGameDir().resolve(SayItMod.MOD_ID).resolve("models"));
		challenges = new ChallengeManager(speech);

		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Rules.TYPE, (payload, context) -> applyRules(payload));
		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Challenge.TYPE, (payload, context) -> challenges.serverChallenge(payload));
		ClientPlayNetworking.registerGlobalReceiver(SayItPayloads.Result.TYPE,
				(payload, context) -> challenges.serverResult(payload.id(), Outcome.byId(payload.outcome()), payload.reveal()));

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
		// Changeable in Options -> Controls -> Key Binds.
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SayItMod.MOD_ID, "main"));
		clearKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.sayitmc.clear_heard", SDLScancode.SDL_SCANCODE_BACKSPACE, category));
		// Not 1-3, which already switch hotbar slots.
		int[] answerDefaults = {SDLScancode.SDL_SCANCODE_Z, SDLScancode.SDL_SCANCODE_X, SDLScancode.SDL_SCANCODE_C};

		for (int i = 0; i < answerKeys.length; i++) {
			answerKeys[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sayitmc.answer_" + (i + 1), answerDefaults[i], category));
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (clearKey.consumeClick()) {
				challenges.clearHeard();
			}

			for (int i = 0; i < answerKeys.length; i++) {
				while (answerKeys[i].consumeClick()) {
					challenges.choose(i);
				}
			}

			challenges.tick(client.player);
		});

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
		matchThreshold = rules.matchPercent() / 100.0;
		minConfidence = rules.confidencePercent() / 100.0;
		QuizOptions.warmUp(serverLanguage);

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

	/** How close a spoken word must be, set by the server. */
	static double matchThreshold() {
		return matchThreshold;
	}

	static double minConfidence() {
		return minConfidence;
	}

	static boolean isMandatory() {
		return serverGame && serverMandatory;
	}

	/** The key currently bound to "clear misheard words", as shown in the controls menu. */
	static String clearKeyName() {
		return clearKey.getTranslatedKeyMessage().getString();
	}

	/** The key currently bound to quiz answer {@code index}. */
	static String answerKeyName(int index) {
		return answerKeys[index].getTranslatedKeyMessage().getString();
	}

	public static SpeechService speech() {
		return speech;
	}

	public static ChallengeManager challenges() {
		return challenges;
	}
}
