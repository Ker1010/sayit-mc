package com.sayitmc.game;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.sayitmc.text.Romanizer;

/** Minecraft language codes, as chosen by the server. */
public final class Languages {
	/** Suggestions for {@code /sayitadmin language}; any language code Minecraft has works. */
	public static final List<String> COMMON_CODES = List.of(
			"ar_sa", "bg_bg", "ca_es", "cs_cz", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_gb", "en_us",
			"eo_uy", "es_ar", "es_es", "es_mx", "fa_ir", "fi_fi", "fil_ph", "fr_ca", "fr_fr", "he_il", "hi_in",
			"hu_hu", "id_id", "it_it", "ja_jp", "ka_ge", "kk_kz", "ko_kr", "nl_nl", "no_no", "pl_pl", "pt_br",
			"pt_pt", "ro_ro", "ru_ru", "sk_sk", "sr_sp", "sv_se", "th_th", "tr_tr", "uk_ua", "vi_vn", "zh_cn",
			"zh_hk", "zh_tw");

	private static final Pattern CODE = Pattern.compile("[a-z]{2,4}(_[a-z0-9]{2,4})?");

	private Languages() {
	}

	/**
	 * Whether this looks like a Minecraft language code. Dedicated servers only ship English, so whether the
	 * language really exists is only known on clients; they skip items they have no translation for.
	 */
	public static boolean isValidCode(String code) {
		return CODE.matcher(code).matches();
	}

	/** {@code ja_jp -> JAPANESE}, used in the "SAY IT IN ..." header. */
	public static String displayName(String code) {
		String prefix = Romanizer.languagePrefix(code);
		String name = Locale.forLanguageTag(prefix).getDisplayLanguage(Locale.ENGLISH);
		return (name.isEmpty() || name.equalsIgnoreCase(prefix) ? code : name).toUpperCase(Locale.ROOT);
	}
}
