package com.sayitmc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;

import com.sayitmc.speech.SpeechService;

public final class SayItClient implements ClientModInitializer {
	public static final String MOD_ID = "sayitmc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SpeechService speech;
	private static ChallengeManager challenges;

	@Override
	public void onInitializeClient() {
		SayItConfig.load();
		LearnedItems.load();

		speech = new SpeechService(FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("models"));
		challenges = new ChallengeManager(speech);
		ItemWatcher watcher = new ItemWatcher(challenges);

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			watcher.reset();

			if (SayItConfig.get().enabled) {
				speech.prepare(SayItConfig.get().language);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> challenges.clear());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			watcher.tick(client.player);
			challenges.tick();
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "challenge"), ChallengeHud::extract);
		ClientCommandRegistrationCallback.EVENT.register(SayItCommands::register);
	}

	public static SpeechService speech() {
		return speech;
	}

	public static ChallengeManager challenges() {
		return challenges;
	}
}
