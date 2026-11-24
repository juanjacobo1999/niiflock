package com.asistente.movil

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

sealed class VoiceEvent {
    data object Listening : VoiceEvent()
    data class Partial(val text: String) : VoiceEvent()
    data class Final(val text: String) : VoiceEvent()
    data class Failed(val message: String) : VoiceEvent()
    data object Ended : VoiceEvent()
}

/**
 * Actividad transparente que solo existe mientras escuchas. Android no deja usar el micrófono
 * desde un servicio en segundo plano; con esta actividad visible sí. Entrega el texto por [Session.voice].
 */
class VoiceActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private var label: TextView? = null
    private var delivered = false
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setOnClickListener { cancelAndFinish() }

        val tv = TextView(this)
        tv.text = "Escuchando… (toca para cancelar)"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 17f
        tv.setPadding(dp(24), dp(14), dp(24), dp(14))
        val bg = GradientDrawable()
        bg.setColor(0xE6202124.toInt())
        bg.cornerRadius = dp(28).toFloat()
        tv.background = bg
        label = tv

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        lp.bottomMargin = dp(120)
        root.addView(tv, lp)
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            fail("Sin permiso de micrófono. Concédelo en la app › Ajustes.")
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("Este teléfono no tiene reconocimiento de voz disponible (necesita la app de Google).")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Session.voice.tryEmit(VoiceEvent.Listening)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) {
                    label?.text = t
                    Session.voice.tryEmit(VoiceEvent.Partial(t))
                }
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) {
                    fail("No te escuché. Inténtalo de nuevo.")
                } else {
                    delivered = true
                    Session.voice.tryEmit(VoiceEvent.Final(t))
                    finish()
                }
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No te escuché. Inténtalo de nuevo."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Falta el permiso de micrófono."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "El reconocimiento de voz necesita internet."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "El reconocimiento de voz está ocupado. Inténtalo de nuevo."
                    else -> "No pude reconocer la voz (código $error)."
                }
                fail(msg)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.voiceLocale().toLanguageTag())
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(intent)

        // Si nadie habla, no dejes el micrófono abierto.
        handler.postDelayed({ if (!delivered) fail("No te escuché. Inténtalo de nuevo.") }, 25_000L)
    }

    private fun fail(message: String) {
        if (delivered) return
        delivered = true
        Session.voice.tryEmit(VoiceEvent.Failed(message))
        finish()
    }

    private fun cancelAndFinish() {
        if (!delivered) {
            delivered = true
            Session.voice.tryEmit(VoiceEvent.Ended)
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // nada que hacer
        }
        recognizer = null
        if (!delivered) Session.voice.tryEmit(VoiceEvent.Ended)
        super.onDestroy()
    }

    private companion object {
        const val REQ_MIC = 77
    }
}

/** Lee las respuestas en voz alta si el usuario lo activó. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var queued: String? = null

    fun speak(text: String) {
        val clean = text.replace("*", "").replace("#", "").trim()
        if (clean.isEmpty()) return
        val current = tts
        if (current == null) {
            queued = clean
            tts = TextToSpeech(AsistenteApp.ctx) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setLanguage(Prefs.voiceLocale())
                    queued?.let { say(it) }
                    queued = null
                }
            }
        } else if (ready) {
            current.setLanguage(Prefs.voiceLocale())
            say(clean)
        } else {
            queued = clean
        }
    }

    private fun say(text: String) {
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "asistente")
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // nada que hacer
        }
    }
}
