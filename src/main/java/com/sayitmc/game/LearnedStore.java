package com.sayitmc.game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import com.sayitmc.SayItMod;

/** Which items each player has said correctly, per language. Saved with the world. */
public final class LearnedStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<TreeMap<String, TreeMap<String, TreeSet<String>>>>() { }.getType();

	private final Path file;
	/** player uuid -> language code -> item ids */
	private Map<String, TreeMap<String, TreeSet<String>>> learned = new TreeMap<>();

	public LearnedStore(Path file) {
		this.file = file;
	}

	public boolean contains(UUID player, String language, String itemId) {
		Map<String, TreeSet<String>> languages = learned.get(player.toString());
		Set<String> items = languages == null ? null : languages.get(language);
		return items != null && items.contains(itemId);
	}

	public void add(UUID player, String language, String itemId) {
		if (learned.computeIfAbsent(player.toString(), p -> new TreeMap<>()).computeIfAbsent(language, l -> new TreeSet<>()).add(itemId)) {
			save();
		}
	}

	public void clear(UUID player, String language) {
		Map<String, TreeSet<String>> languages = learned.get(player.toString());

		if (languages != null && languages.remove(language) != null) {
			save();
		}
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			Map<String, TreeMap<String, TreeSet<String>>> loaded = GSON.fromJson(reader, TYPE);

			if (loaded != null) {
				learned = loaded;
			}
		} catch (IOException | JsonParseException e) {
			SayItMod.LOGGER.error("Could not read {}", file, e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(learned, TYPE, writer);
			}
		} catch (IOException e) {
			SayItMod.LOGGER.error("Could not save {}", file, e);
		}
	}
}
