package com.mauricio.cinecasa.data

data class Category(val id: String, val name: String)

class Vod(
    val id: Int,
    val name: String,
    val icon: String,
    val rating: Float,
    val added: Long,
    val cat: String,
    val ext: String,
    val tmdb: String,
    yearField: Int,
) {
    val title: String = TextUtil.cleanTitle(name)
    val year: Int = if (yearField > 1900) yearField else TextUtil.yearOf(name)
    val norm: String = TextUtil.norm(title)
}

class Show(
    val id: Int,
    val name: String,
    val cover: String,
    val backdrop: String,
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val release: String,
    val rating: Float,
    val lastMod: Long,
    val cat: String,
    val trailer: String,
    val tmdb: String,
) {
    val title: String = TextUtil.cleanTitle(name)
    val year: Int = TextUtil.yearOf(release).takeIf { it > 0 } ?: TextUtil.yearOf(name)
    val norm: String = TextUtil.norm(title)
    val normPeople: String = TextUtil.norm("$cast $director")
}

class Channel(
    val id: Int,
    val num: Int,
    val name: String,
    val icon: String,
    val epgId: String,
    val cat: String,
    val archiveDays: Int,
) {
    val norm: String = TextUtil.norm(name)
}

/** Detalle de una película (get_vod_info). */
data class VodInfo(
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val release: String = "",
    val durationSecs: Int = 0,
    val rating: Float = 0f,
    val backdrop: String = "",
    val poster: String = "",
    val trailer: String = "",
    val tmdb: String = "",
    val country: String = "",
) {
    val normPeople: String by lazy { TextUtil.norm("$cast $director") }
}

data class Episode(
    val id: Int,
    val season: Int,
    val num: Int,
    val title: String,
    val ext: String,
    val plot: String,
    val image: String,
    val durationSecs: Int,
)

data class SeriesInfo(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val release: String,
    val backdrop: String,
    val cover: String,
    val trailer: String,
    val rating: Float,
    val seasons: List<Int>,
    val episodes: Map<Int, List<Episode>>,
) {
    /** Todos los episodios en orden (temporada, número). */
    val allEpisodes: List<Episode> by lazy { seasons.flatMap { episodes[it].orEmpty() } }
}

data class EpgItem(val title: String, val desc: String, val start: Long, val end: Long) {
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

data class AccountInfo(
    val ok: Boolean,
    val status: String,
    val expires: Long?,
    val maxCons: Int,
    val activeCons: Int,
    val formats: List<String>,
    val trial: Boolean,
)

/** Algo que se puede reproducir (película o episodio). */
data class PlayItem(
    val key: String,
    val kind: String, // "movie" | "episode"
    val id: Int,
    val seriesId: Int,
    val url: String,
    val title: String,
    val subtitle: String,
    val image: String,
    val season: Int = 0,
    val ep: Int = 0,
)
