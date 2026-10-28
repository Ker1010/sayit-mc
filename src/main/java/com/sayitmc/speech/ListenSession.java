package com.sayitmc.speech;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.sayitmc.SayItClient;

/** Streams the microphone into a Vosk recognizer on its own thread until {@link #close()} is called. */
public final class ListenSession implements AutoCloseable {
	/** Vosk models are trained on 16 kHz, but it resamples, so fall back to what the microphone supports. */
	private static final float[] SAMPLE_RATES = {16000f, 48000f, 44100f};

	private final TargetDataLine line;
	private final Recognizer recognizer;
	private final List<String> finalTexts = new CopyOnWriteArrayList<>();
	private volatile String partialText = "";
	private volatile boolean running = true;
	private volatile boolean finished;

	private ListenSession(TargetDataLine line, Recognizer recognizer) {
		this.line = line;
		this.recognizer = recognizer;
	}

	static ListenSession start(Model model) throws LineUnavailableException, IOException {
		TargetDataLine line = openMicrophone();
		Recognizer recognizer;

		try {
			recognizer = new Recognizer(model, line.getFormat().getSampleRate());
		} catch (IOException | RuntimeException e) {
			line.close();
			throw e;
		}

		ListenSession session = new ListenSession(line, recognizer);
		Thread thread = new Thread(session::run, "SayIt microphone");
		thread.setDaemon(true);
		thread.start();
		return session;
	}

	/** Everything recognized so far, including the words still being spoken. */
	public String heard() {
		String done = String.join(" ", finalTexts);
		String partial = partialText;
		return partial.isEmpty() ? done : (done + " " + partial).trim();
	}

	/** True once the microphone is released and the last words have been flushed into {@link #heard()}. */
	public boolean isFinished() {
		return finished;
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

				if (read <= 0) {
					continue;
				}

				if (recognizer.acceptWaveForm(buffer, read)) {
					addFinal(recognizer.getResult());
				} else {
					partialText = field(recognizer.getPartialResult(), "partial");
				}
			}

			addFinal(recognizer.getFinalResult());
		} catch (Throwable t) {
			SayItClient.LOGGER.warn("Speech recognition stopped", t);
		} finally {
			line.stop();
			line.close();
			recognizer.close();
			finished = true;
		}
	}

	private void addFinal(String json) {
		String text = field(json, "text");

		if (!text.isEmpty()) {
			finalTexts.add(text);
		}

		partialText = "";
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
