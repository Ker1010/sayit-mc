package com.sayitmc.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import com.sayitmc.client.speech.SpeechService;
import com.sayitmc.game.Languages;

/** The "SAY IT IN GERMAN / Weizen / listening..." card in the top-left corner. */
public final class ChallengeHud {
	private static final int MARGIN = 8;
	private static final int ICON_SIZE = 32;
	private static final int TEXT_LEFT = 52;
	private static final int PADDING_RIGHT = 12;
	private static final int LINE_HEIGHT = 9;
	private static final long SLIDE_MILLIS = 180;

	private static final int BACKGROUND = 0xE61E2433;
	private static final int TRACK = 0xFF3A4252;
	private static final int GOLD = 0xFFF5C542;
	private static final int GREEN = 0xFF5BD46C;
	private static final int RED = 0xFFE5534B;
	private static final int MUTED = 0xFF9AA1AD;
	private static final int LIGHT = 0xFFDDE1E7;
	private static final int WHITE = 0xFFFFFFFF;

	private static Challenge shown;
	private static long shownSince;

	private ChallengeHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Challenge challenge = SayItClient.challenges().current();

		if (challenge == null) {
			shown = null;
			return;
		}

		long now = System.currentTimeMillis();

		if (challenge != shown) {
			shown = challenge;
			shownSince = now;
		}

		Font font = Minecraft.getInstance().font;
		Challenge.Phase phase = challenge.phase();
		boolean quiz = phase == Challenge.Phase.QUIZ;
		// After a quiz the answer stays hidden unless it was picked.
		boolean showOptions = challenge.wasQuiz() && phase != Challenge.Phase.CORRECT;
		String language = Languages.displayName(challenge.languageCode,
				Languages.locale(Minecraft.getInstance().getLanguageManager().getSelected()));
		String header = I18n.get(showOptions ? "sayitmc.hud.quiz_header" : "sayitmc.hud.header", language);
		String timer = phase == Challenge.Phase.LISTENING || quiz ? challenge.secondsLeft(now) + "s" : "";
		List<String> choices = showOptions ? quizLines(challenge) : List.of();
		String subtitle = challenge.subtitle == null ? null : "(" + challenge.subtitle + ")";
		int accent = accentColor(phase);

		int maxTextWidth = Math.max(80, graphics.guiWidth() - 2 * MARGIN - TEXT_LEFT - PADDING_RIGHT);
		int wordWidth = width(font, challenge.word);
		float wordScale = Math.clamp((float) maxTextWidth / Math.max(1, wordWidth), 1f, 2f);
		int textWidth = Math.max(130, width(font, header) + width(font, timer) + 16);
		textWidth = Math.max(textWidth, (int) Math.ceil(wordWidth * wordScale));

		if (subtitle != null) {
			textWidth = Math.max(textWidth, width(font, subtitle));
		}

		for (String choice : choices) {
			textWidth = Math.max(textWidth, width(font, choice));
		}

		textWidth = Math.min(textWidth, maxTextWidth);
		String status = phase == Challenge.Phase.LISTENING || phase == Challenge.Phase.CHECKING
				? fitEnd(font, statusLine(challenge), textWidth)
				: fit(font, statusLine(challenge), textWidth);
		subtitle = subtitle == null ? null : fit(font, subtitle, textWidth);

		int width = TEXT_LEFT + textWidth + PADDING_RIGHT;
		int wordTop = 20;
		int subtitleTop = wordTop + Math.round(LINE_HEIGHT * wordScale) + 2;
		int barTop = showOptions
				? wordTop + choices.size() * (LINE_HEIGHT + 3) + 1
				: subtitle == null ? subtitleTop + 1 : subtitleTop + LINE_HEIGHT + 3;
		int statusTop = barTop + 7;
		int height = statusTop + LINE_HEIGHT + 6;

