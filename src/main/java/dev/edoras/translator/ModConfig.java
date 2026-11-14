package dev.edoras.translator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * config/chattranslator/config.json — se crea solo la primera vez que arranca el servidor.
 * Los cambios se aplican al reiniciar el servidor.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Idiomas a los que se traduce. Cada jugador recibe el de su Minecraft si está acá. */
	public List<String> languages = List.of("es", "en", "pt", "fr", "it");

	/** Si el Minecraft de un jugador está en un idioma que no está en la lista, se le traduce a este. */
	public String fallbackLanguage = "en";

	/** "google" (gratis, sin clave), "deepl" (necesita clave), "libretranslate" o "mymemory". */
	public String provider = "google";

	/** Si el principal falla o lo frenan (HTTP 429), se usa este: "mymemory" (gratis, sin clave) o "none". */
	public String fallbackProvider = "mymemory";

	/** Minutos que se deja de usar un servicio cuando responde "demasiados pedidos". */
	public int rateLimitCooldownMinutes = 10;

	/** Opcional: con un mail, MyMemory sube el límite de 5.000 a 50.000 caracteres por día. */
	public String myMemoryEmail = "";

	public String deeplApiKey = "";

	public String libreTranslateUrl = "https://libretranslate.com";
	public String libreTranslateApiKey = "";

	/** Mensajes más cortos que esto no se traducen (ej: "k", "?"). */
	public int minLength = 2;
	/** Mensajes más largos se cortan antes de traducir. */
	public int maxLength = 256;

	public int timeoutSeconds = 6;

	/**
	 * Formato de la línea traducida. Usa &-códigos de color.
	 * {player} = quien escribió, {from} = idioma original, {to} = idioma del que lee, {text} = traducción.
	 */
	public String format = "&8 ↳ &7[{from}] {player}: &f{text}";

	public static ModConfig load(Path file) {
		ModConfig cfg = new ModConfig();
		try {
			if (Files.exists(file)) {
				ModConfig read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
				if (read != null) cfg = read;
			}
			if (cfg.languages == null || cfg.languages.isEmpty()) cfg.languages = List.of("es", "en", "pt", "fr", "it");
			if (cfg.fallbackLanguage == null || cfg.fallbackLanguage.isBlank()) cfg.fallbackLanguage = "en";
			// Reescribe siempre: así aparecen opciones nuevas si actualizás el mod.
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
		} catch (Exception e) {
			ChatTranslator.LOGGER.error("[ChatTranslator] No pude leer {}: {} — uso la config por defecto", file, e.getMessage());
		}
		return cfg;
	}
}
