package com.mauricio.cinecasa.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray

/** Traduce al español las sinopsis que el proveedor manda en inglés u otro idioma (con caché en el aparato). */
object Translate {
    private lateinit var sp: SharedPreferences
    private val mem = HashMap<String, String>()

    fun init(ctx: Context) { sp = ctx.getSharedPreferences("traducciones", Context.MODE_PRIVATE) }

    private val es = setOf("el", "la", "los", "las", "de", "del", "que", "y", "en", "un", "una", "por", "con", "para", "su", "sus", "se", "es", "al", "lo", "como", "pero", "cuando", "tras")
    private val other = setOf("the", "and", "of", "to", "a", "in", "is", "his", "her", "with", "for", "on", "when", "their", "who", "after", "an", "from", "but", "le", "les", "des", "et", "und", "der", "die", "das", "il", "di", "che", "do", "da", "não", "um", "uma")

    /** ¿Parece que ya está en español? */
    fun isSpanish(t: String): Boolean {
        val w = t.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
        if (w.size < 4) return true
        val a = w.count { it in es }
        val b = w.count { it in other }
        return a >= b
    }

    fun cached(t: String): String? {
        if (t.isBlank() || isSpanish(t)) return t
        val k = t.hashCode().toString() + ":" + t.length
        mem[k]?.let { return it }
        if (::sp.isInitialized) sp.getString(k, null)?.let { mem[k] = it; return it }
        return null
    }

    suspend fun toSpanish(t: String): String = withContext(Dispatchers.IO) {
        cached(t)?.let { return@withContext it }
        val k = t.hashCode().toString() + ":" + t.length
        try {
            val u = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx").addQueryParameter("sl", "auto")
                .addQueryParameter("tl", "es").addQueryParameter("dt", "t")
                .addQueryParameter("q", t.take(4500)).build().toString()
            val parts = JSONArray(Net.get(u, Net.quick)).getJSONArray(0)
            val out = buildString { for (i in 0 until parts.length()) append(parts.optJSONArray(i)?.optString(0).orEmpty()) }.trim()
            if (out.isBlank()) return@withContext t
            mem[k] = out
            if (::sp.isInitialized) sp.edit().putString(k, out).apply()
            out
        } catch (_: Exception) { t }
    }
}
