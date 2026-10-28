package com.sayitmc.game;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.sayitmc.text.Romanizer;

/** Links to Google Translate, which shows the word with a button to hear it said. */
final class GoogleTranslate {
	private GoogleTranslate() {
	}

	static URI link(String word, String from, String to) {
		String text = URLEncoder.encode(word, StandardCharsets.UTF_8);
		return URI.create("https://translate.google.com/?sl=" + from + "&tl=" + to + "&text=" + text + "&op=translate");
	}

	/** Minecraft language code to Google's: {@code ja_jp -> ja}, {@code zh_cn -> zh-CN}, {@code zh_tw -> zh-TW}. */
	static String language(String minecraftCode) {
		String code = minecraftCode == null ? "" : minecraftCode.toLowerCase(Locale.ROOT);
		String prefix = Romanizer.languagePrefix(code);

		if (prefix.equals("zh")) {
			return code.equals("zh_tw") || code.equals("zh_hk") ? "zh-TW" : "zh-CN";
		}

		return prefix.isEmpty() ? "en" : prefix;
	}
}
