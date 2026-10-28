package com.sayitmc.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import com.sayitmc.SayItMod;

/** This player's settings, stored in {@code config/sayitmc.json}. */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("sayitmc.json");
	private static ClientConfig instance = new ClientConfig();

	// The language and the time per word are chosen by the server, not here.
	public boolean enabled = true;
	/** How close the spoken word must be to count, from 0 (anything) to 1 (exact). */
	public double matchThreshold = 0.75;
	/** Optional Vosk model download URLs by language ("ja" or "ja_jp"), for languages without a built-in one. */
	public Map<String, String> speechModelUrls = new LinkedHashMap<>();

	public static ClientConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);

				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				SayItMod.LOGGER.error("Could not read {}, using defaults", FILE, e);
			}
		}

		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());

			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			SayItMod.LOGGER.error("Could not save {}", FILE, e);
		}
	}

	private void sanitize() {
		if (speechModelUrls == null) {
			speechModelUrls = new LinkedHashMap<>();
		}

		matchThreshold = Math.clamp(matchThreshold, 0.0, 1.0);
	}
}
