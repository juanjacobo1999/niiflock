package com.asistente.movil

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF2E7D32)
private val WarnRed = Color(0xFFC62828)

// ───────────────────────── estado de permisos ─────────────────────────

fun isServiceEnabled(ctx: Context): Boolean {
    val component = ComponentName(ctx, AssistantService::class.java)
    val enabled = Settings.Secure.getString(
        ctx.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

fun hasMic(ctx: Context): Boolean =
    ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

// ───────────────────────── raíz ─────────────────────────

@Composable
fun AppRoot() {
    var screen by remember { mutableStateOf(if (Prefs.consent) "chat" else "welcome") }
    BackHandler(enabled = screen == "settings") { screen = "chat" }
    when (screen) {
        "welcome" -> WelcomeScreen(
            onAccept = {
                Prefs.consent = true
                screen = if (Prefs.hasApiKey()) "chat" else "settings"
            },
        )
        "settings" -> SettingsScreen(onBack = { screen = "chat" })
        else -> ChatScreen(onSettings = { screen = "settings" })
    }
}

// ───────────────────────── bienvenida y aviso de privacidad ─────────────────────────

@Composable
fun WelcomeScreen(onAccept: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Asistente", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Claude en tu teléfono: le hablas o le escribes, y él abre apps, toca, escribe y busca por ti. " +
                    "Aparece como un botón flotante sobre cualquier pantalla.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Antes de empezar, ten en cuenta:", style = MaterialTheme.typography.titleMedium)
            Text(
                "• Para actuar, la app lee tu pantalla y puede tocar y escribir por ti. Lo que ve en pantalla " +
                    "(textos, nombres, mensajes) y lo que le pidas se envía a Anthropic, a través de la API y con TU clave. " +
                    "No va a ningún otro servidor, y el chat no se guarda en el teléfono.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "• La API se cobra aparte de cualquier suscripción de Claude: pagas por lo que uses. " +
                    "Una orden típica cuesta centavos; los modelos grandes cuestan más. Verás un gasto aproximado en Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "• No actúa en apps de bancos, billeteras, criptomonedas ni gestores de contraseñas, y no escribe " +
                    "contraseñas ni PIN. Antes de enviar, pagar, borrar, llamar o instalar, te pide permiso.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "• Puede equivocarse. Mira lo que hace y usa el botón rojo para detenerlo en cualquier momento.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "• No hay acceso mágico a «todo»: Android bloquea la lectura de algunas pantallas (apps seguras, " +
                    "capturas prohibidas) y eso se respeta.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { accepted = !accepted },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Text("Entiendo cómo funciona y acepto.", style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onAccept, enabled = accepted, modifier = Modifier.fillMaxWidth()) {
                Text("Empezar")
            }
        }
    }
}

// ───────────────────────── chat ─────────────────────────

