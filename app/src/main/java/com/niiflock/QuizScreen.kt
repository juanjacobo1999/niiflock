package com.niiflock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val GreenText = Color(0xFF2E7D32)
private val RedText = Color(0xFFC62828)
private val GreenFill = Color(0xFFC8E6C9)
private val RedFill = Color(0xFFFFCDD2)
private val DarkText = Color(0xFF1B1B1B)

/**
 * Pantalla de pregunta.
 * [appLabel] es el nombre de la app bloqueada; si es null es el modo práctica (no desbloquea nada).
 */
@Composable
fun QuizScreen(
    appLabel: String?,
    minutes: Int,
    pickQuestion: () -> Question,
    onUnlock: () -> Unit,
    onLeave: () -> Unit,
) {
    var question by remember { mutableStateOf(pickQuestion()) }
    var selected by remember(question) { mutableStateOf<Int?>(null) }
    // Orden en que se muestran las opciones (cada posición apunta a la opción original).
    val order = remember(question) { question.opciones.indices.shuffled() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (appLabel != null) "«$appLabel» está bloqueada" else "Modo práctica",
                style = MaterialTheme.typography.titleLarge,
            )
            if (appLabel != null) {
                Text(
                    text = "Responde bien para usarla durante $minutes min.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = question.norma,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = question.texto, style = MaterialTheme.typography.titleMedium)

            val chosen = selected
            val answered = chosen != null
            order.forEach { original ->
                val isCorrect = original == question.correcta
                val isChosen = chosen == original
                val container = when {
                    answered && isCorrect -> GreenFill
                    answered && isChosen -> RedFill
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val content = if (answered && (isCorrect || isChosen)) {
                    DarkText
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Button(
                    onClick = { if (selected == null) selected = original },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = container,
                        contentColor = content,
                    ),
                ) {
                    Text(
                        text = question.opciones[original],
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
            }

            if (chosen != null) {
                val ok = chosen == question.correcta
                Text(
                    text = if (ok) "¡Correcto!" else "Incorrecto",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ok) GreenText else RedText,
                )
                if (question.explicacion.isNotBlank()) {
                    Text(text = question.explicacion, style = MaterialTheme.typography.bodyMedium)
                }
                if (ok && appLabel != null) {
                    Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir $appLabel")
                    }
                } else {
                    Button(
                        onClick = { question = pickQuestion() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (ok) "Otra pregunta" else "Intentar con otra pregunta")
                    }
                }
                OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                    Text(if (appLabel != null) "Salir" else "Cerrar")
                }
            } else if (appLabel != null) {
                TextButton(onClick = onLeave) { Text("Salir sin abrir") }
            }
        }
    }
}
