package dev.edoras.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Habla con el servicio de traducción. Todo es asíncrono: nunca frena el hilo del servidor.
 *
 * 1.0.1: si un servicio responde "demasiados pedidos" (HTTP 429), se deja de usar unos minutos
 * y mientras tanto se usa el de respaldo (MyMemory, gratis y sin registro).
 */
public final class Translator {

	public record Result(String text, String detectedLang) {}

	/** El servicio nos frenó por exceso de pedidos (HTTP 429 o cuota diaria agotada). */
	static final class RateLimitException extends RuntimeException {
		RateLimitException(String msg) { super(msg); }
	}

	/** Dónde se escriben los avisos. Lo configura la clase principal (mod o plugin). */
	public static Consumer<String> WARN = s -> System.err.println("[ChatTranslator] " + s);

	private static final int CACHE_SIZE = 1000;

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final Map<String, Result> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	/** servicio -> hasta cuándo está pausado (ms). */
	private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
	/** servicio -> último aviso en consola (ms), para no llenarla. */
	private final Map<String, Long> lastLog = new ConcurrentHashMap<>();

	public void clearCache() {
		cache.clear();
	}

	/**
	 * @param target     código de {@link Langs} (es, en, pt...)
	 * @param senderHint idioma del Minecraft de quien escribió, por si hay que adivinar el idioma de origen
	 */
	public CompletableFuture<Result> translate(String text, String target, String senderHint, ModConfig cfg) {
		String key = target + "|" + text;
		Result cached = cache.get(key);
		if (cached != null) return CompletableFuture.completedFuture(cached);

		String primary = norm(cfg.provider);
		String backup = norm(cfg.fallbackProvider);
		boolean hasBackup = !backup.isEmpty() && !backup.equals("none") && !backup.equals(primary);

		CompletableFuture<Result> f;
		if (isBlocked(primary) && hasBackup) {
			// El principal está pausado: directo al de respaldo.
			f = call(backup, text, target, senderHint, cfg);
		} else {
			f = call(primary, text, target, senderHint, cfg).handle((r, err) -> {
				if (err == null && r != null) return CompletableFuture.completedFuture(r);
				onError(primary, err, cfg);
				return hasBackup ? call(backup, text, target, senderHint, cfg) : CompletableFuture.<Result>completedFuture(null);
			}).thenCompose(x -> x);
		}

		return f.handle((r, err) -> {
			if (err != null) {
				onError(backup, err, cfg);
				return null;
			}
			if (r != null) cache.put(key, r);
			return r;
		});
	}

	private CompletableFuture<Result> call(String provider, String text, String target, String hint, ModConfig cfg) {
		if (isBlocked(provider)) return CompletableFuture.failedFuture(new RateLimitException(provider + " está en pausa"));
		try {
			return switch (provider) {
				case "deepl" -> deepl(text, target, cfg);
				case "libretranslate", "libre" -> libre(text, target, cfg);
				case "mymemory" -> mymemory(text, target, hint, cfg);
				default -> google(text, target, cfg);
			};
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	// ---------------------------------------------------------------- pausas y avisos

	private boolean isBlocked(String provider) {
		Long until = blockedUntil.get(provider);
		return until != null && System.currentTimeMillis() < until;
	}

	private void onError(String provider, Throwable err, ModConfig cfg) {
		Throwable cause = err;
		while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
				&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		long now = System.currentTimeMillis();
		if (cause instanceof RateLimitException) {
			Long until = blockedUntil.get(provider);
			if (until != null && now < until) return; // ya estaba pausado, ya avisamos
			int minutes = Math.max(1, cfg.rateLimitCooldownMinutes);
			blockedUntil.put(provider, now + minutes * 60_000L);
			WARN.accept(provider + " frenó las traducciones por exceso de pedidos (" + cause.getMessage()
					+ "). Lo pauso " + minutes + " min" + (norm(cfg.fallbackProvider).equals("none") ? "." : " y uso " + norm(cfg.fallbackProvider) + " mientras tanto."));
			return;
		}
		Long last = lastLog.get(provider);
		if (last == null || now - last > 60_000) {
			lastLog.put(provider, now);
			WARN.accept("Falló una traducción con " + provider + ": " + cause);
		}
	}

	private static String norm(String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

	// ---------------------------------------------------------------- Google (gratis, sin clave, no oficial)

	private CompletableFuture<Result> google(String text, String target, ModConfig cfg) {
		String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t"
				+ "&tl=" + enc(target) + "&q=" + enc(text);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(cfg.timeoutSeconds))
				.header("User-Agent", "Mozilla/5.0")
				.GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
			check(resp, "Google");
			// Respuesta: [[["traducción","original",...],...], null, "idioma_detectado", ...]
			JsonArray root = JsonParser.parseString(resp.body()).getAsJsonArray();
			StringBuilder sb = new StringBuilder();
			if (root.size() > 0 && root.get(0).isJsonArray()) {
				for (JsonElement seg : root.get(0).getAsJsonArray()) {
					if (seg.isJsonArray() && seg.getAsJsonArray().size() > 0 && !seg.getAsJsonArray().get(0).isJsonNull()) {
						sb.append(seg.getAsJsonArray().get(0).getAsString());
					}
				}
			}
			String detected = root.size() > 2 && root.get(2).isJsonPrimitive() ? root.get(2).getAsString() : "";
			return new Result(sb.toString(), detected);
		});
	}

