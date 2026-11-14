package dev.edoras.translator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traductor automático de chat, solo servidor. Sin comandos:
 *  1. Cada jugador recibe las traducciones en el idioma de su Minecraft (es, en, pt, fr, it; si no, inglés).
 *  2. Cada mensaje se manda normal y, debajo, a los que hablan otro idioma les llega la traducción.
 *  3. Si el mensaje ya está en tu idioma, no ves nada extra.
 */
public final class ChatTranslator implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("chattranslator");

	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chattranslator");

	private static ModConfig config;
	private static final Translator TRANSLATOR = new Translator();
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		config = ModConfig.load(DIR.resolve("config.json"));
		Translator.WARN = msg -> LOGGER.warn("[ChatTranslator] {}", msg);

		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> server = null);

		// El mensaje original se manda normal (chat firmado intacto); la traducción llega abajo.
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
				onChat(message.signedContent(), sender));

		LOGGER.info("[ChatTranslator] Listo. Idiomas: {} · idioma de respaldo: {} · traductor: {} (respaldo: {})",
				config.languages, config.fallbackLanguage, config.provider, config.fallbackProvider);
	}

	/** Idioma al que hay que traducirle a este jugador, según el idioma de su Minecraft. */
	private static String targetLang(ServerPlayer p) {
		return Langs.targetFor(p.clientInformation().language(), config.languages, config.fallbackLanguage);
	}

	private static void onChat(String raw, ServerPlayer sender) {
		if (server == null || raw == null) return;
		String text = raw.strip().replace("§", "");
		if (text.length() < config.minLength) return;
		if (text.length() > config.maxLength) text = text.substring(0, config.maxLength);

		// Agrupar a los que leen por idioma: una sola traducción por idioma.
		Map<String, List<UUID>> byLang = new HashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p == sender) continue;
			byLang.computeIfAbsent(targetLang(p), k -> new ArrayList<>()).add(p.getUUID());
		}
		if (byLang.isEmpty()) return;

		final String original = text;
		final String senderName = sender.getName().getString();
		final String senderLang = Langs.fromMinecraftLocale(sender.clientInformation().language());
		final MinecraftServer srv = server;
		final ModConfig cfg = config;

		byLang.forEach((lang, readers) ->
			TRANSLATOR.translate(original, lang, senderLang, cfg).thenAccept(res -> {
				if (res == null || res.text().isBlank()) return;
				// El traductor detecta solo en qué idioma está escrito; si ya es el del que lee, no se muestra nada.
				if (Langs.sameLanguage(res.detectedLang(), lang)) return;
				if (res.text().strip().equalsIgnoreCase(original)) return;

				Component line = buildLine(cfg, senderName, original, res, lang);
				srv.execute(() -> {
					for (UUID id : readers) {
						ServerPlayer p = srv.getPlayerList().getPlayer(id);
						if (p != null) p.sendSystemMessage(line);
					}
				});
			})
		);
	}

	private static Component buildLine(ModConfig cfg, String player, String original, Translator.Result res, String to) {
		String from = res.detectedLang() == null || res.detectedLang().isBlank() ? "?" : Langs.tag(res.detectedLang());
		String translated = res.text().replace("§", "");
		// Los &-códigos se convierten ANTES de meter el texto del jugador, así nadie puede colorear el chat.
		String line = cfg.format.replace('&', '§')
				.replace("{player}", player)
				.replace("{from}", from)
				.replace("{to}", Langs.tag(to))
				.replace("{text}", translated);
		// Pasando el mouse por encima se ve el mensaje original.
		Component hover = Component.literal("§7Original (" + from + "):\n§f" + original);
		return Component.literal(line).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
	}
}
