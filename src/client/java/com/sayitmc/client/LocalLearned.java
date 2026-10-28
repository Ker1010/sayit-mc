package com.sayitmc.client;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

import com.sayitmc.SayItMod;

/**
 * Items said correctly in practice mode, per language, stored in {@code config/sayitmc/learned.json}.
 * On a server with the mod, the server keeps track instead.
 */
public final class LocalLearned {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<TreeMap<String, TreeSet<String>>>() { }.getType();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("sayitmc").resolve("learned.json");
	private static Map<String, TreeSet<String>> learned = new TreeMap<>();

	private LocalLearned() {
	}

	public static boolean contains(String language, String itemId) {
		Set<String> items = learned.get(language);
		return items != null && items.contains(itemId);
	}

	public static void add(String language, String itemId) {
		if (learned.computeIfAbsent(language, l -> new TreeSet<>()).add(itemId)) {
			save();
		}
	}

	public static int count(String language) {
		Set<String> items = learned.get(language);
		return items == null ? 0 : items.size();
	}

	public static void clear(String language) {
		if (learned.remove(language) != null) {
			save();
		}
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, TreeSet<String>> loaded = GSON.fromJson(reader, TYPE);

			if (loaded != null) {
				learned = loaded;
			}
		} catch (IOException | JsonParseException e) {
			SayItMod.LOGGER.error("Could not read {}", FILE, e);
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());

			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(learned, TYPE, writer);
			}
		} catch (IOException e) {
			SayItMod.LOGGER.error("Could not save {}", FILE, e);
		}
	}
}
