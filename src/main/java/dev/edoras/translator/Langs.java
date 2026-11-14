package dev.edoras.translator;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pasa el idioma del Minecraft de cada jugador (es_ar, pt_br, en_us...) a un código corto (es, pt, en...). */
public final class Langs {
	private Langs() {}

	/** "es_ar" -> "es", "pt_br" -> "pt", "en_gb" -> "en", "fr_ca" -> "fr"... */
	public static String fromMinecraftLocale(String locale) {
		if (locale == null || locale.isBlank()) return "";
		return base(locale);
	}

	/**
	 * Idioma al que se le traduce a un jugador: el de su Minecraft si está en la lista de
	 * idiomas soportados; si no, el idioma de respaldo (inglés por defecto).
	 */
	public static String targetFor(String minecraftLocale, List<String> supported, String fallback) {
		String lang = fromMinecraftLocale(minecraftLocale);
		for (String s : supported) {
			if (base(s).equals(lang)) return base(s);
		}
		return base(fallback);
	}

	/** Parte base del código: "es_ar" -> "es", "pt-BR" -> "pt", "EN-US" -> "en". */
	public static String base(String code) {
		if (code == null) return "";
		String c = code.toLowerCase(Locale.ROOT).replace('_', '-').trim();
		int i = c.indexOf('-');
		return i > 0 ? c.substring(0, i) : c;
	}

	public static boolean sameLanguage(String a, String b) {
		return !base(a).isEmpty() && base(a).equals(base(b));
	}

	/** Etiqueta corta para el chat: "ES", "EN", "PT"... */
	public static String tag(String code) {
		return base(code).toUpperCase(Locale.ROOT);
	}

	// ------------------------------------------------------------ detector simple de idioma
	// Solo hace falta para MyMemory, que no detecta el idioma de origen solo.

	private static final Map<String, Set<String>> WORDS = Map.of(
		"es", Set.of("el", "los", "las", "que", "y", "en", "una", "es", "por", "para", "con", "no", "lo", "como", "pero",
				"hola", "alguien", "tiene", "tengo", "vos", "estoy", "esta", "gracias", "bueno", "si", "ya", "muy", "donde",
				"yo", "mi", "te", "hay", "voy", "vamos", "tambien", "ahora", "hacer", "quien", "porque", "cuando", "nada", "todo"),
		"en", Set.of("the", "a", "an", "is", "are", "you", "i", "to", "and", "of", "it", "that", "this", "what", "have",
				"has", "do", "does", "not", "yes", "for", "with", "my", "your", "hello", "hi", "thanks", "please", "can",
				"where", "how", "who", "ok", "anyone", "me", "we", "be", "was", "just", "now", "here", "there", "get", "go", "come", "out", "something"),
		"pt", Set.of("o", "os", "e", "em", "um", "uma", "nao", "voce", "eu", "tenho", "tem", "obrigado", "ola", "oi",
				"vem", "na", "minha", "meu", "isso", "esta", "com", "mais", "muito", "sim", "alguem", "aqui", "agora", "tambem", "vou"),
		"fr", Set.of("le", "les", "et", "un", "une", "est", "je", "tu", "il", "vous", "nous", "pas", "oui", "merci",
				"bonjour", "salut", "qui", "dans", "avec", "pour", "sur", "ce", "c", "ai", "suis", "moi", "quoi", "ici"),
		"it", Set.of("il", "lo", "gli", "di", "un", "una", "non", "io", "sono", "ciao", "grazie", "che", "per", "con",
				"ho", "hai", "mi", "ti", "come", "dove", "anche", "qualcuno", "questo", "sei", "va", "bene", "adesso")
	);

	/** Adivina el idioma de un mensaje entre es, en, pt, fr, it. Devuelve null si no está claro. */
	public static String guess(String text) {
		if (text == null || text.isBlank()) return null;
		String lower = text.toLowerCase(Locale.ROOT);
		Map<String, Integer> score = new java.util.HashMap<>();
		// Letras que delatan el idioma.
		if (lower.matches("(?s).*[ñ¿¡].*")) score.merge("es", 3, Integer::sum);
		if (lower.matches("(?s).*[ãõ].*")) score.merge("pt", 3, Integer::sum);
		if (lower.matches("(?s).*[êœ].*")) score.merge("fr", 2, Integer::sum);
		if (lower.matches("(?s).*[ìò].*")) score.merge("it", 2, Integer::sum);
		String plain = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		for (String w : plain.split("[^\\p{L}]+")) {
			if (w.isEmpty()) continue;
			for (Map.Entry<String, Set<String>> e : WORDS.entrySet()) {
				if (e.getValue().contains(w)) score.merge(e.getKey(), 1, Integer::sum);
			}
		}
		String best = null;
		int bestScore = 0;
		boolean tie = false;
		for (Map.Entry<String, Integer> e : score.entrySet()) {
			if (e.getValue() > bestScore) { best = e.getKey(); bestScore = e.getValue(); tie = false; }
			else if (e.getValue() == bestScore) tie = true;
		}
		return bestScore > 0 && !tie ? best : null;
	}
}
