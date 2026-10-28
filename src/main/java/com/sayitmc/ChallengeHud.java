package com.sayitmc;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

import com.sayitmc.speech.SpeechService;

/** The "SAY IT IN GERMAN / Weizen / listening..." card in the top-left corner. */
public final class ChallengeHud {
	private static final int MARGIN = 8;
	private static final int ICON_SIZE = 32;
	private static final int TEXT_LEFT = 52;
	private static final int PADDING_RIGHT = 12;
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
		String header = I18n.get("sayitmc.hud.header", TargetLanguage.displayName(challenge.languageCode));
		String timer = phase == Challenge.Phase.LISTENING ? challenge.secondsLeft(now) + "s" : "";
		String subtitle = challenge.subtitle == null ? null : "(" + challenge.subtitle + ")";
		int accent = accentColor(phase);

		int maxTextWidth = Math.max(80, graphics.guiWidth() - 2 * MARGIN - TEXT_LEFT - PADDING_RIGHT);
		int wordWidth = font.width(challenge.word);
		float wordScale = Math.clamp((float) maxTextWidth / Math.max(1, wordWidth), 1f, 2f);
		int textWidth = Math.max(130, font.width(header) + font.width(timer) + 16);
		textWidth = Math.max(textWidth, (int) Math.ceil(wordWidth * wordScale));

		if (subtitle != null) {
			textWidth = Math.max(textWidth, font.width(subtitle));
		}

		textWidth = Math.min(textWidth, maxTextWidth);
		String status = fit(font, statusLine(challenge), textWidth);
		subtitle = subtitle == null ? null : fit(font, subtitle, textWidth);

		int width = TEXT_LEFT + textWidth + PADDING_RIGHT;
		int wordTop = 20;
		int subtitleTop = wordTop + Math.round(font.lineHeight * wordScale) + 2;
		int barTop = subtitle == null ? subtitleTop + 1 : subtitleTop + font.lineHeight + 3;
		int statusTop = barTop + 7;
		int height = statusTop + font.lineHeight + 6;

		float slide = Math.min(1f, (now - shownSince) / (float) SLIDE_MILLIS);
		int x = MARGIN - Math.round((width + MARGIN) * (1 - slide) * (1 - slide));
		int y = MARGIN;
		int textX = x + TEXT_LEFT;

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.fill(x, y, x + 3, y + height, accent);

		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 12, y + (height - ICON_SIZE) / 2f);
		graphics.pose().scale(2f);
		graphics.item(challenge.stack, 0, 0);
		graphics.pose().popMatrix();

		graphics.text(font, header, textX, y + 7, MUTED);

		if (!timer.isEmpty()) {
			graphics.text(font, timer, textX + textWidth - font.width(timer), y + 7, WHITE);
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(textX, y + wordTop);
		graphics.pose().scale(wordScale);
		graphics.text(font, challenge.word, 0, 0, phase == Challenge.Phase.CORRECT ? GREEN : GOLD);
		graphics.pose().popMatrix();

		if (subtitle != null) {
			graphics.text(font, subtitle, textX, y + subtitleTop, LIGHT);
		}

		float progress = progress(challenge, now);
		graphics.fill(textX, y + barTop, textX + textWidth, y + barTop + 3, TRACK);
		graphics.fill(textX, y + barTop, textX + Math.round(textWidth * progress), y + barTop + 3, accent);

		graphics.text(font, status, textX, y + statusTop, statusColor(phase));
	}

	private static String statusLine(Challenge challenge) {
		SpeechService speech = SayItClient.speech();

		return switch (challenge.phase()) {
			case PREPARING -> speech.state() == SpeechService.State.DOWNLOADING
					? I18n.get("sayitmc.hud.downloading", Math.round(speech.downloadProgress() * 100))
					: I18n.get("sayitmc.hud.loading");
			case LISTENING, CHECKING -> challenge.heard().isEmpty()
					? I18n.get("sayitmc.hud.listening")
					: I18n.get("sayitmc.hud.heard", challenge.heard());
			case CORRECT -> I18n.get("sayitmc.hud.correct");
			case TIMED_OUT -> I18n.get("sayitmc.hud.timeout");
			case FLASHCARD -> I18n.get("sayitmc.hud.flashcard", challenge.note());
		};
	}

	private static float progress(Challenge challenge, long now) {
		return switch (challenge.phase()) {
			case PREPARING -> SayItClient.speech().state() == SpeechService.State.DOWNLOADING
					? (float) SayItClient.speech().downloadProgress()
					: 0f;
			case LISTENING, FLASHCARD -> challenge.timeLeftFraction(now);
			case CORRECT -> 1f;
			case CHECKING, TIMED_OUT -> 0f;
		};
	}

	private static int accentColor(Challenge.Phase phase) {
		return switch (phase) {
			case LISTENING, CHECKING -> GOLD;
			case CORRECT -> GREEN;
			case TIMED_OUT -> RED;
			case PREPARING, FLASHCARD -> MUTED;
		};
	}

	private static int statusColor(Challenge.Phase phase) {
		return switch (phase) {
			case CORRECT -> GREEN;
			case TIMED_OUT -> RED;
			default -> LIGHT;
		};
	}

	/** Cuts {@code text} with an ellipsis so it is at most {@code maxWidth} pixels wide. */
	private static String fit(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		String ellipsis = "...";
		int end = text.length();

		while (end > 0 && font.width(text.substring(0, end)) + font.width(ellipsis) > maxWidth) {
			end--;
		}

		return text.substring(0, end) + ellipsis;
	}
}
