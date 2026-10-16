package com.mauricio.cinecasa.data

import android.util.Base64
import java.text.Normalizer
import java.util.Calendar

object TextUtil {
    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    // Etiquetas que los proveedores ponen al inicio: "ES - ", "LAT | ", "[4K] ", "4K: "
    private val tag = "(?:es|esp|lat|latino|mx|en|eng|us|usa|sub|subs|4k|hd|fhd|uhd|sd|vip|multi|dual|3d|hdr|ar|br|pt|fr|it|de|tr|uk|ca|vo|vose|cam|ts)"
    private val prefixStrict = Regex("^\\s*(?:\\[[^\\]]{1,10}]|\\(\\s*$tag\\s*\\)|$tag\\s*[|:\\-–])\\s*", RegexOption.IGNORE_CASE)
    private val suffixRe = Regex(
        "\\s*(?:[\\[(](?:[^\\])]{1,14})[\\])]|\\b(?:4k|uhd|fhd|hd|sd|hdr|1080p|720p|2160p|latino|castellano|subtitulad[ao]|sub|dual|multi|lat|esp)\\b|[-|]\\s*(?:19|20)\\d{2}|[-|]\\s*$)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val yearParen = Regex("[(\\[]\\s*((?:19|20)\\d{2})\\s*[)\\]]")
    private val yearLoose = Regex("(?:^|\\D)((?:19|20)\\d{2})(?:\\D|$)")

    fun norm(s: String): String {
        if (s.isEmpty()) return ""
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        return n.replace(nonAlnum, " ").trim()
    }

    fun tokens(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    /** Quita etiquetas de idioma/calidad y el año: "LAT | Duna (2021) 4K" -> "Duna" */
    fun cleanTitle(raw: String): String {
        var t = raw.trim()
        // Solo quitamos prefijos con separador explícito para no romper títulos reales
        repeat(2) { t = t.replace(prefixStrict, "") }
        var prev: String
        do {
            prev = t
            t = t.replace(suffixRe, "").trim()
        } while (t != prev && t.isNotEmpty())
        t = t.replace(yearParen, "").trim().trimEnd('-', '|', ':', ' ')
        return t.ifEmpty { raw.trim() }
    }

    fun yearOf(s: String): Int {
        if (s.isEmpty()) return 0
        yearParen.find(s)?.let { return it.groupValues[1].toInt() }
        val y = yearLoose.findAll(s).map { it.groupValues[1].toInt() }.lastOrNull() ?: return 0
        val now = Calendar.getInstance().get(Calendar.YEAR)
        return if (y in 1900..(now + 1)) y else 0
    }

    /** Distancia de edición acotada (se detiene si pasa de max). */
    fun lev(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /** Muchos paneles mandan la guía en base64. */
    fun maybeB64(s: String): String {
        if (s.isEmpty() || s.length % 4 != 0 || s.contains(' ')) return s
        return try {
            val d = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
            if (d.isNotEmpty() && d.none { it == '�' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }) d else s
        } catch (e: Exception) {
            s
        }
    }

    fun duration(secs: Int): String {
        if (secs <= 0) return ""
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return if (h > 0) "${h} h ${m} min" else "$m min"
    }

    fun clock(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    fun hhmm(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private val adultRe = Regex("(adult|xxx|\\+18|18\\+|porn|para adultos|adultos|hot|erotic|er[oó]tic)", RegexOption.IGNORE_CASE)
    fun isAdult(catName: String): Boolean = adultRe.containsMatchIn(catName)

    /** Extrae el id de YouTube de un valor que puede ser id o liga completa. */
    fun youtubeId(v: String): String {
        val s = v.trim()
        if (s.isEmpty()) return ""
        Regex("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{6,})").find(s)?.let { return it.groupValues[1] }
        return if (Regex("^[A-Za-z0-9_-]{6,20}$").matches(s)) s else ""
    }
}
