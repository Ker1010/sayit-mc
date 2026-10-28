package com.sayitmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnswerMatcherTest {
	private static final double THRESHOLD = 0.75;

	@Test
	void exactAndSurroundedAnswersMatch() {
		assertTrue(AnswerMatcher.matches("weizen", "weizen", THRESHOLD));
		assertTrue(AnswerMatcher.matches("weizen", "ahweizenbitte", THRESHOLD));
	}

	@Test
	void smallRecognitionErrorsAreForgiven() {
		assertTrue(AnswerMatcher.matches("daiyamondonoken", "daiyamondonokin", THRESHOLD));
		assertTrue(AnswerMatcher.matches("weizen", "ummweitzen", THRESHOLD));
	}

	@Test
	void wrongOrMissingAnswersFail() {
		assertFalse(AnswerMatcher.matches("weizen", "brot", THRESHOLD));
		assertFalse(AnswerMatcher.matches("weizen", "", THRESHOLD));
		assertFalse(AnswerMatcher.matches("komugi", "kome", THRESHOLD));
	}
}
