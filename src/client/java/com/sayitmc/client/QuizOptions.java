package com.sayitmc.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** Multiple-choice answers for players without a microphone: the right name plus two other item names. */
final class QuizOptions {
	static final int COUNT = 3;

	private static String cachedLanguage;
	private static List<String> allNames = List.of();

	private QuizOptions() {
	}

	/** The right word and two decoys in random order, or null if the language has too few item names. */
	static List<String> build(String word, String language, Random random) {
		List<String> names = names(language);
		Set<String> picked = new LinkedHashSet<>();
		picked.add(word);

		for (int attempt = 0; attempt < 100 && picked.size() < COUNT && !names.isEmpty(); attempt++) {
			picked.add(names.get(random.nextInt(names.size())));
		}

		if (picked.size() < COUNT) {
			return null;
		}

		List<String> options = new ArrayList<>(picked);
		Collections.shuffle(options, random);
		return options;
	}

	/** Collects the item names now (when the rules arrive) rather than in the middle of the first challenge. */
	static void warmUp(String language) {
		names(language);
	}

	/** Up to {@code count} other item names, never {@code word}. */
	static List<String> decoys(String word, String language, int count, Random random) {
		List<String> names = names(language);
		Set<String> picked = new LinkedHashSet<>();

		for (int attempt = 0; attempt < 100 && picked.size() < count && !names.isEmpty(); attempt++) {
			String name = names.get(random.nextInt(names.size()));

			if (!name.equals(word)) {
				picked.add(name);
			}
		}

		return List.copyOf(picked);
	}

	private static List<String> names(String language) {
		if (!language.equals(cachedLanguage)) {
			Set<String> unique = new LinkedHashSet<>();

			for (Item item : BuiltInRegistries.ITEM) {
				String name = TargetLanguage.translate(language, item.getDescriptionId());

				if (name != null && !name.isBlank()) {
					unique.add(name.trim());
				}
			}

			allNames = List.copyOf(unique);
			cachedLanguage = language;
		}

		return allNames;
	}
}
