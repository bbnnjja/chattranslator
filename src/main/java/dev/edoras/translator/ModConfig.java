package dev.edoras.translator;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Valores de config.yml (plugins/ChatTranslator/config.yml). */
public final class ModConfig {
	public List<String> languages = List.of("es", "en", "pt", "fr", "it");
	public String fallbackLanguage = "en";
	public String provider = "google";
	public String fallbackProvider = "mymemory";
	public int rateLimitCooldownMinutes = 10;
	public String myMemoryEmail = "";
	public String deeplApiKey = "";
	public String libreTranslateUrl = "https://libretranslate.com";
	public String libreTranslateApiKey = "";
	public int minLength = 2;
	public int maxLength = 256;
	public int timeoutSeconds = 6;
	public String format = "&8 ↳ &7[{from}] {player}: &f{text}";

	public static ModConfig from(FileConfiguration c) {
		ModConfig m = new ModConfig();
		List<String> langs = c.getStringList("languages");
		if (!langs.isEmpty()) m.languages = List.copyOf(langs);
		m.fallbackLanguage = c.getString("fallbackLanguage", m.fallbackLanguage);
		m.provider = c.getString("provider", m.provider);
		m.fallbackProvider = c.getString("fallbackProvider", m.fallbackProvider);
		m.rateLimitCooldownMinutes = c.getInt("rateLimitCooldownMinutes", m.rateLimitCooldownMinutes);
		m.myMemoryEmail = c.getString("myMemoryEmail", m.myMemoryEmail);
		m.deeplApiKey = c.getString("deeplApiKey", m.deeplApiKey);
		m.libreTranslateUrl = c.getString("libreTranslateUrl", m.libreTranslateUrl);
		m.libreTranslateApiKey = c.getString("libreTranslateApiKey", m.libreTranslateApiKey);
		m.minLength = c.getInt("minLength", m.minLength);
		m.maxLength = c.getInt("maxLength", m.maxLength);
		m.timeoutSeconds = c.getInt("timeoutSeconds", m.timeoutSeconds);
		m.format = c.getString("format", m.format);
		return m;
	}
}
