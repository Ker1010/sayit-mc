package com.sayitmc.client.speech;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

import com.sayitmc.SayItMod;
import com.sayitmc.text.Romanizer;

/** Keeps the speech model for the target language loaded, fetching it in the background the first time. */
public final class SpeechService {
	public enum State {
		IDLE,
		DOWNLOADING,
		LOADING,
		READY,
		UNAVAILABLE
	}

	/** A failed download (e.g. no internet) is tried again after this long. */
	private static final long RETRY_MILLIS = 60_000;

	private final Path modelsDir;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SayIt speech model loader");
		thread.setDaemon(true);
		return thread;
	});

	private volatile State state = State.IDLE;
	private volatile double downloadProgress;
	private volatile String problem = "";
	private volatile long failedAt;
	private String languageCode;
	private Model model;
	private Future<?> pending;
	private ListenSession active;
	private CompletableFuture<ListenSession> opening;

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
		boolean retry = state == State.UNAVAILABLE && System.currentTimeMillis() - failedAt > RETRY_MILLIS
				&& SpeechModels.urlFor(code).isPresent();

		if (code.equals(languageCode) && state != State.IDLE && !retry) {
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
		return state == State.READY && opening == null && (active == null || active.isFinished());
	}

	/**
	 * Opens the microphone and sets up the recognizers off the render thread (both can take a few hundred
	 * milliseconds, which would freeze the game). Only valid while {@link #canListen()}.
	 */
	public synchronized CompletableFuture<ListenSession> listen(List<String> wordList) {
		if (!canListen()) {
			return CompletableFuture.failedFuture(new IllegalStateException("speech model or microphone is not ready"));
		}

		Model loaded = model;
		CompletableFuture<ListenSession> started = CompletableFuture.supplyAsync(() -> {
			try {
				return ListenSession.start(loaded, wordList);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, worker);
		opening = started;
		started.whenComplete((session, error) -> {
			synchronized (this) {
				if (session != null) {
					active = session;
				}

				opening = null;
			}
		});
		return started;
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

			SayItMod.LOGGER.info("Loaded speech model {} for {}", dir.getFileName(), code);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			// UnsatisfiedLinkError lands here too, e.g. on CPUs Vosk has no native library for.
			SayItMod.LOGGER.error("Could not load speech model for {} from {}", code, url, t);
			fail(code, Objects.requireNonNullElse(t.getMessage(), t.getClass().getSimpleName()));
		}
	}

	private synchronized void fail(String code, String reason) {
		if (isCurrent(code)) {
			problem = reason;
			failedAt = System.currentTimeMillis();
			state = State.UNAVAILABLE;
		}
	}

	private synchronized boolean isCurrent(String code) {
		return code.equals(languageCode);
	}
}
