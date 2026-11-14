# Chat Translator (plugin para Paper / Purpur 26.3)

La versión plugin del mod Chat Translator. Hace lo mismo: traduce el chat automáticamente, **sin comandos**, y los jugadores no instalan nada.

## Cómo funciona

1. El plugin lee el idioma que cada jugador tiene puesto en su Minecraft.
2. Cuando alguien escribe, el mensaje sale normal y el traductor detecta solo en qué idioma está.
3. Debajo, a cada jugador que habla otro idioma le aparece la traducción:

```
<Benja> hola, alguien tiene hierro?
 ↳ [ES] Benja: hello, does anyone have iron?
```

- Si el mensaje ya está en tu idioma, no ves nada extra.
- Pasando el mouse por encima de la traducción se ve el mensaje original.
- Idiomas: español, inglés, portugués, francés e italiano. Cualquier otro idioma recibe inglés.
- Respeta muteos y filtros de otros plugins: si un mensaje se bloquea, no se traduce.

## Compilar el .jar

### Opción A: GitHub, sin instalar nada
1. Creá un repositorio en github.com y subí **todo** el contenido de esta carpeta (incluida `.github`).
2. En la pestaña **Actions** se compila solo en unos minutos.
3. Entrá al build terminado → **Artifacts** → `chattranslator-plugin-jar`. Adentro está `ChatTranslator-plugin-26.3-1.0.1.jar`.

### Opción B: en tu PC
1. Instalá Java 25.
2. En esta carpeta ejecutá `gradlew.bat build` (Windows) o `./gradlew build` (Linux/Mac).
3. El .jar queda en `build/libs/`.

## Instalar

1. Subí el .jar a la carpeta `plugins/` del servidor.
2. Reiniciá. En la consola tiene que aparecer: `[ChatTranslator] Listo. Idiomas: [es, en, pt, fr, it]...`
3. La configuración se crea sola en `plugins/ChatTranslator/config.yml`.

## Configuración

| Opción | Qué hace |
|---|---|
| `languages` | Idiomas a los que se traduce |
| `fallbackLanguage` | Idioma para los que tienen el Minecraft en otro idioma |
| `provider` | `google` (gratis, sin clave), `deepl` o `libretranslate` |
| `fallbackProvider` | Traductor de respaldo si el principal falla o lo frenan: `mymemory` (por defecto, gratis y sin clave) o `none` |
| `rateLimitCooldownMinutes` | Minutos que se pausa un traductor cuando responde "demasiados pedidos" (HTTP 429). Por defecto 10 |
| `myMemoryEmail` | Opcional: con un mail, MyMemory pasa de 5.000 a 50.000 caracteres por día |
| `deeplApiKey` | Tu clave de DeepL si usás `deepl` |
| `minLength` / `maxLength` | Largo mínimo para traducir y máximo |
| `format` | Cómo se ve la línea, con `{player}`, `{from}`, `{to}`, `{text}` y colores con `&` |

Los cambios se aplican al reiniciar.

## Notas
- Los mensajes del chat se envían a un servicio de traducción externo (Google, DeepL o LibreTranslate).
- Solo traduce el chat público, no los `/msg`.
- Funciona en Paper y Purpur 26.3 (necesitan Java 25). No funciona en Spigot ni en Folia.

## Novedades 1.0.1
- Si Google frena las traducciones (error HTTP 429), el mod lo pausa unos minutos y usa MyMemory mientras tanto, así la traducción no se corta.
- Avisa una sola vez en la consola cuando pasa eso, en vez de repetir el error.
- Nuevo traductor `mymemory`, con detector propio del idioma de cada mensaje.
