package com.sayitmc.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;

/**
 * Item names in the server's language, read straight from Minecraft's own translation files,
 * independent of the language the game UI is set to.
 */
public final class TargetLanguage {
	private static String loadedCode;
	private static ClientLanguage language;

	private TargetLanguage() {
	}

	/** The translation of {@code key} in {@code code}, or {@code null} if that language has none. */
	public static String translate(String code, String key) {
		Language target = load(code);
		return target.has(key) ? target.getOrDefault(key) : null;
	}

	/** Drops the cached translations, e.g. after switching language. */
	public static void invalidate() {
		loadedCode = null;
		language = null;
	}

	private static ClientLanguage load(String code) {
		if (!code.equals(loadedCode)) {
			language = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false);
			loadedCode = code;
		}

		return language;
	}
}