@Composable
fun ChatScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val msgs by Session.messages.collectAsState()
    val running by Session.running.collectAsState()
    val status by Session.status.collectAsState()
    val pending by Session.pending.collectAsState()
    var input by remember { mutableStateOf("") }
    var serviceOn by remember { mutableStateOf(isServiceEnabled(ctx)) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = isServiceEnabled(ctx)
            delay(1000)
        }
    }
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
    }

    fun send() {
        val t = input.trim()
        if (t.isNotEmpty() && !running) {
            Session.submit(t)
            input = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Asistente",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { Session.newConversation() }) { Text("Nueva") }
                TextButton(onClick = onSettings) { Text("Ajustes") }
            }

            if (!serviceOn) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "El servicio de accesibilidad está apagado: puedo conversar, pero no actuar en el teléfono.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onSettings) { Text("Activar") }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (msgs.isEmpty()) {
                    item(key = "hint") { EmptyHint() }
                }
                items(msgs, key = { it.id }) { m -> MessageRow(m) }
            }

            if (running && status.isNotBlank()) {
                Text(
                    status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe una orden…") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                IconButton(
                    onClick = {
                        if (!running) ctx.startActivity(Intent(ctx, VoiceActivity::class.java))
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Hablar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (running) {
                    Button(
                        onClick = { Session.cancel() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Detener") }
                } else {
                    Button(onClick = { send() }, enabled = input.isNotBlank()) { Text("Enviar") }
                }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { Session.answer(false) },
            title = { Text("¿Permitir esta acción?") },
            text = { Text(p.text) },
            confirmButton = { TextButton(onClick = { Session.answer(true) }) { Text("Permitir") } },
            dismissButton = { TextButton(onClick = { Session.answer(false) }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Prueba con órdenes como:", style = MaterialTheme.typography.titleMedium)
        Text("• «Abre YouTube y busca recetas de arepas»", style = MaterialTheme.typography.bodyMedium)
        Text("• «Pon una alarma mañana a las 6:30»", style = MaterialTheme.typography.bodyMedium)
        Text("• «Abre las notificaciones y resúmelas»", style = MaterialTheme.typography.bodyMedium)
        Text("• «Busca en Descargas el PDF de la factura»", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Con el botón flotante puedes hacerlo desde cualquier app: tócalo para escribir o mantenlo pulsado para hablar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun MessageRow(m: ChatMsg) {
    when (m.role) {
        Role.USER -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ChatBubble(m.text, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        }
        Role.ASSISTANT -> Row(modifier = Modifier.fillMaxWidth()) {
            ChatBubble(m.text, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Role.STATUS -> Text(
            m.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Role.ERROR -> Text(
            m.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ChatBubble(text: String, background: Color, foreground: Color) {
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

// ───────────────────────── ajustes ─────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun RadioRow(selected: Boolean, label: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermRow(title: String, ok: Boolean, help: String, action: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                if (ok) "Activado" else "Falta",
                color = if (ok) OkGreen else WarnRed,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!ok) {
            Text(help, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val usage by Session.usage.collectAsState()

    var keyInput by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(Prefs.hasApiKey()) }
    var keyMsg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    var model by remember { mutableStateOf(Prefs.model) }
    var effort by remember { mutableStateOf(Prefs.effort) }
    var language by remember { mutableStateOf(Prefs.language) }
    var speak by remember { mutableStateOf(Prefs.speak) }
    var showBubble by remember { mutableStateOf(Prefs.showBubble) }
    var maxSteps by remember { mutableStateOf(Prefs.maxSteps) }
    var extraBlocked by remember { mutableStateOf(Prefs.extraBlocked) }

    var serviceOn by remember { mutableStateOf(isServiceEnabled(ctx)) }
    var micOn by remember { mutableStateOf(hasMic(ctx)) }
    var filesOn by remember { mutableStateOf(hasAllFilesAccess()) }

    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = isServiceEnabled(ctx)
            micOn = hasMic(ctx)
            filesOn = hasAllFilesAccess()
            delay(1000)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micOn = granted
    }

    fun open(intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            keyMsg = "No pude abrir esa pantalla: ${e.message}"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ajustes",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                )
                TextButton(onClick = onBack) { Text("Volver al chat") }
            }

            // 1. Clave de la API
            SectionTitle("1. Tu clave de la API de Claude")
            Text(
                "Créala en console.anthropic.com › API keys y pégala aquí. Se guarda cifrada en este teléfono " +
                    "y solo se envía a Anthropic. Nunca la pegues en un chat.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (keySaved) "API key guardada (pega otra para reemplazarla)" else "Pega aquí tu API key")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (Prefs.saveApiKey(keyInput)) {
                            keySaved = true
                            keyInput = ""
                            keyMsg = "Clave guardada, cifrada en este teléfono."
                        } else {
                            keyMsg = "No pude guardar la clave. Revisa que esté pegada."
                        }
                    },
                    enabled = keyInput.isNotBlank(),
                ) { Text("Guardar") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            testing = true
                            val k = if (keyInput.isNotBlank()) keyInput.trim() else Prefs.apiKey
                            keyMsg = if (k == null) {
                                "Primero pega y guarda tu API key."
                            } else {
                                ClaudeApi.test(k, Prefs.model)
                            }
                            testing = false
                        }
                    },
                    enabled = !testing && (keySaved || keyInput.isNotBlank()),
                ) { Text(if (testing) "Probando…" else "Probar conexión") }
                if (keySaved) {
                    TextButton(
                        onClick = {
                            Prefs.clearApiKey()
                            keySaved = false
                            keyMsg = "Clave borrada."
                        },
                    ) { Text("Borrar") }
                }
            }
            keyMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(
                onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))) },
            ) { Text("Abrir console.anthropic.com") }

            HorizontalDivider()

            // 2. Permisos
            SectionTitle("2. Permisos")
            PermRow(
                title = "Servicio de accesibilidad",
                ok = serviceOn,
                help = "Es lo que permite el botón flotante y que el asistente toque y escriba. Actívalo en " +
                    "Ajustes › Accesibilidad › Aplicaciones instaladas › Asistente. Si sale en gris, abre «Info de la app», " +
                    "toca ⋮ y elige «Permitir ajustes restringidos»; luego vuelve a intentarlo.",
                action = "Abrir ajustes de accesibilidad",
                onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            if (!serviceOn) {
                OutlinedButton(
                    onClick = {
                        open(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", ctx.packageName, null),
                            ),
                        )
                    },
                ) { Text("Abrir info de la app (ajustes restringidos)") }
            }
            PermRow(
                title = "Micrófono (comandos de voz)",
                ok = micOn,
                help = "Necesario para dictar órdenes.",
                action = "Conceder",
                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
            PermRow(
                title = "Acceso a todos los archivos (opcional)",
                ok = filesOn,
                help = "Solo si quieres que busque, lea o guarde archivos del teléfono. Cada escritura o borrado te pide permiso.",
                action = "Conceder",
                onClick = {
                    open(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + ctx.packageName),
                        ),
                    )
                },
            )

            HorizontalDivider()

            // 3. Modelo
            SectionTitle("3. Modelo")
            Text(
                "Cambiar de modelo o de esfuerzo empieza una conversación nueva. El gasto depende del modelo.",
                style = MaterialTheme.typography.bodySmall,
            )
            for (m in MODELS) {
                RadioRow(selected = model == m.id, label = m.label) {
                    model = m.id
                    Prefs.model = m.id
                }
            }
            Text("Esfuerzo de razonamiento", style = MaterialTheme.typography.titleSmall)
            for ((id, label) in EFFORTS) {
                RadioRow(selected = effort == id, label = label) {
                    effort = id
                    Prefs.effort = id
                }
            }
            Text("Pasos máximos por orden", style = MaterialTheme.typography.titleSmall)
            for (n in listOf(15, 30, 50)) {
                RadioRow(selected = maxSteps == n, label = "$n pasos") {
                    maxSteps = n
                    Prefs.maxSteps = n
                }
            }
            Text(
                "Esta sesión: ${usage.input + usage.cacheRead + usage.cacheWrite} tokens de entrada, " +
                    "${usage.output} de salida · gasto aproximado US$ ${"%.3f".format(usage.cost)}",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            // 4. Botón flotante y voz
            SectionTitle("4. Botón flotante y voz")
            SwitchRow("Mostrar el botón flotante", showBubble) { on ->
                showBubble = on
                Prefs.showBubble = on
                AssistantService.instance?.refreshOverlay()
            }
            SwitchRow("Leer las respuestas en voz alta", speak) { on ->
                speak = on
                Prefs.speak = on
                if (!on) Speaker.stop()
            }
            Text("Idioma de voz", style = MaterialTheme.typography.titleSmall)
            for ((code, label) in VOICE_LANGS) {
                RadioRow(selected = language == code, label = label) {
                    language = code
                    Prefs.language = code
                }
            }

            HorizontalDivider()

            // 5. Seguridad
            SectionTitle("5. Apps protegidas")
            Text(
                "Siempre protegidas: bancos, billeteras, criptomonedas, gestores de contraseñas, autenticadores " +
                    "e instalador de paquetes. Aquí puedes añadir más palabras (del nombre de la app o de su paquete), " +
                    "separadas por comas.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = extraBlocked,
                onValueChange = {
                    extraBlocked = it
                    Prefs.extraBlocked = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Palabras extra (ej. tigo, claro, miempresa)") },
                minLines = 1,
                maxLines = 3,
            )

            HorizontalDivider()

            SectionTitle("Cómo se usa")
            Text(
                "Toca el botón flotante para escribir una orden. Mantenlo pulsado para hablar. Arrástralo para moverlo. " +
                    "Mientras trabaja se pone rojo: tócalo para detenerlo. Las acciones sensibles te piden permiso en pantalla.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
