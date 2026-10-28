package com.sayitmc.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LanguagesTest {
	@Test
	void acceptsMinecraftLanguageCodes() {
		for (String code : Languages.COMMON_CODES) {
			assertTrue(Languages.isValidCode(code), code);
		}

		assertTrue(Languages.isValidCode("lol_us"));
	}

	@Test
	void rejectsAnythingElse() {
		assertFalse(Languages.isValidCode("Japanese"));
		assertFalse(Languages.isValidCode("ja-JP"));
		assertFalse(Languages.isValidCode(""));
	}

	@Test
	void displayNames() {
		assertEquals("JAPANESE", Languages.displayName("ja_jp"));
		assertEquals("GERMAN", Languages.displayName("de_de"));
	}
}