		float slide = Math.min(1f, (now - shownSince) / (float) SLIDE_MILLIS);
		int x = MARGIN - Math.round((width + MARGIN) * (1 - slide) * (1 - slide));
		int y = MARGIN;
		int textX = x + TEXT_LEFT;

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.fill(x, y, x + 3, y + height, accent);

		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 12, y + (height - ICON_SIZE) / 2f);
		graphics.pose().scale(2f);
		graphics.fakeItem(challenge.stack, 0, 0);
		graphics.pose().popMatrix();

		text(graphics, font, header, textX, y + 7, MUTED);

		if (!timer.isEmpty()) {
			text(graphics, font, timer, textX + textWidth - width(font, timer), y + 7, WHITE);
		}

		if (showOptions) {
			for (int i = 0; i < choices.size(); i++) {
				text(graphics, font, fit(font, choices.get(i), textWidth), textX, y + wordTop + i * (LINE_HEIGHT + 3), quiz ? GOLD : MUTED);
			}
		} else {
			int wordColor = phase == Challenge.Phase.CORRECT ? GREEN : phase == Challenge.Phase.WRONG ? RED : GOLD;
			graphics.pose().pushMatrix();
			graphics.pose().translate(textX, y + wordTop);
			graphics.pose().scale(wordScale);
			text(graphics, font, challenge.word, 0, 0, wordColor);
			graphics.pose().popMatrix();
		}

		if (subtitle != null && !showOptions) {
			text(graphics, font, subtitle, textX, y + subtitleTop, LIGHT);
		}

		float progress = progress(challenge, now);
		graphics.fill(textX, y + barTop, textX + textWidth, y + barTop + 3, TRACK);
		graphics.fill(textX, y + barTop, textX + Math.round(textWidth * progress), y + barTop + 3, accent);

		text(graphics, font, status, textX, y + statusTop, statusColor(phase));
	}

	private static String statusLine(Challenge challenge) {
		SpeechService speech = SayItClient.speech();

		return switch (challenge.phase()) {
			case PREPARING -> speech.state() == SpeechService.State.DOWNLOADING
					? I18n.get("sayitmc.hud.downloading", Math.round(speech.downloadProgress() * 100))
					: I18n.get("sayitmc.hud.loading");
			case LISTENING, CHECKING -> challenge.heard().isEmpty()
					? I18n.get("sayitmc.hud.listening", SayItClient.clearKeyName())
					: I18n.get("sayitmc.hud.heard", challenge.heard());
			case QUIZ -> I18n.get("sayitmc.hud.quiz", SayItClient.answerKeyName(0), SayItClient.answerKeyName(1), SayItClient.answerKeyName(2));
			case CORRECT -> I18n.get("sayitmc.hud.correct");
			case WRONG -> I18n.get("sayitmc.hud.wrong");
			case TIMED_OUT -> I18n.get("sayitmc.hud.timeout");
			case CLEARED -> I18n.get("sayitmc.hud.cleared");
			case DROPPED -> I18n.get("sayitmc.hud.dropped");
			case FLASHCARD -> I18n.get("sayitmc.hud.flashcard", challenge.note());
		};
	}

	private static float progress(Challenge challenge, long now) {
		return switch (challenge.phase()) {
			case PREPARING -> SayItClient.speech().state() == SpeechService.State.DOWNLOADING
					? (float) SayItClient.speech().downloadProgress()
					: 0f;
			case LISTENING, QUIZ, FLASHCARD -> challenge.timeLeftFraction(now);
			case CORRECT -> 1f;
			case CHECKING, WRONG, TIMED_OUT, CLEARED, DROPPED -> 0f;
		};
	}

	private static int accentColor(Challenge.Phase phase) {
		return switch (phase) {
			case LISTENING, QUIZ, CHECKING -> GOLD;
			case CORRECT -> GREEN;
			case WRONG, TIMED_OUT, CLEARED -> RED;
			case PREPARING, DROPPED, FLASHCARD -> MUTED;
		};
	}

	private static int statusColor(Challenge.Phase phase) {
		return switch (phase) {
			case CORRECT -> GREEN;
			case WRONG, TIMED_OUT, CLEARED -> RED;
			default -> LIGHT;
		};
	}

	/** "[Z] 小麦 (komugi)" for each quiz option. */
	private static List<String> quizLines(Challenge challenge) {
		List<String> lines = new ArrayList<>();
		List<String> options = challenge.options();

		for (int i = 0; i < options.size(); i++) {
			String sub = challenge.optionSubtitles().get(i);
			lines.add("[" + SayItClient.answerKeyName(i) + "] " + options.get(i) + (sub == null ? "" : " (" + sub + ")"));
		}

		return lines;
	}

	private static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, Component.literal(text), x, y, color);
	}

	private static int width(Font font, String text) {
		return font.width(Component.literal(text));
	}

	/** Like {@link #fit} but keeps the end, so the newest recognized words stay visible. */
	private static String fitEnd(Font font, String text, int maxWidth) {
		if (width(font, text) <= maxWidth) {
			return text;
		}

		int ellipsis = width(font, "...");
		int start = 0;

		while (start < text.length() && width(font, text.substring(start)) + ellipsis > maxWidth) {
			start++;
		}

		return "..." + text.substring(start);
	}

	/** Cuts {@code text} with an ellipsis so it is at most {@code maxWidth} pixels wide. */
	private static String fit(Font font, String text, int maxWidth) {
		if (width(font, text) <= maxWidth) {
			return text;
		}

		int ellipsis = width(font, "...");
		int end = text.length();

		while (end > 0 && width(font, text.substring(0, end)) + ellipsis > maxWidth) {
			end--;
		}

		return text.substring(0, end) + "...";
	}
}
