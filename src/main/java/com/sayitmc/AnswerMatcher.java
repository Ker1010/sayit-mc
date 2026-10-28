package com.sayitmc;

/**
 * Fuzzy comparison between the expected word and what the speech recognizer heard.
 * Both inputs are {@link Romanizer#matchKey match keys}.
 */
public final class AnswerMatcher {
	private AnswerMatcher() {
	}

	public static boolean matches(String expected, String heard, double threshold) {
		return !expected.isEmpty() && !heard.isEmpty() && bestSimilarity(expected, heard) >= threshold;
	}

	/**
	 * Similarity in [0, 1] between {@code expected} and the closest part of {@code heard},
	 * so filler words around the answer ("uh weizen") do not count against the player.
	 */
	public static double bestSimilarity(String expected, String heard) {
		if (heard.contains(expected)) {
			return 1.0;
		}

		if (heard.length() <= expected.length() + 1) {
			return similarity(expected, heard);
		}

		double best = 0;

		for (int length = Math.max(1, expected.length() - 1); length <= expected.length() + 1; length++) {
			for (int start = 0; start + length <= heard.length(); start++) {
				best = Math.max(best, similarity(expected, heard.substring(start, start + length)));
			}
		}

		return best;
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
