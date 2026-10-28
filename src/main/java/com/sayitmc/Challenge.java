package com.sayitmc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

import com.sayitmc.speech.ListenSession;
import com.sayitmc.speech.SpeechService;

/** One "say this item's name" prompt, from waiting for the speech model to showing the result. */
public final class Challenge {
	public enum Phase {
		/** Speech model still downloading or loading; the clock has not started. */
		PREPARING,
		LISTENING,
		/** Time is up, waiting for the recognizer to finish the last words. */
		CHECKING,
		CORRECT,
		TIMED_OUT,
		/** Speech is unavailable, so the word is only shown. */
		FLASHCARD
	}

	private static final long RESULT_MILLIS = 2500;
	private static final long CHECK_GRACE_MILLIS = 1500;

	public final ItemStack stack;
	public final String itemId;
	public final String languageCode;
	public final String word;
	/** English-style pronunciation, or null when the word is already in Latin letters. */
	public final String subtitle;
	private final String expectedKey;
	private final long answerMillis;

	private Phase phase = Phase.PREPARING;
	private long phaseStart = System.currentTimeMillis();
	private ListenSession session;
	private String heard = "";
	private String note = "";

	private Challenge(ItemStack stack, String itemId, String languageCode, String word, long answerMillis) {
		this.stack = stack;
		this.itemId = itemId;
		this.languageCode = languageCode;
		this.word = word;
		this.subtitle = Romanizer.subtitle(word, languageCode);
		this.expectedKey = Romanizer.matchKey(word, languageCode);
		this.answerMillis = answerMillis;
	}

	/** Null when the target language has no name for this item. */
	static Challenge create(ItemStack stack, String itemId, SayItConfig config) {
		String word = TargetLanguage.translate(config.language, stack.getItem().getDescriptionId());

		if (word == null || word.isBlank()) {
			return null;
		}

		return new Challenge(stack, itemId, config.language, word.trim(), config.secondsToAnswer * 1000L);
	}

	void tick(SpeechService speech, SayItConfig config, long now) {
		switch (phase) {
			case PREPARING -> {
				if (expectedKey.isEmpty()) {
					showFlashcard("nothing to compare", now);
				} else if (speech.canListen()) {
					startListening(speech, now);
				} else if (speech.state() == SpeechService.State.UNAVAILABLE) {
					showFlashcard(speech.problem(), now);
				}
			}
			case LISTENING -> {
				checkAnswer(config, now);

				if (phase == Phase.LISTENING && now - phaseStart >= answerMillis) {
					session.close();
					enter(Phase.CHECKING, now);
				}
			}
			case CHECKING -> {
				checkAnswer(config, now);

				if (phase == Phase.CHECKING && (session.isFinished() || now - phaseStart >= CHECK_GRACE_MILLIS)) {
					enter(Phase.TIMED_OUT, now);
					playSound("entity.villager.no", 1.0f);
				}
			}
			default -> {
			}
		}
	}

	boolean isOver(long now) {
		return switch (phase) {
			case CORRECT, TIMED_OUT -> now - phaseStart >= RESULT_MILLIS;
			case FLASHCARD -> now - phaseStart >= answerMillis;
			default -> false;
		};
	}

	/** True when the item should count as learned. */
	boolean wasCorrect() {
		return phase == Phase.CORRECT;
	}

	void cancel() {
		if (session != null) {
			session.close();
		}
	}

	public Phase phase() {
		return phase;
	}

	public String heard() {
		return heard;
	}

	/** Why the challenge fell back to a flashcard. */
	public String note() {
		return note;
	}

	/** 1 -> 0 while the clock runs. */
	public float timeLeftFraction(long now) {
		long total = phase == Phase.FLASHCARD || phase == Phase.LISTENING ? answerMillis : 0;
		return total == 0 ? 0 : Math.clamp(1f - (float) (now - phaseStart) / total, 0f, 1f);
	}

	public int secondsLeft(long now) {
		return phase == Phase.LISTENING ? (int) Math.ceil(Math.max(0, answerMillis - (now - phaseStart)) / 1000.0) : 0;
	}

	private void startListening(SpeechService speech, long now) {
		try {
			session = speech.listen();
			enter(Phase.LISTENING, now);
		} catch (Exception e) {
			SayItClient.LOGGER.warn("Could not open the microphone", e);
			showFlashcard(e.getMessage() == null ? "no microphone" : e.getMessage(), now);
		}
	}

	private void checkAnswer(SayItConfig config, long now) {
		String latest = session.heard();

		if (latest.equals(heard)) {
			return;
		}

		heard = latest;

		if (AnswerMatcher.matches(expectedKey, Romanizer.matchKey(latest, languageCode), config.matchThreshold)) {
			session.close();
			enter(Phase.CORRECT, now);
			playSound("entity.player.levelup", 1.4f);
		}
	}

	private void showFlashcard(String reason, long now) {
		note = reason;
		enter(Phase.FLASHCARD, now);
	}

	private void enter(Phase next, long now) {
		phase = next;
		phaseStart = now;
	}

	private static void playSound(String id, float pitch) {
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));

		if (sound != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.7f));
		}
	}
}
