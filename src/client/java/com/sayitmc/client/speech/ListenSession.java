package com.sayitmc.client.speech;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.sayitmc.SayItMod;

/**
 * Streams the microphone into two Vosk recognizers on its own thread until {@link #close()} is called:
 * a free one that reports its top guesses, and one limited to a word list (the answer plus decoys), which
 * hears short words much more reliably.
 */
public final class ListenSession implements AutoCloseable {
	/** Vosk models are trained on 16 kHz, but it resamples, so fall back to what the microphone supports. */
	private static final float[] SAMPLE_RATES = {16000f, 48000f, 44100f};
	private static final int ALTERNATIVES = 3;

	private final TargetDataLine line;
	private final Recognizer free;
	/** Null when the word list could not be used with this model. */
	private final Recognizer limited;
	/** How sure the limited recognizer must be of every word before its guess counts. */
	private final double minConfidence;
	/** Best guess of each finished phrase, for showing what was heard. */
	private final List<String> finalTexts = new CopyOnWriteArrayList<>();
	/** Every guess of every finished phrase, from both recognizers, for checking the answer. */
	private final List<String> candidates = new CopyOnWriteArrayList<>();
	private volatile String partialText = "";
	private volatile boolean running = true;
	private volatile boolean clearRequested;
	private volatile boolean finished;

	private ListenSession(TargetDataLine line, Recognizer free, Recognizer limited, double minConfidence) {
		this.line = line;
		this.free = free;
		this.limited = limited;
		this.minConfidence = minConfidence;
	}

	/** @param wordList phrases the limited recognizer may hear, e.g. the answer and a few other item names */
	static ListenSession start(Model model, List<String> wordList, double minConfidence) throws LineUnavailableException, IOException {
		TargetDataLine line = openMicrophone();
		float rate = line.getFormat().getSampleRate();
		Recognizer free;

		try {
			free = new Recognizer(model, rate);
			free.setMaxAlternatives(ALTERNATIVES);
		} catch (IOException | RuntimeException e) {
			line.close();
			throw e;
		}

		ListenSession session = new ListenSession(line, free, limitedRecognizer(model, rate, wordList), minConfidence);
		Thread thread = new Thread(session::run, "SayIt microphone");
		thread.setDaemon(true);
		thread.start();
		return session;
	}

	private static Recognizer limitedRecognizer(Model model, float rate, List<String> wordList) {
		if (wordList.isEmpty()) {
			return null;
		}

		JsonArray grammar = new JsonArray();
		wordList.forEach(grammar::add);
		// Lets noise and other words come out as "unknown" instead of being forced onto a listed word.
		grammar.add("[unk]");

		try {
			Recognizer recognizer = new Recognizer(model, rate, grammar.toString());

			if (recognizer.getPointer() == null) {
				SayItMod.LOGGER.warn("Speech model refused the word list, using free recognition only");
				return null;
			}

			// Per-word confidences, so a sound only roughly like a listed word can be rejected.
			recognizer.setWords(true);

			return recognizer;
		} catch (RuntimeException e) {
			SayItMod.LOGGER.warn("Speech model does not support word lists, using free recognition only", e);
			return null;
		}
	}

	/** Everything recognized so far, including the words still being spoken. */
	public String heard() {
		String done = String.join(" ", finalTexts);
		String partial = partialText;
		return partial.isEmpty() ? done : (done + " " + partial).trim();
	}

	/**
	 * All guesses for finished phrases so far (both recognizers, top alternatives). Words still being spoken
	 * do not count: the recognizer's guesses jump around until the phrase ends.
	 */
	public List<String> candidates() {
		return List.copyOf(candidates);
	}

	/** True once the microphone is released and the last words have been flushed into {@link #heard()}. */
	public boolean isFinished() {
		return finished;
	}

	/** Forgets everything heard so far, e.g. a misheard word, so the player can say it again cleanly. */
	public void clearHeard() {
		finalTexts.clear();
		candidates.clear();
		partialText = "";
		clearRequested = true;
	}

	@Override
	public void close() {
		running = false;
	}

	private void run() {
		// 100 ms of 16-bit mono audio per read keeps the stop latency low.
		byte[] buffer = new byte[(int) (line.getFormat().getSampleRate() / 10) * 2];

		try {
			line.start();

			while (running) {
				int read = line.read(buffer, 0, buffer.length);

				if (clearRequested) {
					clearRequested = false;
					free.reset();

					if (limited != null) {
						limited.reset();
					}

					finalTexts.clear();
					candidates.clear();
					partialText = "";
					continue;
				}

				if (read <= 0) {
					continue;
				}

				if (free.acceptWaveForm(buffer, read)) {
					addFreeResult(free.getResult());
				} else {
					partialText = field(free.getPartialResult(), "partial");
				}

				// Only finished phrases count from the word list; its partial guesses jump around too much.
				if (limited != null && limited.acceptWaveForm(buffer, read)) {
					addLimitedResult(limited.getResult());
				}
			}

			addFreeResult(free.getFinalResult());

			if (limited != null) {
				addLimitedResult(limited.getFinalResult());
			}
		} catch (Throwable t) {
			SayItMod.LOGGER.warn("Speech recognition stopped", t);
		} finally {
			line.stop();
			line.close();
			free.close();

			if (limited != null) {
				limited.close();
			}

			finished = true;
		}
	}

	/** With alternatives on, results look like {"alternatives": [{"text": ...}, ...]}. */
	private void addFreeResult(String json) {
		JsonObject result = JsonParser.parseString(json).getAsJsonObject();
		JsonArray alternatives = result.getAsJsonArray("alternatives");
		List<String> texts = new ArrayList<>();

		if (alternatives != null) {
			for (JsonElement alternative : alternatives) {
				JsonElement text = alternative.getAsJsonObject().get("text");

				if (text != null && !text.getAsString().isBlank()) {
					texts.add(text.getAsString().trim());
				}
			}
		} else {
			String text = field(json, "text");

			if (!text.isEmpty()) {
				texts.add(text);
			}
		}

		if (!texts.isEmpty()) {
			finalTexts.add(texts.getFirst());
			candidates.addAll(texts);
		}

		partialText = "";
	}

	/** Results look like {"result": [{"conf": 0.97, "word": ...}, ...], "text": ...}. */
	private void addLimitedResult(String json) {
		JsonObject result = JsonParser.parseString(json).getAsJsonObject();
		JsonArray words = result.getAsJsonArray("result");
		String text = field(json, "text").replace("[unk]", "").trim();

		if (text.isEmpty() || words == null || words.isEmpty()) {
			return;
		}

		for (JsonElement word : words) {
			JsonObject entry = word.getAsJsonObject();
			JsonElement conf = entry.get("conf");

			if (conf == null || conf.getAsDouble() < minConfidence || "[unk]".equals(entry.get("word").getAsString())) {
				return;
			}
		}

		candidates.add(text);
	}

	private static String field(String json, String name) {
		JsonElement value = JsonParser.parseString(json).getAsJsonObject().get(name);
		return value == null ? "" : value.getAsString().trim();
	}

	private static TargetDataLine openMicrophone() throws LineUnavailableException {
		LineUnavailableException failure = null;

		for (float rate : SAMPLE_RATES) {
			AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

			if (!AudioSystem.isLineSupported(info)) {
				continue;
			}

			try {
				TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format);
				return line;
			} catch (LineUnavailableException e) {
				failure = e;
			}
		}

		throw failure != null ? failure : new LineUnavailableException("no microphone found");
	}
}
