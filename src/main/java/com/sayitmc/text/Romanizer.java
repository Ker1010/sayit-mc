package com.sayitmc.text;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.ibm.icu.text.Transliterator;

/**
 * Turns words written in non-Latin scripts into something an English reader can pronounce,
 * e.g. {@code ダイヤモンドの剣 -> daiyamondo no ken}, {@code 小麦 -> xiǎo mài}, {@code Пшениця -> Pshenytsya}.
 */
public final class Romanizer {
	/** Transliterators that read better for English speakers than ICU's generic Any-Latin. */
	private static final Map<String, String> TRANSFORMS = Map.ofEntries(
			Map.entry("zh", "Han-Latin"),
			Map.entry("ko", "Hangul-Latin"),
			Map.entry("ru", "Russian-Latin/BGN"),
			Map.entry("uk", "Ukrainian-Latin/BGN"),
			Map.entry("be", "Belarusian-Latin/BGN"),
			Map.entry("bg", "Bulgarian-Latin/BGN"),
			Map.entry("mk", "Macedonian-Latin/BGN"),
			Map.entry("sr", "Serbian-Latin/BGN"),
			Map.entry("kk", "Kazakh-Latin/BGN"),
			Map.entry("mn", "Mongolian-Latin/BGN"),
			Map.entry("el", "Greek-Latin/UNGEGN"),
			Map.entry("ka", "Georgian-Latin/BGN"),
			Map.entry("hy", "Armenian-Latin/BGN"),
			Map.entry("ar", "Arabic-Latin/BGN"),
			Map.entry("fa", "Persian-Latin/BGN"),
			Map.entry("he", "Hebrew-Latin/BGN"));

	/** Languages whose accents carry pronunciation (tones, long vowels, stress), so the subtitle keeps them. */
	private static final Set<String> KEEP_ACCENTS = Set.of("zh", "ja", "el");

	/** Doubled vowels, "ei" and "ou": the ways a long vowel is spelled out in kana. */
	private static final Pattern JAPANESE_LONG_VOWELS = Pattern.compile("([aiueo])\\1+|(?<=e)i|(?<=o)u");

	private static final Map<String, Transliterator> TRANSLITERATORS = new ConcurrentHashMap<>();
	private static volatile Tokenizer japaneseTokenizer;

	private Romanizer() {
	}

	/**
	 * The pronunciation subtitle shown under the word, or {@code null} when the word is already written in Latin letters.
	 */
	public static String subtitle(String text, String languageCode) {
		if (isLatin(text)) {
			return null;
		}

		String lang = languagePrefix(languageCode);
		String latin = toLatin(text, lang);
		if (!KEEP_ACCENTS.contains(lang)) {
			latin = transliterator("Latin-ASCII").transliterate(latin);
		}

		latin = latin.replaceAll("\\s+", " ").trim();
		return latin.isEmpty() || latin.equals(text) ? null : latin;
	}

	/**
	 * Lowercase ASCII letters and digits only, used to compare what the player said with the expected word.
	 * Both sides go through the same conversion, so kanji vs. kana or simplified vs. traditional spellings still match.
	 */
	public static String matchKey(String text, String languageCode) {
		String lang = languagePrefix(languageCode);
		String latin = isLatin(text) ? text : toLatin(text, lang);
		String ascii = transliterator("Latin-ASCII").transliterate(latin).toLowerCase(Locale.ROOT);
		StringBuilder key = new StringBuilder(ascii.length());

		for (int i = 0; i < ascii.length(); i++) {
			char c = ascii.charAt(i);

			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				key.append(c);
			}
		}

