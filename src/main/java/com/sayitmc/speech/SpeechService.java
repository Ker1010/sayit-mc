package com.sayitmc.speech;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

import com.sayitmc.Romanizer;
import com.sayitmc.SayItClient;

/** Keeps the speech model for the target language loaded, fetching it in the background the first time. */
public final class SpeechService {
	public enum State {
		IDLE,
		DOWNLOADING,
		LOADING,
		READY,
		UNAVAILABLE
	}

	private final Path modelsDir;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SayIt speech model loader");
		thread.setDaemon(true);
		return thread;
	});

	private volatile State state = State.IDLE;
	private volatile double downloadProgress;
	private volatile String problem = "";
	private String languageCode;
	private Model model;
	private Future<?> pending;
	private ListenSession active;

	public SpeechService(Path modelsDir) {
		this.modelsDir = modelsDir;
	}

	public State state() {
		return state;
	}

	public double downloadProgress() {
		return downloadProgress;
	}

	/** Why speech is {@link State#UNAVAILABLE}. */
	public String problem() {
		return problem;
	}

	/** Starts getting the model for this language ready; cheap to call repeatedly. */
	public synchronized void prepare(String code) {
		if (code.equals(languageCode) && state != State.IDLE) {
			return;
		}

		unload();
		languageCode = code;

		// Warm up the romanizer off the render thread too; Kuromoji's dictionary takes a moment to load.
		worker.execute(() -> Romanizer.subtitle("小麦", code));

		Optional<String> url = SpeechModels.urlFor(code);

		if (url.isEmpty()) {
			fail(code, "no speech model for " + code + " (set one in config/sayitmc.json)");
			return;
		}

		state = State.DOWNLOADING;
		downloadProgress = 0;
		pending = worker.submit(() -> load(code, url.get()));
	}

	/** True when the model is loaded and the previous recording has released the microphone. */
	public synchronized boolean canListen() {
		return state == State.READY && (active == null || active.isFinished());
	}

	/** Opens the microphone. Only valid while {@link #canListen()}. */
	public synchronized ListenSession listen() throws Exception {
		if (!canListen()) {
			throw new IllegalStateException("speech model or microphone is not ready");
		}

		active = ListenSession.start(model);
		return active;
	}

	/** Frees the loaded model; the next {@link #prepare} starts over (and retries failed downloads). */
	public synchronized void unload() {
		if (pending != null) {
			pending.cancel(true);
			pending = null;
		}

		if (model != null) {
			// Vosk reference-counts models, so recognizers still running keep theirs alive.
			model.close();
			model = null;
		}

		languageCode = null;
		state = State.IDLE;
		problem = "";
	}

	private void load(String code, String url) {
		try {
			Path dir = SpeechModels.install(url, modelsDir, progress -> downloadProgress = progress);

			synchronized (this) {
				if (!isCurrent(code)) {
					return;
				}

				state = State.LOADING;
			}

			LibVosk.setLogLevel(LogLevel.WARNINGS);
			Model loaded = new Model(dir.toString());

			synchronized (this) {
				if (!isCurrent(code)) {
					loaded.close();
					return;
				}

				model = loaded;
				state = State.READY;
			}

			SayItClient.LOGGER.info("Loaded speech model {} for {}", dir.getFileName(), code);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			// UnsatisfiedLinkError lands here too, e.g. on CPUs Vosk has no native library for.
			SayItClient.LOGGER.error("Could not load speech model for {} from {}", code, url, t);
			fail(code, Objects.requireNonNullElse(t.getMessage(), t.getClass().getSimpleName()));
		}
	}

	private synchronized void fail(String code, String reason) {
		if (isCurrent(code)) {
			problem = reason;
			state = State.UNAVAILABLE;
		}
	}

	private synchronized boolean isCurrent(String code) {
		return code.equals(languageCode);
	}
}
