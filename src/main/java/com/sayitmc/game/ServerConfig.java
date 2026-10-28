package com.sayitmc.game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.sayitmc.SayItMod;

/** Rules of the game, stored in {@code config/sayitmc-server.json} on the server (or the singleplayer game). */
public final class ServerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_LANGUAGE = "de_de";

	/** Minecraft language code every player learns, e.g. de_de, ja_jp, zh_cn, ko_kr, fr_fr. Players cannot change it. */
	public String language = DEFAULT_LANGUAGE;
	public int secondsToAnswer = 10;
	/** How close the spoken word must be to count, from 0 (anything) to 1 (exact). Answers of 3 letters or less must be exact. */
	public double matchThreshold = 0.85;
	/** How sure the speech recognizer must be of each word (0-1) when it hears one of the listed words. */
	public double minConfidence = 0.8;
	/** Take the item away when its timer runs out. Turn off for practice without losing items. */
	public boolean clearItemsOnTimeout = true;
	public boolean includeCreativePlayers = false;
	/** Also quiz what players carry when they join, not only items picked up later. */
	public boolean quizItemsAlreadyInInventory = false;
	/**
	 * An unanswered item put away somewhere other than the ground (a chest, a bundle) is cleared when it comes back
	 * into the inventory within this many minutes.
	 */
	public int rememberStashedItemsMinutes = 30;
	/** How long to wait for a player's speech model to download before giving up on a challenge. */
	public int waitForClientSeconds = 60;
	/** Show the word and countdown above the challenged player's head, for everyone to see. */
	public boolean showChallengeAboveHead = true;
	/** Kick players whose client does not have the mod, and do not let anyone turn the game off. */
	public boolean requireMod = true;
	public String requireModMessage = "This server plays Say It! Install the Say It! mod (sayit-mc) and Fabric API for Minecraft 26.3 to join.";

	public static ServerConfig load(Path file) {
		ServerConfig config = new ServerConfig();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);

				if (loaded != null) {
					config = loaded;
				}
			} catch (IOException | JsonParseException e) {
				SayItMod.LOGGER.error("Could not read {}, using defaults", file, e);
			}
		}

		config.sanitize();
		config.save(file);
		return config;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			SayItMod.LOGGER.error("Could not save {}", file, e);
		}
	}

	public ServerConfig copy() {
		return GSON.fromJson(GSON.toJson(this), ServerConfig.class);
	}

	private void sanitize() {
		language = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);

		if (!Languages.isValidCode(language)) {
			SayItMod.LOGGER.warn("\"{}\" is not a Minecraft language code, using {}", language, DEFAULT_LANGUAGE);
			language = DEFAULT_LANGUAGE;
		}

		secondsToAnswer = Math.clamp(secondsToAnswer, 3, 120);
		matchThreshold = Math.clamp(matchThreshold, 0.5, 1.0);
		minConfidence = Math.clamp(minConfidence, 0.0, 1.0);
		waitForClientSeconds = Math.clamp(waitForClientSeconds, 5, 600);
		rememberStashedItemsMinutes = Math.clamp(rememberStashedItemsMinutes, 0, 24 * 60);

		if (requireModMessage == null || requireModMessage.isBlank()) {
			requireModMessage = new ServerConfig().requireModMessage;
		}
	}
}
