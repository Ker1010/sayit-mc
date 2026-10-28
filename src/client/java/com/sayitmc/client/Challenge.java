package com.sayitmc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.sayitmc.SayItMod;
import com.sayitmc.client.speech.ListenSession;
import com.sayitmc.client.speech.SpeechService;
import com.sayitmc.game.Outcome;
import com.sayitmc.game.Verdict;
import com.sayitmc.network.SayItPayloads;
import com.sayitmc.text.AnswerMatcher;
import com.sayitmc.text.Romanizer;

/**
 * One "say this item's name" card, from waiting for the speech model to showing the result.
 * Server challenges report to the server, which owns the timer and clears the item;
 * practice cards ({@code /sayit test}) only run locally.
 */
public final class Challenge {
	public enum Phase {
		/** Speech model still downloading or loading; the clock has not started. */
		PREPARING,
		LISTENING,
		/** No speech: pick the right word out of three with the answer keys. */
		QUIZ,
		/** Time is up, waiting for the last words (and, in the server game, the server's verdict). */
		CHECKING,
		CORRECT,
		/** Picked a wrong quiz answer. */
		WRONG,
		/** Time ran out on a practice card, or on a server that keeps items. */
		TIMED_OUT,
		/** Server game: time ran out and the item was taken away. */
		CLEARED,
		/** Server game: the item left the inventory, so its timer was reset. */
		DROPPED,
		/** Speech is unavailable, so the word is only shown. */
		FLASHCARD
	}

	public static final int PRACTICE = -1;

	private static final long RESULT_MILLIS = 2500;
	private static final long DROPPED_MILLIS = 1500;
	private static final long CHECK_GRACE_MILLIS = 1500;
	/** In the server game, give up waiting for the server's verdict after this long. */
	private static final long SERVER_VERDICT_MILLIS = 5000;

	/** Server challenge id, or {@link #PRACTICE}. */
	public final int serverId;
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
	private boolean over;
	private ListenSession session;
	/** The microphone being opened in the background. */
	private CompletableFuture<ListenSession> opening;
	private String heard = "";
	private int checkedGuesses;
	private String note = "";
	private List<String> options = List.of();
	private List<String> optionSubtitles = List.of();

	private Challenge(int serverId, ItemStack stack, String itemId, String languageCode, String word, long answerMillis) {
		this.serverId = serverId;
		this.stack = stack;
		this.itemId = itemId;
		this.languageCode = languageCode;
		this.word = word;
		this.subtitle = Romanizer.subtitle(word, languageCode);
		this.expectedKey = Romanizer.matchKey(word, languageCode);
		this.answerMillis = answerMillis;
	}

	/** Null when the target language has no name for this item. */
	static Challenge create(int serverId, ItemStack stack, String itemId, String languageCode, int seconds) {
		String word = TargetLanguage.translate(languageCode, stack.getItem().getDescriptionId());

		if (word == null || word.isBlank()) {
			return null;
		}

		return new Challenge(serverId, stack, itemId, languageCode, word.trim(), seconds * 1000L);
	}

	public boolean isServerGame() {
		return serverId != PRACTICE;
	}

	void tick(SpeechService speech, ClientConfig config, long now) {
		switch (phase) {
			case PREPARING -> {
				if (expectedKey.isEmpty()) {
					startQuiz("nothing to compare", now);
				} else if (opening != null) {
					if (opening.isDone()) {
						startListening(now);
					}
				} else if (speech.canListen()) {
					opening = speech.listen(wordList(), SayItClient.minConfidence());
				} else if (speech.state() == SpeechService.State.UNAVAILABLE) {
					startQuiz(speech.problem(), now);
				}
			}
			case LISTENING -> {
				checkAnswer(config, now);

				if (phase == Phase.LISTENING && now - phaseStart >= answerMillis) {
					session.close();
					enter(Phase.CHECKING, now);
				}
			}
			case QUIZ -> {
				if (now - phaseStart >= answerMillis) {
					enter(Phase.CHECKING, now);
				}
			}
			case CHECKING -> {
				if (session != null) {
					checkAnswer(config, now);
				}

				long waited = now - phaseStart;
				boolean done = isServerGame()
						? waited >= SERVER_VERDICT_MILLIS
						: session == null || session.isFinished() || waited >= CHECK_GRACE_MILLIS;

				if (phase == Phase.CHECKING && done) {
					enter(Phase.TIMED_OUT, now);
					playSound("entity.villager.no", 1.0f);
				}
			}
			default -> {
			}
		}
	}

	/** The server's verdict for a server challenge. */
	void serverResult(Outcome outcome, long now) {
		closeSession();

		switch (outcome) {
			case CORRECT -> {
				if (phase != Phase.CORRECT) {
					enter(Phase.CORRECT, now);
					playSound("entity.player.levelup", 1.4f);
				}
			}
			case CLEARED -> {
				// After a wrong pick the card keeps showing the right word.
				if (phase != Phase.WRONG) {
					enter(Phase.CLEARED, now);
				}

				playSound("entity.item.break", 0.8f);
			}
			case TIMED_OUT -> {
				if (phase != Phase.TIMED_OUT && phase != Phase.WRONG) {
					enter(Phase.TIMED_OUT, now);
					playSound("entity.villager.no", 1.0f);
				}
			}
			case DROPPED -> enter(Phase.DROPPED, now);
			case CANCELLED -> {
				// A flashcard is shown because this client cancelled; anything else just closes.
				if (phase != Phase.FLASHCARD) {
					over = true;
				}
			}
		}
	}

