package com.sayitmc;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;

/**
 * Item names in the language being learned, read straight from Minecraft's own translation files,
 * independent of the language the game UI is set to.
 */
public final class TargetLanguage {
	private static String loadedCode;
	private static ClientLanguage language;

	private TargetLanguage() {
	}

	/** The translation of {@code key} in {@code code}, or {@code null} if that language has none. */
	public static String translate(String code, String key) {
		if (!code.equals(loadedCode)) {
			language = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false);
			loadedCode = code;
		}

		return language.has(key) ? language.getOrDefault(key) : null;
	}

	/** Drops the cached translations, e.g. after switching language. */
	public static void invalidate() {
		loadedCode = null;
		language = null;
	}

	public static boolean exists(String code) {
		return codes().contains(code);
	}

	public static Collection<String> codes() {
		return Minecraft.getInstance().getLanguageManager().getLanguages().keySet();
	}

	/** {@code ja_jp -> JAPANESE}, used in the "SAY IT IN ..." header. */
	public static String displayName(String code) {
		String prefix = Romanizer.languagePrefix(code);
		String name = Locale.forLanguageTag(prefix).getDisplayLanguage(Locale.ENGLISH);
		return (name.isEmpty() || name.equalsIgnoreCase(prefix) ? code : name).toUpperCase(Locale.ROOT);
	}
}
