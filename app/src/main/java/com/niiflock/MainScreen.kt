package com.niiflock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

private val OkGreen = Color(0xFF2E7D32)
private val WarnRed = Color(0xFFC62828)

/** ¿Está activado el servicio de accesibilidad de NiifLock en los ajustes del sistema? */
fun isAccessibilityEnabled(ctx: Context): Boolean {
    val component = ComponentName(ctx, GateAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        ctx.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

/** [refreshTick] cambia cada vez que se vuelve a esta pantalla, para releer el estado de los permisos. */
@Composable
fun MainScreen(refreshTick: Int) {
    val ctx = LocalContext.current

    val accessibilityOn = remember(refreshTick) { isAccessibilityEnabled(ctx) }
    var enabled by remember { mutableStateOf(Prefs.isEnabled(ctx)) }
    var minutes by remember { mutableIntStateOf(Prefs.unlockMinutes(ctx)) }
    var blocked by remember { mutableStateOf(Prefs.blockedPackages(ctx)) }
    var disabledNorms by remember { mutableStateOf(Prefs.disabledNorms(ctx)) }

    val apps = remember(refreshTick) { Apps.launchable(ctx) }
    val norms = remember { QuestionBank.norms(ctx) }

    fun setBlocked(pkg: String, on: Boolean) {
        blocked = if (on) blocked + pkg else blocked - pkg
        Prefs.setBlockedPackages(ctx, blocked)
    }

    fun setNorm(norm: String, on: Boolean) {
        disabledNorms = if (on) disabledNorms - norm else disabledNorms + norm
        Prefs.setDisabledNorms(ctx, disabledNorms)
    }

    fun chooseMinutes(m: Int) {
        minutes = m
        Prefs.setUnlockMinutes(ctx, m)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.systemBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NiifLock", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Elige qué apps bloquear. Para abrirlas tendrás que responder una pregunta de NIC/NIIF.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item(key = "accessibility") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Servicio de accesibilidad", style = MaterialTheme.typography.titleMedium)
                    if (accessibilityOn) {
                        Text("Activado", color = OkGreen, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(
                            "Desactivado: sin esto no se bloquea nada.",
                            color = WarnRed,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Si Android no te deja activarlo (opción en gris), ve a Ajustes › Aplicaciones › " +
                                "NiifLock › ⋮ › «Permitir ajustes restringidos» y vuelve a intentarlo.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text("Abrir ajustes de accesibilidad")
                        }
                    }
                }
            }

            item(key = "enabled") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Bloqueo activo",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            enabled = on
                            Prefs.setEnabled(ctx, on)
                        },
                    )
                }
            }

            item(key = "div-1") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item(key = "minutes-title") {
                Text("2. Tiempo desbloqueada tras acertar", style = MaterialTheme.typography.titleMedium)
            }
            items(listOf(5, 15, 30, 60), key = { "minutes-$it" }) { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { chooseMinutes(m) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = minutes == m, onClick = { chooseMinutes(m) })
                    Text("$m minutos", style = MaterialTheme.typography.bodyLarge)
                }
            }

            item(key = "div-2") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item(key = "norms-title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("3. Normas en las preguntas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Desmarca las que no quieras practicar. Si desmarcas todas, se usan todas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(norms, key = { "norm-$it" }) { norm ->
                val checked = norm !in disabledNorms
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setNorm(norm, !checked) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { on -> setNorm(norm, on) })
                    Text(norm, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item(key = "practice") {
                OutlinedButton(
                    onClick = { ctx.startActivity(Intent(ctx, QuizActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Probar una pregunta")
                }
            }

            item(key = "div-3") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item(key = "apps-title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("4. Apps a bloquear", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ajustes, teléfono y emergencias nunca se pueden bloquear.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(apps, key = { "app-${it.packageName}" }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in blocked,
                    onToggle = { on -> setBlocked(app.packageName, on) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val icon = remember(app.packageName) {
        try {
            ctx.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}
