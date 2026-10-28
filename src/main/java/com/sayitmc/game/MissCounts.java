package com.sayitmc.game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import com.sayitmc.SayItMod;

/** How many times each player missed each item, per language, so the answer can be shown after a few misses. */
public final class MissCounts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<TreeMap<String, TreeMap<String, TreeMap<String, Integer>>>>() { }.getType();

	private final Path file;
	/** player uuid -> language code -> item id -> misses */
	private Map<String, TreeMap<String, TreeMap<String, Integer>>> misses = new TreeMap<>();

	public MissCounts(Path file) {
		this.file = file;
	}

	/** Counts one more miss and returns the total. */
	public int add(UUID player, String language, String itemId) {
		int total = misses.computeIfAbsent(player.toString(), p -> new TreeMap<>())
				.computeIfAbsent(language, l -> new TreeMap<>())
				.merge(itemId, 1, Integer::sum);
		save();
		return total;
	}

	public void clear(UUID player, String language, String itemId) {
		Map<String, TreeMap<String, Integer>> languages = misses.get(player.toString());
		Map<String, Integer> items = languages == null ? null : languages.get(language);

		if (items != null && items.remove(itemId) != null) {
			save();
		}
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			Map<String, TreeMap<String, TreeMap<String, Integer>>> loaded = GSON.fromJson(reader, TYPE);

			if (loaded != null) {
				misses = loaded;
			}
		} catch (IOException | JsonParseException e) {
			SayItMod.LOGGER.error("Could not read {}", file, e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(misses, TYPE, writer);
			}
		} catch (IOException e) {
			SayItMod.LOGGER.error("Could not save {}", file, e);
		}
	}
}
