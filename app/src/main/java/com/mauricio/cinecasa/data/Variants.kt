package com.mauricio.cinecasa.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Idioma de una versión, deducido de las etiquetas del título y de la categoría. */
enum class Lang(val label: String, val short: String) {
    LAT("Latino", "LAT"),
    ESP("Español", "ESP"),
    CAST("Castellano", "CAST"),
    DUAL("Dual", "DUAL"),
    SUB("Subtitulado", "SUB"),
    EN("Inglés", "ING"),
    NONE("", ""),
}

object LangDetect {
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val dual = r("\\b(dual|multi(?:audio)?|dual audio)\\b")
    private val sub = r("\\b(sub|subs|subt|subtitulad[ao]s?|vose|v\\.?o\\.?s\\.?e?|subbed)\\b|subtitulad")
    private val lat = r("\\b(lat|latino|latinoamerica(?:no)?|lat-am|latam|mx|mexico|méxico|audio latino)\\b")
    private val cast = r("\\b(cast|castellano|españa|espana|es-es|spain)\\b")
    private val esp = r("\\b(esp|español|espanol|spanish|es)\\b")
    private val en = r("\\b(eng|english|ingl[eé]s|ing|en|usa|us|uk|vo|original)\\b")
    private val q4k = r("\\b(4k|uhd|2160p?)\\b")
    private val qfhd = r("\\b(fhd|1080p?|full ?hd)\\b")
    private val qhd = r("\\b(hd|720p?)\\b")
    private val qcam = r("\\b(cam|ts|hdcam|telesync)\\b")

    private fun fromText(t: String): Lang? = when {
        dual.containsMatchIn(t) -> Lang.DUAL
        sub.containsMatchIn(t) -> Lang.SUB
        lat.containsMatchIn(t) -> Lang.LAT
        cast.containsMatchIn(t) -> Lang.CAST
        esp.containsMatchIn(t) -> Lang.ESP
        en.containsMatchIn(t) -> Lang.EN
        else -> null
    }

    /** Primero manda la etiqueta del título; si no trae, la de la categoría. */
    fun lang(name: String, catName: String): Lang {
        // Solo revisamos las etiquetas, no las palabras del título (una película puede llamarse "Ingles para todos")
        val tags = tagText(name)
        return fromText(tags) ?: fromText(catName) ?: Lang.NONE
    }

    fun quality(name: String, catName: String): String {
        val t = tagText(name) + " " + catName
        return when {
            q4k.containsMatchIn(t) -> "4K"
            qfhd.containsMatchIn(t) -> "FHD"
            qcam.containsMatchIn(t) -> "CAM"
            qhd.containsMatchIn(t) -> "HD"
            else -> ""
        }
    }

    private val bracket = Regex("[\\[(]([^\\])]{1,24})[\\])]")
    private val prefix = Regex("^\\s*([A-Za-z0-9+ ]{2,14})\\s*[|:\\-–]\\s+")
    private val suffix = Regex("[-|]\\s*([A-Za-zÁÉÍÓÚáéíóúñÑ0-9 ]{2,20})\\s*$")
    private val trailingWords = Regex("\\b(latino|castellano|subtitulad[ao]|dual|4k|uhd|fhd|hd|1080p|720p|eng|esp|lat|sub|cam)\\s*$", RegexOption.IGNORE_CASE)

    /** Junta solo las partes del nombre que parecen etiquetas: [LAT], (SUB), "ES | ", "- Latino", "... 4K". */
    private fun tagText(name: String): String {
        val sb = StringBuilder()
        bracket.findAll(name).forEach { sb.append(it.groupValues[1]).append(' ') }
        prefix.find(name)?.let { sb.append(it.groupValues[1]).append(' ') }
        suffix.find(name)?.let { sb.append(it.groupValues[1]).append(' ') }
        trailingWords.find(name)?.let { sb.append(it.groupValues[1]).append(' ') }
        return sb.toString()
    }
}

/** Preferencia de idioma: qué versiones se muestran y cuál va primero. */
object LangFilter {
    data class Preset(val id: String, val label: String, val allowed: Set<Lang>, val order: List<Lang>)

    val presets = listOf(
        Preset("all", "Todos los idiomas", Lang.values().toSet(), listOf(Lang.LAT, Lang.ESP, Lang.DUAL, Lang.CAST, Lang.NONE, Lang.SUB, Lang.EN)),
        Preset("es", "Solo español", setOf(Lang.LAT, Lang.ESP, Lang.CAST, Lang.DUAL, Lang.NONE), listOf(Lang.LAT, Lang.ESP, Lang.DUAL, Lang.CAST, Lang.NONE)),
        Preset("es_sub", "Español y subtitulado", setOf(Lang.LAT, Lang.ESP, Lang.CAST, Lang.DUAL, Lang.SUB, Lang.NONE), listOf(Lang.LAT, Lang.ESP, Lang.DUAL, Lang.CAST, Lang.NONE, Lang.SUB)),
        Preset("en", "Inglés y subtitulado", setOf(Lang.EN, Lang.SUB, Lang.DUAL, Lang.NONE), listOf(Lang.EN, Lang.DUAL, Lang.SUB, Lang.NONE)),
    )

    var current by mutableStateOf(presets.first())
        private set

    fun load() { current = presets.firstOrNull { it.id == Prefs.langPreset } ?: presets.first() }

    fun set(id: String) {
        current = presets.firstOrNull { it.id == id } ?: presets.first()
        Prefs.langPreset = current.id
    }

    fun next() = set(presets[(presets.indexOf(current) + 1) % presets.size].id)

    fun allows(l: Lang) = l in current.allowed
    fun rank(l: Lang): Int = current.order.indexOf(l).let { if (it < 0) 99 else it }

    private val qualityRank = mapOf("4K" to 0, "FHD" to 1, "HD" to 2, "" to 3, "CAM" to 9)
    fun rankQuality(q: String) = qualityRank[q] ?: 5
}
