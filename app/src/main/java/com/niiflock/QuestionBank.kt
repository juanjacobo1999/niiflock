package com.niiflock

import android.content.Context
import org.json.JSONArray

data class Question(
    val id: String,
    val norma: String,
    val texto: String,
    val opciones: List<String>,
    val correcta: Int,
    val explicacion: String,
)

/** Banco de preguntas cargado desde assets/preguntas.json. */
object QuestionBank {
    private var cache: List<Question>? = null
    private val recent = ArrayDeque<String>()

    @Synchronized
    fun all(ctx: Context): List<Question> {
        cache?.let { return it }

        val text = ctx.applicationContext.assets.open("preguntas.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val arr = JSONArray(text)
        val list = ArrayList<Question>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("opciones")
            list.add(
                Question(
                    id = "q$i",
                    norma = o.getString("norma"),
                    texto = o.getString("pregunta"),
                    opciones = List(opts.length()) { idx -> opts.getString(idx) },
                    correcta = o.getInt("correcta"),
                    explicacion = o.optString("explicacion", ""),
                )
            )
        }
        cache = list
        return list
    }

    /** Normas en el mismo orden en que aparecen en el archivo. */
    fun norms(ctx: Context): List<String> = all(ctx).map { it.norma }.distinct()

    /** Elige una pregunta al azar, evitando repetir las últimas que salieron. */
    @Synchronized
    fun pick(ctx: Context): Question {
        val disabled = Prefs.disabledNorms(ctx)
        var pool = all(ctx).filter { it.norma !in disabled }
        if (pool.isEmpty()) pool = all(ctx)

        val fresh = pool.filter { it.id !in recent }
        val q = (if (fresh.isNotEmpty()) fresh else pool).random()

        recent.addLast(q.id)
        val keep = minOf(20, pool.size / 2)
        while (recent.size > keep) recent.removeFirst()
        return q
    }
}
