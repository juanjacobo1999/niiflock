package com.asistente.movil

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, val kind: String, override val message: String) : Exception(message)

/** Cliente mínimo de la API de mensajes de Anthropic (sin librerías extra). */
object ClaudeApi {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    /** Campos opcionales que algún modelo rechazó: a partir de ahí se dejan de enviar. */
    private val dropped = mutableSetOf<String>()

    private val optionalFields = mapOf(
        "cache_control" to listOf("cache_control"),
        "output_config" to listOf("output_config", "effort"),
        "thinking" to listOf("thinking", "between_tools"),
    )

    private class Http(val code: Int, val body: String, val retryAfter: Long?)

    private suspend fun post(apiKey: String, json: String): Http = suspendCancellableCoroutine { cont ->
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        cont.invokeOnCancellation {
            try {
                conn.disconnect()
            } catch (e: Throwable) {
                // ya estaba cerrada
            }
        }
        thread(name = "claude-http") {
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 180_000
                conn.doOutput = true
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.setRequestProperty("content-type", "application/json")
                conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val retry = conn.getHeaderField("retry-after")?.trim()?.toLongOrNull()
                conn.disconnect()
                if (cont.isActive) cont.resume(Http(code, text, retry))
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    /** Envía [body] y devuelve la respuesta ya interpretada. Reintenta errores pasajeros. */
    suspend fun create(apiKey: String, body: JSONObject): JSONObject {
        var attempt = 0
        while (true) {
            for (f in dropped) body.remove(f)

            val res = try {
                post(apiKey, body.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (attempt < 3) {
                    attempt++
                    delay(1500L * attempt)
                    continue
                }
                throw ApiException(
                    0,
                    "network",
                    "No pude conectar con la API de Claude (${e.javaClass.simpleName}). Revisa tu internet.",
                )
            }

            if (res.code in 200..299) {
                return try {
                    JSONObject(res.body)
                } catch (e: Exception) {
                    throw ApiException(res.code, "parse", "Respuesta inesperada de la API.")
                }
            }

            val (kind, msg) = parseError(res.body)

            // Un campo opcional que este modelo no acepta: se quita y se reintenta.
            if (res.code == 400) {
                val culprit = optionalFields.entries.firstOrNull { (field, triggers) ->
                    field !in dropped && body.has(field) && triggers.any { msg.contains(it, ignoreCase = true) }
                }?.key
                if (culprit != null) {
                    dropped.add(culprit)
                    continue
                }
            }

            val transient = res.code == 429 || res.code == 408 || res.code >= 500
            if (transient && attempt < 3) {
                attempt++
                val wait = (res.retryAfter ?: (2L * attempt)).coerceIn(1L, 30L)
                delay(wait * 1000L)
                continue
            }
            throw ApiException(res.code, kind, friendly(res.code, msg))
        }
    }

    private fun parseError(body: String): Pair<String, String> = try {
        val e = JSONObject(body).optJSONObject("error")
        Pair(e?.optString("type") ?: "", e?.optString("message") ?: body.take(300))
    } catch (e: Exception) {
        Pair("", body.take(300))
    }

    private fun friendly(code: Int, msg: String): String = when (code) {
        401 -> "La API rechazó tu clave (401). Revisa que la API key esté bien copiada y siga activa en console.anthropic.com."
        403 -> "Tu clave no tiene permiso para esto (403): $msg"
        404 -> "No existe ese modelo o recurso (404): $msg"
        429 -> "Demasiadas peticiones o límite de uso (429). Espera un momento e inténtalo de nuevo."
        529 -> "Los servidores de Claude están saturados (529). Inténtalo en unos minutos."
        400 -> if (msg.contains("credit", ignoreCase = true) || msg.contains("balance", ignoreCase = true)) {
            "Tu cuenta de la API se quedó sin saldo. Recarga en console.anthropic.com › Plans & Billing. ($msg)"
        } else {
            "La API rechazó la petición (400): $msg"
        }
        else -> "Error de la API ($code): $msg"
    }

    /** Petición mínima para comprobar que la clave y el modelo funcionan. */
    suspend fun test(apiKey: String, model: String): String = try {
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", "Responde solo con la palabra OK."),
        )
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 64)
            .put("messages", messages)
        val r = create(apiKey, body)
        "Conexión correcta con ${r.optString("model", model)}."
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.message ?: "Error desconocido."
    }
}
