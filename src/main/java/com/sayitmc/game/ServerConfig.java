package com.sayitmc.game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.sayitmc.SayItMod;

/** Rules of the game, stored in {@code config/sayitmc-server.json} on the server (or the singleplayer game). */
public final class ServerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int secondsToAnswer = 10;
	/** Take the item away when its timer runs out. Turn off for practice without losing items. */
	public boolean clearItemsOnTimeout = true;
	public boolean includeCreativePlayers = false;
	/** Also quiz what players carry when they join, not only items picked up later. */
	public boolean quizItemsAlreadyInInventory = false;
	/** How long to wait for a player's speech model to download before giving up on a challenge. */
	public int waitForClientSeconds = 60;

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

		config.secondsToAnswer = Math.clamp(config.secondsToAnswer, 3, 120);
		config.waitForClientSeconds = Math.clamp(config.waitForClientSeconds, 5, 600);

		try {
			Files.createDirectories(file.getParent());

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			SayItMod.LOGGER.error("Could not save {}", file, e);
		}

		return config;
	}
}
