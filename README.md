# NiifLock y Asistente

Este repositorio tiene dos apps de Android que se compilan juntas en GitHub Actions:

| App | Carpeta | Qué hace |
|---|---|---|
| **NiifLock** | `app/` | Bloquea las apps que elijas hasta que respondas bien una pregunta de NIC/NIIF. |
| **Asistente** | `assistant/` | Claude en tu celular: un botón flotante al que le escribes o le hablas, y que abre apps, toca y escribe por ti. Ver [Asistente](#asistente-claude-en-tu-celular). |

> **Estado:** el código está escrito pero **no se ha probado en un teléfono**. Si algo falla al compilar o al usarlo, copia el error o describe qué pasó y pásaselo a Claude para corregirlo.

# NiifLock

App de Android que bloquea las apps que tú elijas hasta que respondas bien una pregunta de NIC/NIIF.

## Cómo funciona

- Un servicio de accesibilidad detecta qué app pasa al frente. Solo lee el nombre del paquete, no el contenido de la pantalla.
- Si la app está en tu lista y no está desbloqueada, se abre la pregunta encima.
- Aciertas: se abre la app y queda desbloqueada 5, 15, 30 o 60 minutos (lo eliges). Fallas: sale otra pregunta. «Salir» te lleva al inicio, y el botón Atrás también.
- Cuando vence el tiempo, la pregunta vuelve a salir (se revisa cada ~15 segundos).
- Nunca se pueden bloquear: Ajustes, Teléfono/Marcador, llamadas de emergencia, la propia NiifLock ni el launcher.
- Si el proceso de la app se reinicia, todo vuelve a quedar bloqueado.

## Conseguir el APK sin Android Studio

### Opción A: GitHub Actions (la más segura)

1. Crea una cuenta gratis en github.com y un repositorio vacío.
2. Sube todo este proyecto al repositorio, incluida la carpeta oculta `.github`.
3. Entra en la pestaña **Actions**, elige **Build APK** y pulsa **Run workflow**. También se ejecuta solo con cada subida a `main`.
4. A los 5-10 minutos aparece en **Releases** una versión «Build N» con dos archivos: `NiifLock.apk` y `Asistente.apk`. Descarga el que quieras desde el celular.

### Opción B: compilar en el chat de Claude

Necesita que el espacio de trabajo tenga salida a internet y pueda llegar a `dl.google.com` y `maven.google.com` (para el SDK de Android y las librerías). Se compila con `gradle :app:assembleDebug :assistant:assembleDebug` y los APK quedan en `app/build/outputs/apk/debug/app-debug.apk` y `assistant/build/outputs/apk/debug/assistant-debug.apk`.

### Opción C: en un computador

JDK 17 y Android SDK 35 instalados, y luego `gradle :app:assembleDebug :assistant:assembleDebug` (o `./gradlew …` si añades el wrapper).

## Instalar y activar

1. Abre `NiifLock.apk` en el celular y permite instalar apps de origen desconocido si te lo pide.
   - **Google Play Protect** puede mostrar «Se bloqueó la app para proteger tu dispositivo». Es normal con apps de accesibilidad instaladas fuera de Play Store. Para instalarla: Play Store › tu foto › **Play Protect** › ⚙ › apaga **Analizar apps con Play Protect**, instala, y vuelve a encenderlo después.
2. Abre NiifLock y pulsa **Abrir ajustes de accesibilidad**. Entra en NiifLock y actívalo.
   - Android 13 o superior: si la opción sale en gris, ve a Ajustes › Aplicaciones › NiifLock › ⋮ › **Permitir ajustes restringidos** y repite el paso.
3. Marca las apps que quieres bloquear.
4. En Samsung, Xiaomi, Huawei y similares, pon la batería de NiifLock en **Sin restricciones**. Si no, el sistema puede cerrar el servicio y el bloqueo deja de funcionar.

## Cosas que conviene saber

- Se ve un parpadeo breve de la app bloqueada antes de que aparezca la pregunta.
- Es una herramienta de autocontrol, no un candado. Puedes apagar el interruptor «Bloqueo activo», desactivar el servicio en Ajustes › Accesibilidad o desinstalar la app.
- Google Play restringe las apps de este tipo, así que se instala por APK y es para uso personal.

## Editar las preguntas

Están en `app/src/main/assets/preguntas.json`. Cada pregunta tiene este formato:

```json
{"norma": "NIC 2", "pregunta": "...", "opciones": ["A", "B", "C", "D"], "correcta": 1, "explicacion": "..."}
```

`correcta` es la posición de la opción correcta, empezando en 0. Evita las comillas dobles dentro de los textos. Las opciones se barajan al mostrarlas.

El banco trae 100 preguntas de 37 normas (Marco Conceptual, NIC, NIIF y NIIF para las PYMES). Se redactaron como material de práctica: verifícalas contra el texto oficial de la IFRS Foundation y avisa si alguna está mal.

---

# Asistente (Claude en tu celular)

Una app de Android parecida a Claude Code, pero para el teléfono. Aparece como un **botón flotante** sobre cualquier pantalla. Le escribes o le hablas («abre YouTube y busca recetas de arepas», «pon una alarma a las 6:30», «resume mis notificaciones») y Claude lo hace: lee la pantalla, toca, escribe, abre apps, usa alarmas, mensajes, ajustes y archivos.

## Cómo se usa

- **Tocar** el botón: abre el panel de chat. **Mantenerlo pulsado**: dicta una orden por voz. **Arrastrarlo**: lo mueves (se pega al borde).
- Mientras trabaja se pone **rojo**: tócalo para detenerlo al instante. Verde = escuchando.
- La app también tiene su propia pantalla de chat y de ajustes (modelo, esfuerzo de razonamiento, idioma de voz, leer respuestas en voz alta, apps protegidas, gasto aproximado).

## Instalar y activar

1. Descarga `Asistente.apk` desde **Releases** e instálalo (permite apps de origen desconocido si te lo pide).
   - Si Google Play Protect lo bloquea, apaga temporalmente **Analizar apps con Play Protect** (Play Store › tu foto › Play Protect › ⚙), instala, y vuelve a encenderlo.
2. Abre la app, lee el aviso de privacidad y acéptalo.
3. En **Ajustes › 1**, pega tu **API key** de Claude (créala en <https://console.anthropic.com/settings/keys>) y pulsa **Probar conexión**. Se guarda cifrada en el teléfono (Android Keystore). **Nunca pegues la clave en un chat.**
4. En **Ajustes › 2**, activa el **servicio de accesibilidad** (Ajustes › Accesibilidad › Asistente). Si sale en gris (Android 13+): Ajustes › Aplicaciones › Asistente › ⋮ › **Permitir ajustes restringidos**, y vuelve a intentarlo.
5. Concede el **micrófono** (para voz) y, si quieres que maneje archivos, **Acceso a todos los archivos**.
6. En Samsung, Xiaomi, Huawei y similares, pon la batería de la app en **Sin restricciones** para que el sistema no cierre el servicio.

## Qué puede hacer

Unas 29 herramientas, agrupadas así:

- **Pantalla:** leer, tocar (por número de elemento o por texto), mantener pulsado, escribir, Enter, desplazar, deslizar, Atrás / Inicio / Recientes / notificaciones / ajustes rápidos / bloquear, capturas de pantalla.
- **Apps y sistema:** abrir apps, listarlas, abrir enlaces y pantallas de ajustes, volumen, linterna, alarmas y temporizadores, preparar SMS / correo / llamada (siempre queda para que tú confirmes), info del dispositivo.
- **Archivos** (con «todos los archivos»): listar, buscar, leer, escribir y borrar. Escribir y borrar piden permiso cada vez.

## Costos

La API de Claude **se cobra aparte** de cualquier suscripción de Claude. Pagas por tokens usados. Una orden sencilla suele costar centavos; los modelos más grandes cuestan más y las órdenes largas (muchas pantallas leídas) también. La app muestra un gasto aproximado de la sesión en Ajustes. Por defecto usa Sonnet 5.5; Haiku 5.5 es el más barato y Opus 5.5 el más capaz. Revisa los precios vigentes y fija un límite de gasto en la consola de Anthropic.

## Privacidad y seguridad

- Lo que la app **ve en pantalla** (textos, nombres, mensajes, y capturas cuando las pide) y lo que le dices **se envía a Anthropic** por la API, con tu clave. No va a ningún otro servidor. El chat vive solo en memoria: no se guarda en el teléfono.
- **Apps protegidas** (no actúa en ellas): bancos, billeteras, criptomonedas, gestores de contraseñas, autenticadores e instalador de paquetes. Puedes añadir más palabras en Ajustes.
- No escribe contraseñas ni PIN. Pide permiso en pantalla antes de enviar, pagar, borrar, llamar o instalar, y antes de escribir o borrar archivos. Si no contestas en 2 minutos, se toma como un «no».
- Lo que lee de la pantalla se marca como **no confiable** para Claude: una página o mensaje que diga «ignora tus instrucciones» no debería obedecerse.
- Hay un tope de pasos por orden (15, 30 o 50) y el botón rojo detiene todo.

## Límites

- **No tiene acceso a «todo».** Android bloquea que las apps lean ciertas pantallas (apps marcadas como seguras, algunos pagos), y algunas apps (juegos, ciertas webs o apps hechas con motores propios) no exponen sus botones al servicio de accesibilidad. En esos casos Claude puede pedir una captura, que a veces también está bloqueada.
- No puede desbloquear el teléfono, ni instalar apps en silencio, ni saltarse los permisos del sistema.
- Se puede equivocar: míralo trabajar, especialmente al principio. Es una herramienta personal, no un producto terminado.
- Necesita Android 8 o superior; las capturas de pantalla, Android 11 o superior.
- **No se ha probado en un teléfono real.** Espera algunas rondas de ajustes.

## Para quien quiera tocar el código

Kotlin, Compose (pantallas) y vistas clásicas (botón flotante). Todo en `assistant/src/main/java/com/asistente/movil/`:

| Archivo | Rol |
|---|---|
| `Agent.kt` | Bucle del agente: manda la conversación a Claude, ejecuta las herramientas que pide y devuelve el resultado. |
| `ClaudeApi.kt` | Cliente HTTP de la API de Mensajes (reintentos, errores en español). |
| `Tools.kt` | Definición y ejecución de las herramientas. |
| `Screen.kt` | Lectura del árbol de accesibilidad y acciones sobre la pantalla (toques, gestos, capturas). |
| `Policy.kt` | Apps protegidas y campos sensibles. |
| `AssistantService.kt`, `Overlay.kt` | Servicio de accesibilidad y botón/panel flotante. |
| `Voice.kt` | Reconocimiento de voz (en una actividad transparente, porque Android no deja usar el micrófono en segundo plano) y lectura en voz alta. |
| `Session.kt`, `Prefs.kt`, `SecureStore.kt` | Estado de la sesión, ajustes y cifrado de la clave. |
| `Screens.kt`, `Theme.kt`, `MainActivity.kt` | Pantallas de la app (bienvenida, chat, ajustes). |
