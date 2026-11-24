package com.asistente.movil

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

data class ModelInfo(
    val id: String,
    val label: String,
    /** Dólares por millón de tokens (aproximados, para mostrar un gasto estimado). */
    val inPrice: Double,
    val outPrice: Double,
    val cacheReadPrice: Double,
)

val MODELS = listOf(
    ModelInfo("claude-haiku-5-5", "Haiku 5.5 · el más rápido y barato", 0.10, 0.50, 0.01),
    ModelInfo("claude-sonnet-5-5", "Sonnet 5.5 · equilibrado (recomendado)", 2.0, 10.0, 0.10),
    ModelInfo("claude-opus-5-5", "Opus 5.5 · el más capaz y el más caro", 4.0, 20.0, 0.20),
)

val VOICE_LANGS = listOf(
    "auto" to "Automático (idioma del teléfono)",
    "es-CO" to "Español (Colombia)",
    "es-MX" to "Español (México)",
    "es-ES" to "Español (España)",
    "es-US" to "Español (EE. UU.)",
    "en-US" to "English (US)",
    "pt-BR" to "Português (Brasil)",
)

val EFFORTS = listOf(
    "low" to "Bajo: más rápido y barato",
    "medium" to "Medio (recomendado)",
    "high" to "Alto: piensa más, más lento",
)

object Prefs {
    private val sp: SharedPreferences
        get() = AsistenteApp.ctx.getSharedPreferences("asistente", Context.MODE_PRIVATE)

    var consent: Boolean
        get() = sp.getBoolean("consent", false)
        set(v) {
            sp.edit().putBoolean("consent", v).apply()
        }

    /** La API key se guarda cifrada con el Keystore; nunca en texto plano. */
    val apiKey: String?
        get() = sp.getString("key_enc", null)?.let { SecureStore.decrypt(it) }

    fun hasApiKey(): Boolean = sp.contains("key_enc")

    fun saveApiKey(key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        val enc = SecureStore.encrypt(k) ?: return false
        sp.edit().putString("key_enc", enc).apply()
        return true
    }

    fun clearApiKey() {
        sp.edit().remove("key_enc").apply()
    }

    var model: String
        get() = sp.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        set(v) {
            sp.edit().putString("model", v).apply()
        }

    var effort: String
        get() = sp.getString("effort", "medium") ?: "medium"
        set(v) {
            sp.edit().putString("effort", v).apply()
        }

    var language: String
        get() = sp.getString("language", "auto") ?: "auto"
        set(v) {
            sp.edit().putString("language", v).apply()
        }

    var speak: Boolean
        get() = sp.getBoolean("speak", false)
        set(v) {
            sp.edit().putBoolean("speak", v).apply()
        }

    var showBubble: Boolean
        get() = sp.getBoolean("show_bubble", true)
        set(v) {
            sp.edit().putBoolean("show_bubble", v).apply()
        }

    var maxSteps: Int
        get() = sp.getInt("max_steps", 30)
        set(v) {
            sp.edit().putInt("max_steps", v).apply()
        }

    var extraBlocked: String
        get() = sp.getString("extra_blocked", "") ?: ""
        set(v) {
            sp.edit().putString("extra_blocked", v).apply()
        }

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) {
            sp.edit().putInt("bubble_x", v).apply()
        }

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) {
            sp.edit().putInt("bubble_y", v).apply()
        }

    fun voiceLocale(): Locale =
        if (language == "auto") Locale.getDefault() else Locale.forLanguageTag(language)
}
