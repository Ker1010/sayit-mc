package com.sayitmc.client;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;

import com.sayitmc.text.Romanizer;

/**
 * Item names in the language being learned, read straight from Minecraft's own translation files,
 * independent of the language the game UI is set to.
 */
public final class TargetLanguage {
	/** Suggestions for {@code /sayit language}; any language code Minecraft has works. */
	public static final List<String> COMMON_CODES = List.of(
			"ar_sa", "bg_bg", "ca_es", "cs_cz", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_gb", "en_us",
			"eo_uy", "es_ar", "es_es", "es_mx", "fa_ir", "fi_fi", "fil_ph", "fr_ca", "fr_fr", "he_il", "hi_in",
			"hu_hu", "id_id", "it_it", "ja_jp", "ka_ge", "kk_kz", "ko_kr", "nl_nl", "no_no", "pl_pl", "pt_br",
			"pt_pt", "ro_ro", "ru_ru", "sk_sk", "sr_sp", "sv_se", "th_th", "tr_tr", "uk_ua", "vi_vn", "zh_cn",
			"zh_hk", "zh_tw");

	private static String loadedCode;
	private static ClientLanguage language;

	private TargetLanguage() {
	}

	/** The translation of {@code key} in {@code code}, or {@code null} if that language has none. */
	public static String translate(String code, String key) {
		Language target = load(code);
		return target.has(key) ? target.getOrDefault(key) : null;
	}

	/** True if Minecraft ships translations for this language code. */
	public static boolean exists(String code) {
		return load(code).has("item.minecraft.stick");
	}

	/** Drops the cached translations, e.g. after switching language. */
	public static void invalidate() {
		loadedCode = null;
		language = null;
	}

	/** {@code ja_jp -> JAPANESE}, used in the "SAY IT IN ..." header. */
	public static String displayName(String code) {
		String prefix = Romanizer.languagePrefix(code);
		String name = Locale.forLanguageTag(prefix).getDisplayLanguage(Locale.ENGLISH);
		return (name.isEmpty() || name.equalsIgnoreCase(prefix) ? code : name).toUpperCase(Locale.ROOT);
	}

	private static ClientLanguage load(String code) {
		if (!code.equals(loadedCode)) {
			language = ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false);
			loadedCode = code;
		}

		return language;
	}
}
