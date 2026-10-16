package com.mauricio.cinecasa.ui

import com.mauricio.cinecasa.data.Catalog
import com.mauricio.cinecasa.data.Episode
import com.mauricio.cinecasa.data.PlayItem
import com.mauricio.cinecasa.data.Progress
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SeriesInfo
import com.mauricio.cinecasa.data.Show
import com.mauricio.cinecasa.data.Vod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private val sxe = Regex("(?i)^.*?\\bS\\d{1,2}\\s*E\\d{1,3}\\b\\s*[-:|]?\\s*")

/** "Breaking Bad - S01E03 - Título" -> "Título" */
fun episodeName(e: Episode, showTitle: String): String {
    var t = e.title.replace(sxe, "").trim()
    if (t.startsWith(showTitle, ignoreCase = true)) t = t.substring(showTitle.length).trim(' ', '-', ':', '|')
    return if (t.isBlank() || t.equals(showTitle, true)) "Episodio ${e.num}" else t
}

fun movieItem(v: Vod): PlayItem? {
    val x = Repo.xtream() ?: return null
    val info = Repo.cachedVodInfo(v.id)
    return PlayItem(
        key = "movie:${v.id}", kind = "movie", id = v.id, seriesId = 0, url = x.movieUrl(v.id, v.ext),
        title = v.title, subtitle = listOf(yearStr(v.year)).filter { it.isNotBlank() }.joinToString(" · "),
        image = info?.backdrop?.ifBlank { null } ?: v.icon,
    )
}

fun episodeItems(showId: Int, showTitle: String, fallbackImage: String, info: SeriesInfo): List<PlayItem> {
    val x = Repo.xtream() ?: return emptyList()
    return info.allEpisodes.map { e ->
        PlayItem(
            key = "episode:${e.id}", kind = "episode", id = e.id, seriesId = showId, url = x.episodeUrl(e.id, e.ext),
            title = showTitle, subtitle = "T${e.season}:E${e.num} · ${episodeName(e, showTitle)}",
            image = e.image.ifBlank { fallbackImage }, season = e.season, ep = e.num,
        )
    }
}

fun playShow(show: Show, info: SeriesInfo, episodeId: Int, pos: Long, nav: Nav) {
    val items = episodeItems(show.id, show.title, info.backdrop.ifBlank { show.backdrop.ifBlank { show.cover } }, info)
    val idx = items.indexOfFirst { it.id == episodeId }.coerceAtLeast(0)
    if (items.isNotEmpty()) nav.play(items, idx, pos)
}

/** Reanuda desde "Continuar viendo". */
fun resumeProgress(p: Progress, nav: Nav, c: Catalog) {
    if (p.kind == "movie") {
        val v = c.movieById[p.id] ?: return
        movieItem(v)?.let { nav.play(listOf(it), 0, p.pos) }
    } else {
        val show = c.showById[p.seriesId] ?: return
        uiScope.launch {
            val info = Repo.seriesInfo(show.id) ?: return@launch
            playShow(show, info, p.id, p.pos, nav)
        }
    }
}