	boolean isOver(long now) {
		return over || switch (phase) {
			case CORRECT, WRONG, TIMED_OUT, CLEARED -> now - phaseStart >= RESULT_MILLIS;
			case DROPPED -> now - phaseStart >= DROPPED_MILLIS;
			case FLASHCARD -> now - phaseStart >= answerMillis;
			default -> false;
		};
	}

	/** Picks quiz answer {@code index} (an answer key was pressed). */
	boolean choose(int index) {
		if (phase != Phase.QUIZ || index < 0 || index >= options.size()) {
			return false;
		}

		boolean right = options.get(index).equals(word);
		long now = System.currentTimeMillis();
		enter(right ? Phase.CORRECT : Phase.WRONG, now);
		playSound(right ? "entity.player.levelup" : "entity.villager.no", right ? 1.4f : 1.0f);
		sendVerdict(right ? Verdict.CORRECT : Verdict.WRONG);
		return true;
	}

	/** Throws away the words heard so far (the "clear" key). */
	boolean clearHeard() {
		if (phase != Phase.LISTENING || session == null) {
			return false;
		}

		session.clearHeard();
		heard = "";
		checkedGuesses = 0;
		return true;
	}

	void cancel() {
		closeSession();
		over = true;
	}

	public Phase phase() {
		return phase;
	}

	public String heard() {
		return heard;
	}

	/** Why the challenge fell back to the quiz or a flashcard. */
	public String note() {
		return note;
	}

	public List<String> options() {
		return options;
	}

	/** Pronunciation of each option, or null entries for Latin-script words. */
	public List<String> optionSubtitles() {
		return optionSubtitles;
	}

	/** 1 -> 0 while the clock runs. */
	public float timeLeftFraction(long now) {
		long total = phase == Phase.FLASHCARD || phase == Phase.LISTENING || phase == Phase.QUIZ ? answerMillis : 0;
		return total == 0 ? 0 : Math.clamp(1f - (float) (now - phaseStart) / total, 0f, 1f);
	}

	public int secondsLeft(long now) {
		return phase == Phase.LISTENING || phase == Phase.QUIZ ? (int) Math.ceil(Math.max(0, answerMillis - (now - phaseStart)) / 1000.0) : 0;
	}

	private void startListening(long now) {
		try {
			session = opening.join();
			opening = null;
			enter(Phase.LISTENING, now);

			if (isServerGame()) {
				ClientPlayNetworking.send(new SayItPayloads.Listening(serverId));
			}
		} catch (Exception e) {
			opening = null;
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			SayItMod.LOGGER.warn("Could not open the microphone", cause);
			startQuiz(cause.getMessage() == null ? "no microphone" : cause.getMessage(), now);
		}
	}

	/**
	 * The answer and a few other item names, spelled the way the speech model may know them. The decoys keep
	 * the word-list recognizer from forcing any sound onto the answer.
	 */
	private List<String> wordList() {
		List<String> words = new ArrayList<>(Romanizer.speechPhrases(word, languageCode));

		for (String decoy : QuizOptions.decoys(word, languageCode, 10, new Random())) {
			words.addAll(Romanizer.speechPhrases(decoy, languageCode));
		}

		return words;
	}

	private void checkAnswer(ClientConfig config, long now) {
		String latest = session.heard();
		List<String> guesses = session.candidates();

		if (latest.equals(heard) && guesses.size() == checkedGuesses) {
			return;
		}

		heard = latest;
		checkedGuesses = guesses.size();
		boolean matched = guesses.stream().anyMatch(guess -> {
			List<String> words = new ArrayList<>();

			for (String heardWord : guess.split("\\s+")) {
				words.add(Romanizer.matchKey(heardWord, languageCode));
			}

			return AnswerMatcher.matches(expectedKey, words, SayItClient.matchThreshold());
		});

		if (matched) {
			session.close();
			enter(Phase.CORRECT, now);
			playSound("entity.player.levelup", 1.4f);

			sendVerdict(Verdict.CORRECT);
		}
	}

	/** Without speech the player picks the word from three; the server clock starts now. */
	private void startQuiz(String reason, long now) {
		List<String> picks = QuizOptions.build(word, languageCode, new Random());

		if (picks == null) {
			showFlashcard(reason, now);
			return;
		}

		List<String> subtitles = new ArrayList<>();

		for (String option : picks) {
			subtitles.add(Romanizer.subtitle(option, languageCode));
		}

		options = picks;
		optionSubtitles = subtitles;
		note = reason;
		enter(Phase.QUIZ, now);

		if (isServerGame()) {
			ClientPlayNetworking.send(new SayItPayloads.Listening(serverId));
		}
	}

	private void showFlashcard(String reason, long now) {
		note = reason;
		enter(Phase.FLASHCARD, now);

		// The player cannot answer at all, so the server game must not take the item.
		sendVerdict(Verdict.UNJUDGEABLE);
	}

	private void sendVerdict(Verdict verdict) {
		if (isServerGame()) {
			ClientPlayNetworking.send(new SayItPayloads.Answer(serverId, verdict.ordinal()));
		}
	}

	private void closeSession() {
		if (session != null) {
			session.close();
		}

		// Still opening: release the microphone as soon as it is open.
		if (opening != null) {
			opening.thenAccept(ListenSession::close);
			opening = null;
		}
	}

	private void enter(Phase next, long now) {
		phase = next;
		phaseStart = now;
	}

	private static void playSound(String id, float pitch) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		Entity player = minecraft.player;
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));

		// On the client, Level.playSound only plays sounds caused by the local player, which this is.
		if (level != null && player != null && sound != null) {
			level.playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.7f, pitch);
		}
	}
}
