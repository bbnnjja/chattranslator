package dev.edoras.translator;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Traductor automático de chat para Paper / Purpur 26.3. Sin comandos:
 *  1. Cada jugador recibe las traducciones en el idioma de su Minecraft (es, en, pt, fr, it; si no, inglés).
 *  2. El mensaje original sale normal y, debajo, a los que hablan otro idioma les llega la traducción.
 *  3. Si el mensaje ya está en tu idioma, no ves nada extra.
 */
public final class ChatTranslatorPlugin extends JavaPlugin implements Listener {

	private static Logger logger = Logger.getLogger("ChatTranslator");
	private final Translator translator = new Translator();
	private volatile ModConfig config;

	static Logger log() {
		return logger;
	}

	@Override
	public void onEnable() {
		logger = getLogger();
		saveDefaultConfig();
		config = ModConfig.from(getConfig());
		Translator.WARN = msg -> logger.warning(msg);
		getServer().getPluginManager().registerEvents(this, this);
		logger.info("Listo. Idiomas: " + config.languages + " · respaldo: " + config.fallbackLanguage
				+ " · traductor: " + config.provider + " (respaldo: " + config.fallbackProvider + ")");
	}

	/** Idioma al que hay que traducirle a este jugador, según el idioma de su Minecraft ("es_AR" -> "es"). */
	private String targetLang(Player p) {
		return Langs.targetFor(p.locale().toString(), config.languages, config.fallbackLanguage);
	}

	// MONITOR + ignoreCancelled: corre al final y solo si el mensaje realmente se envió
	// (respeta muteos y filtros de otros plugins). No modifica el mensaje original.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChat(AsyncChatEvent event) {
		ModConfig cfg = config;
		String text = PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
		if (text.length() < cfg.minLength) return;
		if (text.length() > cfg.maxLength) text = text.substring(0, cfg.maxLength);

		Player sender = event.getPlayer();
		// Solo a los jugadores que de verdad ven el mensaje, agrupados por idioma: una traducción por idioma.
		Map<String, List<UUID>> byLang = new HashMap<>();
		for (Audience viewer : new ArrayList<>(event.viewers())) {
			if (!(viewer instanceof Player p)) continue;          // la consola no
			if (p.getUniqueId().equals(sender.getUniqueId())) continue;
			byLang.computeIfAbsent(targetLang(p), k -> new ArrayList<>()).add(p.getUniqueId());
		}
		if (byLang.isEmpty()) return;

		final String original = text;
		final String senderName = sender.getName();
		final String senderLang = Langs.fromMinecraftLocale(sender.locale().toString());

		byLang.forEach((lang, readers) ->
			translator.translate(original, lang, senderLang, cfg).thenAccept(res -> {
				if (res == null || res.text().isBlank()) return;
				// El servicio detecta solo en qué idioma está escrito; si ya es el del que lee, no se muestra nada.
				if (Langs.sameLanguage(res.detectedLang(), lang)) return;
				if (res.text().strip().equalsIgnoreCase(original)) return;

				Component line = buildLine(cfg, senderName, original, res, lang);
				// Enviar desde el hilo principal del servidor.
				Bukkit.getScheduler().runTask(this, () -> {
					for (UUID id : readers) {
						Player p = Bukkit.getPlayer(id);
						if (p != null && p.isOnline()) p.sendMessage(line);
					}
				});
			})
		);
	}

	private static Component buildLine(ModConfig cfg, String player, String original,
									   Translator.Result res, String to) {
		String from = res.detectedLang() == null || res.detectedLang().isBlank() ? "?" : Langs.tag(res.detectedLang());
		// Los &-códigos del formato se interpretan, pero la traducción se mete como texto plano:
		// así nadie puede colorear ni romper el chat escribiendo códigos.
		String template = cfg.format
				.replace("{player}", player)
				.replace("{from}", from)
				.replace("{to}", Langs.tag(to));
		Component base = LegacyComponentSerializer.legacyAmpersand().deserialize(template);
		Component line = base.replaceText(TextReplacementConfig.builder()
				.matchLiteral("{text}")
				.replacement(Component.text(res.text()))
				.build());

		// Pasando el mouse por encima se ve el mensaje original.
		Component hover = Component.text("Original (" + from + "):", NamedTextColor.GRAY)
				.append(Component.newline())
				.append(Component.text(original, NamedTextColor.WHITE));
		return line.hoverEvent(HoverEvent.showText(hover));
	}
}