	// ---------------------------------------------------------------- MyMemory (gratis, sin registro)
	// 5.000 caracteres por día sin mail, 50.000 con "myMemoryEmail" en la config.
	// No detecta el idioma de origen, así que lo adivinamos nosotros.

	private CompletableFuture<Result> mymemory(String text, String target, String hint, ModConfig cfg) {
		String source = Langs.guess(text);
		if (source == null) source = Langs.base(hint);
		if (source.isEmpty()) source = "en";
		if (Langs.sameLanguage(source, target)) {
			// Ya está en el idioma del que lee: no hay nada que traducir.
			return CompletableFuture.completedFuture(new Result(text, source));
		}
		final String src = source;
		String url = "https://api.mymemory.translated.net/get?q=" + enc(text)
				+ "&langpair=" + enc(src + "|" + target);
		if (cfg.myMemoryEmail != null && !cfg.myMemoryEmail.isBlank()) url += "&de=" + enc(cfg.myMemoryEmail.trim());
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(cfg.timeoutSeconds))
				.GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
			check(resp, "MyMemory");
			JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
			int status = o.has("responseStatus") && o.get("responseStatus").isJsonPrimitive()
					? o.get("responseStatus").getAsInt() : 200;
			String translated = o.getAsJsonObject("responseData").get("translatedText").getAsString();
			if (status == 429 || translated.toUpperCase(Locale.ROOT).contains("MYMEMORY WARNING")) {
				throw new RateLimitException("MyMemory: se terminó la cuota gratis del día");
			}
			if (status != 200) throw new RuntimeException("MyMemory respondió " + status + ": " + translated);
			return new Result(translated, src);
		});
	}

	// ---------------------------------------------------------------- DeepL (mejor calidad, clave gratis 500.000 caracteres/mes)

	private CompletableFuture<Result> deepl(String text, String target, ModConfig cfg) {
		String key = cfg.deeplApiKey == null ? "" : cfg.deeplApiKey.trim();
		if (key.isEmpty()) throw new IllegalStateException("provider=deepl pero deeplApiKey está vacío en la config");
		String host = key.endsWith(":fx") ? "https://api-free.deepl.com" : "https://api.deepl.com";

		JsonObject body = new JsonObject();
		JsonArray texts = new JsonArray();
		texts.add(text);
		body.add("text", texts);
		body.addProperty("target_lang", deeplCode(target));

		HttpRequest req = HttpRequest.newBuilder(URI.create(host + "/v2/translate"))
				.timeout(Duration.ofSeconds(cfg.timeoutSeconds))
				.header("Authorization", "DeepL-Auth-Key " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
			if (resp.statusCode() == 456) throw new RateLimitException("DeepL: se terminó la cuota del mes");
			check(resp, "DeepL");
			JsonObject t = JsonParser.parseString(resp.body()).getAsJsonObject()
					.getAsJsonArray("translations").get(0).getAsJsonObject();
			return new Result(t.get("text").getAsString(),
					t.has("detected_source_language") ? t.get("detected_source_language").getAsString() : "");
		});
	}

	private static String deeplCode(String code) {
		return switch (code) {
			case "en" -> "EN-US";
			case "pt" -> "PT-BR";
			case "pt-PT" -> "PT-PT";
			case "zh-CN" -> "ZH-HANS";
			case "zh-TW" -> "ZH-HANT";
			default -> code.toUpperCase(Locale.ROOT);
		};
	}

	// ---------------------------------------------------------------- LibreTranslate (se puede hostear gratis)

	private CompletableFuture<Result> libre(String text, String target, ModConfig cfg) {
		JsonObject body = new JsonObject();
		body.addProperty("q", text);
		body.addProperty("source", "auto");
		body.addProperty("target", switch (target) {
			case "zh-CN" -> "zh";
			case "zh-TW" -> "zt";
			case "pt-PT" -> "pt";
			default -> target;
		});
		body.addProperty("format", "text");
		if (cfg.libreTranslateApiKey != null && !cfg.libreTranslateApiKey.isBlank()) {
			body.addProperty("api_key", cfg.libreTranslateApiKey.trim());
		}
		String base = cfg.libreTranslateUrl.endsWith("/")
				? cfg.libreTranslateUrl.substring(0, cfg.libreTranslateUrl.length() - 1) : cfg.libreTranslateUrl;

		HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/translate"))
				.timeout(Duration.ofSeconds(cfg.timeoutSeconds))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
			check(resp, "LibreTranslate");
			JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
			String detected = "";
			if (o.has("detectedLanguage") && o.get("detectedLanguage").isJsonObject()) {
				detected = o.getAsJsonObject("detectedLanguage").get("language").getAsString();
			}
			return new Result(o.get("translatedText").getAsString(), detected);
		});
	}

	// ---------------------------------------------------------------- utilidades

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static void check(HttpResponse<String> resp, String who) {
		int code = resp.statusCode();
		if (code == 429) throw new RateLimitException(who + " respondió HTTP 429");
		if (code / 100 != 2) {
			String b = resp.body() == null ? "" : resp.body().replaceAll("\\s+", " ");
			throw new RuntimeException(who + " respondió HTTP " + code + ": " + (b.length() > 120 ? b.substring(0, 120) : b));
		}
	}
}
