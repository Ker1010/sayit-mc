package com.sayitmc.text;

import java.util.List;

/**
 * Fuzzy comparison between the expected word and what the speech recognizer heard.
 * Both inputs are {@link Romanizer#matchKey match keys}.
 */
public final class AnswerMatcher {
	private AnswerMatcher() {
	}

	/** Answers this short (in match-key letters) must be heard exactly; one wrong sound is too much of them. */
	static final int EXACT_UP_TO = 3;

	/**
	 * Whether a run of whole heard words matches the expected word, so "uh weizen bitte" counts but a longer word
	 * that merely contains the answer's sounds does not.
	 *
	 * @param heardWords match keys of the heard words, in order
	 */
	public static boolean matches(String expected, List<String> heardWords, double threshold) {
		if (expected.isEmpty()) {
			return false;
		}

		for (int start = 0; start < heardWords.size(); start++) {
			StringBuilder run = new StringBuilder();

			for (int end = start; end < heardWords.size() && run.length() <= expected.length() + 2; end++) {
				run.append(heardWords.get(end));

				if (similar(expected, run.toString(), threshold)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean similar(String expected, String heard, double threshold) {
		if (heard.isEmpty()) {
			return false;
		}

		return expected.length() <= EXACT_UP_TO ? expected.equals(heard) : similarity(expected, heard) >= threshold;
	}

	static double similarity(String a, String b) {
		int longest = Math.max(a.length(), b.length());
		return longest == 0 ? 1.0 : 1.0 - (double) levenshtein(a, b) / longest;
	}

	private static int levenshtein(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];

		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}

		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;

			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}

			int[] swap = previous;
			previous = current;
			current = swap;
		}

		return previous[b.length()];
	}
}
