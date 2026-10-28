package com.sayitmc.client.speech;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.sayitmc.client.ClientConfig;
import com.sayitmc.text.Romanizer;

/** Knows which Vosk model serves each language, and downloads + unpacks it on first use. */
public final class SpeechModels {
	private static final String BASE_URL = "https://alphacephei.com/vosk/models/";

	/** Small (~40-50 MB) models from https://alphacephei.com/vosk/models, keyed by language prefix. */
	private static final Map<String, String> SMALL_MODELS = Map.ofEntries(
			Map.entry("en", "vosk-model-small-en-us-0.15"),
			Map.entry("de", "vosk-model-small-de-0.15"),
			Map.entry("fr", "vosk-model-small-fr-0.22"),
			Map.entry("es", "vosk-model-small-es-0.42"),
			Map.entry("it", "vosk-model-small-it-0.22"),
			Map.entry("pt", "vosk-model-small-pt-0.3"),
			Map.entry("nl", "vosk-model-small-nl-0.22"),
			Map.entry("ru", "vosk-model-small-ru-0.22"),
			Map.entry("uk", "vosk-model-small-uk-v3-small"),
			Map.entry("pl", "vosk-model-small-pl-0.22"),
			Map.entry("tr", "vosk-model-small-tr-0.3"),
			Map.entry("ja", "vosk-model-small-ja-0.22"),
			Map.entry("zh", "vosk-model-small-cn-0.22"),
			Map.entry("ko", "vosk-model-small-ko-0.22"),
			Map.entry("vi", "vosk-model-small-vn-0.4"),
			Map.entry("hi", "vosk-model-small-hi-0.22"),
			Map.entry("ca", "vosk-model-small-ca-0.4"),
			Map.entry("cs", "vosk-model-small-cs-0.4-rhasspy"),
			Map.entry("sv", "vosk-model-small-sv-rhasspy-0.15"),
			Map.entry("eo", "vosk-model-small-eo-0.42"),
			Map.entry("fa", "vosk-model-small-fa-0.42"));

	private SpeechModels() {
	}

	/** Download URL for the language, preferring the user's override in the config. */
	public static Optional<String> urlFor(String languageCode) {
		String lang = Romanizer.languagePrefix(languageCode);
		Map<String, String> overrides = ClientConfig.get().speechModelUrls;
		String override = overrides.getOrDefault(languageCode, overrides.get(lang));

		if (override != null && !override.isBlank()) {
			return Optional.of(override.trim());
		}

		return Optional.ofNullable(SMALL_MODELS.get(lang)).map(name -> BASE_URL + name + ".zip");
	}

	/** Returns the unpacked model directory, downloading it first if needed. Blocking. */
	public static Path install(String url, Path modelsDir, DoubleConsumer progress) throws IOException, InterruptedException {
		String fileName = url.substring(url.lastIndexOf('/') + 1);
		String name = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
		Path target = modelsDir.resolve(name);

		if (Files.isDirectory(target)) {
			return target;
		}

		Files.createDirectories(modelsDir);
		Path zip = modelsDir.resolve(fileName + ".part");
		Path staging = modelsDir.resolve(".unpack-" + name);

		try {
			download(url, zip, progress);
			deleteRecursively(staging);
			unzip(zip, staging);
			Path root = findModelRoot(staging).orElseThrow(() -> new IOException("No Vosk model found inside " + fileName));
			Files.move(root, target, StandardCopyOption.ATOMIC_MOVE);
			return target;
		} finally {
			Files.deleteIfExists(zip);
			deleteRecursively(staging);
		}
	}

	private static void download(String url, Path destination, DoubleConsumer progress) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.proxy(ProxySelector.getDefault())
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
		}

		long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);

		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(destination)) {
			byte[] buffer = new byte[64 * 1024];
			long done = 0;
			int read;

			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
				done += read;

				if (total > 0) {
					progress.accept((double) done / total);
				}
			}
		}
	}

	private static void unzip(Path zip, Path destination) throws IOException {
		Path root = destination.toAbsolutePath().normalize();

		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;

			while ((entry = in.getNextEntry()) != null) {
				Path out = root.resolve(entry.getName()).normalize();

				if (!out.startsWith(root)) {
					throw new IOException("Refusing to unpack " + entry.getName() + " outside the model folder");
				}

				if (entry.isDirectory()) {
					Files.createDirectories(out);
				} else {
					Files.createDirectories(out.getParent());
					Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/** A Vosk model folder contains {@code am/} or {@code conf/}; zips usually wrap it in one top-level folder. */
	private static Optional<Path> findModelRoot(Path dir) throws IOException {
		try (Stream<Path> paths = Files.walk(dir, 3)) {
			return paths.filter(Files::isDirectory)
					.filter(p -> Files.isDirectory(p.resolve("am")) || Files.isDirectory(p.resolve("conf")))
					.min(Comparator.comparingInt(Path::getNameCount));
		}
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}

		try (Stream<Path> paths = Files.walk(dir)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