		// Kana spellings write long vowels out (さぎょう = sagyou) where readings mark them (sagyō -> sagyo).
		return lang.equals("ja") ? JAPANESE_LONG_VOWELS.matcher(key).replaceAll("$1") : key.toString();
	}

	/**
	 * The ways a speech model's vocabulary may spell this phrase, for limiting recognition to a word list:
	 * words separated by spaces (Japanese split into words, Chinese also per character), lowercase, no punctuation.
	 */
	public static List<String> speechPhrases(String text, String languageCode) {
		String lang = languagePrefix(languageCode);
		String plain = text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}・、。「」（）()]+", " ").replaceAll("\\s+", " ").trim();
		Set<String> phrases = new LinkedHashSet<>();

		if (lang.equals("ja")) {
			StringBuilder words = new StringBuilder();

			for (Token token : japaneseTokenizer().tokenize(plain)) {
				if (!token.getSurface().isBlank()) {
					words.append(words.isEmpty() ? "" : " ").append(token.getSurface().trim());
				}
			}

			phrases.add(words.toString());
		} else if (lang.equals("zh")) {
			phrases.add(String.join(" ", plain.replace(" ", "").split("")));
		}

		phrases.add(plain);
		phrases.removeIf(String::isBlank);
		return List.copyOf(phrases);
	}

	/**
	 * How a Japanese word with kanji is read, in kana ({@code 小麦 -> こむぎ}, {@code ダイヤモンドの剣 -> ダイヤモンドのけん}),
	 * or {@code null} for other languages and words without kanji.
	 */
	public static String kanaReading(String text, String languageCode) {
		if (!languagePrefix(languageCode).equals("ja") || text.codePoints().noneMatch(Romanizer::isKanji)) {
			return null;
		}

		Transliterator toHiragana = transliterator("Katakana-Hiragana");
		StringBuilder kana = new StringBuilder();

		for (Token token : japaneseTokenizer().tokenize(text)) {
			String reading = token.getReading();
			boolean hasKanji = token.getSurface().codePoints().anyMatch(Romanizer::isKanji);
			kana.append(hasKanji && reading != null && !reading.equals("*") ? toHiragana.transliterate(reading) : token.getSurface());
		}

		return kana.toString();
	}

	private static boolean isKanji(int codePoint) {
		return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
	}

	/** {@code ja_jp -> ja}, {@code zh_tw -> zh}. */
	public static String languagePrefix(String languageCode) {
		int underscore = languageCode.indexOf('_');
		return (underscore < 0 ? languageCode : languageCode.substring(0, underscore)).toLowerCase(Locale.ROOT);
	}

	static boolean isLatin(String text) {
		return text.codePoints()
				.filter(Character::isLetter)
				.allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN);
	}

	private static String toLatin(String text, String lang) {
		if (lang.equals("ja")) {
			return japaneseToLatin(text);
		}

		return transliterator(TRANSFORMS.getOrDefault(lang, "Any-Latin")).transliterate(text);
	}

	/**
	 * ICU would read kanji as Mandarin, so Japanese goes through Kuromoji first to get the kana pronunciation
	 * of each word, which is then romanized (Hepburn, long vowels as macrons).
	 */
	private static String japaneseToLatin(String text) {
		Transliterator kanaToLatin = transliterator("Hiragana-Katakana; Katakana-Latin");
		Transliterator fallback = transliterator("Any-Latin");
		StringBuilder out = new StringBuilder();

		for (Token token : japaneseTokenizer().tokenize(text.replace('・', ' '))) {
			String kana = token.getPronunciation();

			if (kana == null || kana.equals("*")) {
				kana = token.getReading();
			}

			if (kana == null || kana.equals("*")) {
				kana = token.getSurface();
			}

			String latin = fallback.transliterate(kanaToLatin.transliterate(kana)).trim();

			if (!latin.isEmpty()) {
				if (!out.isEmpty()) {
					out.append(' ');
				}

				out.append(latin);
			}
		}

		return out.toString();
	}

	private static Tokenizer japaneseTokenizer() {
		Tokenizer tokenizer = japaneseTokenizer;

		if (tokenizer == null) {
			synchronized (Romanizer.class) {
				tokenizer = japaneseTokenizer;

				if (tokenizer == null) {
					japaneseTokenizer = tokenizer = new Tokenizer();
				}
			}
		}

		return tokenizer;
	}

	private static Transliterator transliterator(String id) {
		return TRANSLITERATORS.computeIfAbsent(id, Transliterator::getInstance);
	}
}
