package com.asistente.movil

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

enum class Role { USER, ASSISTANT, STATUS, ERROR }

data class ChatMsg(val id: Long, val role: Role, val text: String)

/** Una acción sensible esperando que el usuario diga sí o no. */
class Pending(val text: String, val deferred: CompletableDeferred<Boolean>)

data class UsageTotals(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val cost: Double = 0.0,
)

/**
 * Estado compartido entre el botón flotante y la app: el chat, si hay una orden en marcha,
 * las confirmaciones pendientes y el gasto. Todo vive en memoria; no se guarda nada del chat en disco.
 */
object Session {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val running = MutableStateFlow(false)
    val listening = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val pending = MutableStateFlow<Pending?>(null)
    val usage = MutableStateFlow(UsageTotals())
    val voice = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)

    private val ids = AtomicLong(1)

    @Volatile
    private var job: Job? = null
    private var started = false

    /** Se llama una vez al arrancar la app: escucha los resultados del reconocimiento de voz. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            voice.collect { ev ->
                when (ev) {
                    is VoiceEvent.Listening -> {
                        listening.value = true
                        status.value = "Escuchando…"
                    }
                    is VoiceEvent.Partial -> status.value = ev.text
                    is VoiceEvent.Final -> {
                        listening.value = false
                        status.value = ""
                        submit(ev.text)
                    }
                    is VoiceEvent.Failed -> {
                        listening.value = false
                        status.value = ""
                        add(Role.ERROR, ev.message)
                    }
                    is VoiceEvent.Ended -> {
                        listening.value = false
                        if (!running.value) status.value = ""
                    }
                }
            }
        }
    }

    fun add(role: Role, text: String) {
        if (text.isBlank()) return
        messages.update { (it + ChatMsg(ids.getAndIncrement(), role, text)).takeLast(300) }
    }

    /** Lanza una orden del usuario (escrita o dictada). */
    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (running.value) {
            add(Role.ERROR, "Espera a que termine la orden actual o detenla con el botón rojo.")
            return
        }
        if (!Prefs.consent) {
            add(Role.ERROR, "Abre la app y acepta el aviso de privacidad antes de usarla.")
            return
        }
        if (Prefs.apiKey.isNullOrBlank()) {
            add(Role.ERROR, "Falta tu API key. Abre la app › Ajustes y pégala ahí.")
            return
        }
        add(Role.USER, t)
        running.value = true
        status.value = "Pensando…"
        job = scope.launch {
            try {
                Agent.run(t)
            } catch (e: CancellationException) {
                add(Role.STATUS, "Detenido.")
            } catch (e: ApiException) {
                add(Role.ERROR, e.message)
            } catch (e: Throwable) {
                add(Role.ERROR, "Error inesperado: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                running.value = false
                status.value = ""
                pending.value?.deferred?.complete(false)
                pending.value = null
            }
        }
    }

    /** Detiene la orden en marcha (botón rojo). */
    fun cancel() {
        pending.value?.deferred?.complete(false)
        job?.cancel()
        Speaker.stop()
    }

    fun newConversation() {
        cancel()
        Agent.reset()
        messages.value = emptyList()
        usage.value = UsageTotals()
    }

    /** Pide permiso al usuario para una acción sensible. Sin respuesta en 2 minutos cuenta como «no». */
    suspend fun confirm(text: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        pending.value = Pending(text, d)
        try {
            return withTimeoutOrNull(120_000L) { d.await() } ?: false
        } finally {
            pending.value = null
        }
    }

    fun answer(ok: Boolean) {
        pending.value?.deferred?.complete(ok)
    }
}
