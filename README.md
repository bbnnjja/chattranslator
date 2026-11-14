# Chat Translator (Fabric 1.21.11, solo servidor)

Traduce el chat del servidor automáticamente. **No tiene comandos y los jugadores no instalan nada.**

## Cómo funciona

1. El mod lee en qué idioma tiene configurado su Minecraft cada jugador.
2. Cuando alguien escribe, el mensaje sale normal y el traductor detecta solo en qué idioma está.
3. Debajo, a cada jugador que habla otro idioma le aparece la traducción:

```
<Benja> hola, alguien tiene hierro?
 ↳ [ES] Benja: hello, does anyone have iron?      (lo ve el que juega en inglés)
 ↳ [ES] Benja: olá, alguém tem ferro?             (lo ve el que juega en portugués)
```

- Si el mensaje ya está en tu idioma, no ves nada extra.
- Pasando el mouse por encima de la traducción se ve el mensaje original.
- Idiomas: **español, inglés, portugués, francés e italiano**. Si un jugador tiene el Minecraft en otro idioma (alemán, ruso…), le llega en **inglés**.

## Compilar el .jar

### Opción A: GitHub, sin instalar nada
1. Creá un repositorio nuevo en github.com (puede ser privado).
2. Subí **todo el contenido** de esta carpeta (incluida la carpeta oculta `.github`).
3. Andá a la pestaña **Actions**: se compila solo en unos 3–5 minutos.
4. Entrá al build terminado → **Artifacts** → `chattranslator-jar`. Adentro está `chattranslator-1.21.11-1.0.1.jar`.

### Opción B: en tu PC
1. Instalá Java 25 (por ejemplo, Temurin 25 de adoptium.net).
2. Abrí una terminal en esta carpeta y ejecutá:
   - Windows: `gradlew.bat build`
   - Linux/Mac: `./gradlew build`
3. El .jar queda en `build/libs/chattranslator-1.21.11-1.0.1.jar` (usá ese, no el que dice `-sources`).

## Instalar en el servidor

1. Subí el .jar a la carpeta `mods/` del hosting.
2. Tiene que estar **Fabric API** para 1.21.11 en `mods/` (seguramente ya la tenés).
3. Reiniciá. En la consola tiene que aparecer: `[ChatTranslator] Listo. Idiomas: [es, en, pt, fr, it]...`

## Configuración (opcional)

El archivo se crea solo en `config/chattranslator/config.json`. Los cambios se aplican al reiniciar el servidor.

| Opción | Qué hace |
|---|---|
| `languages` | Idiomas a los que se traduce. Por defecto `["es","en","pt","fr","it"]` |
| `fallbackLanguage` | Idioma para los que tienen el Minecraft en otro idioma. Por defecto `"en"` |
| `provider` | `"google"` (gratis, sin clave), `"deepl"` o `"libretranslate"` |
| `fallbackProvider` | Traductor de respaldo si el principal falla o lo frenan: `mymemory` (por defecto, gratis y sin clave) o `none` |
| `rateLimitCooldownMinutes` | Minutos que se pausa un traductor cuando responde "demasiados pedidos" (HTTP 429). Por defecto 10 |
| `myMemoryEmail` | Opcional: con un mail, MyMemory pasa de 5.000 a 50.000 caracteres por día |
| `deeplApiKey` | Tu clave de DeepL si usás `"deepl"` |
| `minLength` / `maxLength` | Largo mínimo para traducir y máximo (se corta) |
| `format` | Cómo se ve la línea. `{player}`, `{from}`, `{to}`, `{text}` y colores con `&` |

### Sobre el proveedor
- **google** (por defecto): gratis y sin registrarse. Usa el traductor público de Google; para un SMP chico anda bien, pero no es un servicio oficial y Google podría limitarlo si hay muchísimo chat.
- **deepl**: mejor calidad. Creás una cuenta “DeepL API Free” (500.000 caracteres por mes gratis), copiás la clave en `deeplApiKey` y ponés `"provider": "deepl"`.

## Notas
- No toca el chat original (el chat firmado y los reportes de Mojang siguen funcionando).
- La traducción se pide en segundo plano: si el traductor tarda o se cae, el servidor no se traba. Solo se deja de ver la traducción y aparece un aviso en la consola.
- No traduce los mensajes privados (`/msg`), solo el chat general.
- Usa los mappings oficiales de Mojang, así que pasarlo a 26.x más adelante es más fácil.

## Novedades 1.0.1
- Si Google frena las traducciones (error HTTP 429), el mod lo pausa unos minutos y usa MyMemory mientras tanto, así la traducción no se corta.
- Avisa una sola vez en la consola cuando pasa eso, en vez de repetir el error.
- Nuevo traductor `mymemory`, con detector propio del idioma de cada mensaje.
