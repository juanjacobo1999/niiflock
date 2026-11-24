# NiifLock

App de Android que bloquea las apps que tú elijas hasta que respondas bien una pregunta de NIC/NIIF.

> **Estado:** el código está escrito pero **todavía no se ha compilado ni probado en un teléfono**. Si el primer build falla, copia el error y pásaselo a Claude para corregirlo.

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
4. A los 5-10 minutos aparece en **Releases** una versión «NiifLock build N» con `app-debug.apk`. Descárgala desde el celular.

### Opción B: compilar en el chat de Claude

Necesita que el espacio de trabajo tenga salida a internet y pueda llegar a `dl.google.com` y `maven.google.com` (para el SDK de Android y las librerías). Se compila con `gradle :app:assembleDebug` y el APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Opción C: en un computador

JDK 17 y Android SDK 35 instalados, y luego `./gradlew :app:assembleDebug`.

## Instalar y activar

1. Abre `app-debug.apk` en el celular y permite instalar apps de origen desconocido si te lo pide.
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
