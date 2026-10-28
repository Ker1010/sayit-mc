package com.sayitmc.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoogleTranslateTest {
	@Test
	void minecraftCodesBecomeGoogleCodes() {
		assertEquals("ja", GoogleTranslate.language("ja_jp"));
		assertEquals("zh-CN", GoogleTranslate.language("zh_cn"));
		assertEquals("zh-TW", GoogleTranslate.language("zh_tw"));
		assertEquals("de", GoogleTranslate.language("de_de"));
	}

	@Test
	void linkHasTheWordEncoded() {
		assertEquals("https://translate.google.com/?sl=ja&tl=zh-CN&text=%E5%B0%8F%E9%BA%A6&op=translate",
				GoogleTranslate.link("小麦", "ja", "zh-CN").toString());
	}
}
