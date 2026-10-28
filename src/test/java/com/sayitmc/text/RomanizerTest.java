package com.sayitmc.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RomanizerTest {
	@Test
	void latinWordsHaveNoSubtitle() {
		assertNull(Romanizer.subtitle("Weizen", "de_de"));
		assertNull(Romanizer.subtitle("Épée en diamant", "fr_fr"));
	}

	@Test
	void matchKeyIgnoresCaseAccentsAndSpaces() {
		assertEquals("weizen", Romanizer.matchKey("Weizen", "de_de"));
		assertEquals(Romanizer.matchKey("Épée en diamant", "fr_fr"), Romanizer.matchKey("epee en diamant", "fr_fr"));
		assertEquals("strasse", Romanizer.matchKey("Straße", "de_de"));
	}

	@Test
	void japaneseUsesKanaReadingsNotChinese() {
		assertEquals("komugi", Romanizer.subtitle("小麦", "ja_jp"));
		assertEquals("daiyamondo no ken", Romanizer.subtitle("ダイヤモンドの剣", "ja_jp"));
		assertEquals("ōku no genboku", Romanizer.subtitle("オークの原木", "ja_jp"));
	}

	@Test
	void japaneseKanjiAndKanaSpellingsMatch() {
		assertEquals(Romanizer.matchKey("小麦", "ja_jp"), Romanizer.matchKey("こむぎ", "ja_jp"));
		assertEquals(Romanizer.matchKey("作業台", "ja_jp"), Romanizer.matchKey("さぎょうだい", "ja_jp"));
	}

	@Test
	void otherScripts() {
		assertEquals("xiǎo mài", Romanizer.subtitle("小麦", "zh_cn"));
		assertEquals("xiaomai", Romanizer.matchKey("小麥", "zh_tw"));
		assertEquals("daiamondeu geom", Romanizer.subtitle("다이아몬드 검", "ko_kr"));
		assertEquals("Almaznyy mech", Romanizer.subtitle("Алмазный меч", "ru_ru"));
		assertEquals("Pshenytsya", Romanizer.subtitle("Пшениця", "uk_ua"));
		assertEquals("Sitári", Romanizer.subtitle("Σιτάρι", "el_gr"));
		assertEquals("khaw sali", Romanizer.subtitle("ข้าวสาลี", "th_th"));
	}

	@Test
	void speechPhrasesMatchHowSpeechModelsSpellWords() {
		assertEquals(java.util.List.of("ダイヤモンド の 剣", "ダイヤモンドの剣"), Romanizer.speechPhrases("ダイヤモンドの剣", "ja_jp"));
		assertEquals(java.util.List.of("小 麦", "小麦"), Romanizer.speechPhrases("小麦", "zh_cn"));
		assertEquals(java.util.List.of("épée en diamant"), Romanizer.speechPhrases("Épée en diamant", "fr_fr"));
		assertEquals(java.util.List.of("jack o lantern"), Romanizer.speechPhrases("Jack o'Lantern", "en_us"));
	}
}
