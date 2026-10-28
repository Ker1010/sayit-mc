package com.sayitmc.text;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnswerMatcherTest {
	private static final double THRESHOLD = 0.85;

	@Test
	void exactAndSurroundedAnswersMatch() {
		assertTrue(AnswerMatcher.matches("weizen", List.of("weizen"), THRESHOLD));
		assertTrue(AnswerMatcher.matches("weizen", List.of("ah", "weizen", "bitte"), THRESHOLD));
		assertTrue(AnswerMatcher.matches("daiyamondonoken", List.of("daiyamondo", "no", "ken"), THRESHOLD));
	}

	@Test
	void oneSmallRecognitionErrorIsForgiven() {
		assertTrue(AnswerMatcher.matches("daiyamondonoken", List.of("daiyamondo", "no", "kin"), THRESHOLD));
	}

	@Test
	void wordsThatOnlyContainTheSoundsDoNotMatch() {
		assertFalse(AnswerMatcher.matches("ken", List.of("kenkyu"), THRESHOLD));
		assertFalse(AnswerMatcher.matches("pan", List.of("japan"), THRESHOLD));
	}

	@Test
	void shortWordsMustBeExact() {
		assertTrue(AnswerMatcher.matches("pan", List.of("pan"), THRESHOLD));
		assertFalse(AnswerMatcher.matches("pan", List.of("ban"), THRESHOLD));
	}

	@Test
	void wrongOrMissingAnswersFail() {
		assertFalse(AnswerMatcher.matches("weizen", List.of("brot"), THRESHOLD));
		assertFalse(AnswerMatcher.matches("weizen", List.of(), THRESHOLD));
		assertFalse(AnswerMatcher.matches("komugi", List.of("kome"), THRESHOLD));
		assertFalse(AnswerMatcher.matches("weizen", List.of("weitzer"), THRESHOLD));
	}
}
