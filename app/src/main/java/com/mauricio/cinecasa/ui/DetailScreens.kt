package com.mauricio.cinecasa.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Episode
import com.mauricio.cinecasa.data.Library
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SeriesInfo
import com.mauricio.cinecasa.data.Show
import com.mauricio.cinecasa.data.TextUtil
import com.mauricio.cinecasa.data.Tmdb
import com.mauricio.cinecasa.data.Vod
import com.mauricio.cinecasa.data.VodInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Abre el tráiler en la app de YouTube; si no hay, busca "título año tráiler" en YouTube. */
fun openTrailer(ctx: Context, youtubeId: String, title: String, year: Int, tmdb: String, tv: Boolean) {
    uiScope.launch {
        var id = youtubeId
        if (id.isBlank() && tmdb.isNotBlank()) id = withContext(Dispatchers.IO) { Tmdb.trailer(tmdb, tv) }
        val q = "$title ${if (year > 0) year else ""} tráiler español".replace("  ", " ")
        val intents = if (id.isNotBlank()) listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")),
        ) else listOf(
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra(SearchManager.QUERY, q),
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, q),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))),
        )
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return@launch
            } catch (_: Exception) {}
        }
        Toast.makeText(ctx, "No encontré la app de YouTube en esta tele", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun DetailFrame(backdrop: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Img(backdrop, Modifier.fillMaxSize())
        Scrim()
        content()
    }
}

@Composable
private fun CastRow(cast: String, director: String) {
    val nav = LocalNav.current
    val people = (cast.split(',', '/', '|') + director.split(',', '/'))
        .map { it.trim() }.filter { it.length > 2 }.distinct().take(16)
    if (people.isEmpty()) return
    Column {
        Text("Reparto y dirección · elige a alguien para ver más de su trabajo", color = C.Low, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(people) { p -> Pill(p, false, { nav.go(Route.Search(p)) }) }
        }
    }
}

@Composable
fun MovieDetailScreen(vod0: Vod) {
    val nav = LocalNav.current
    val c0 by Repo.catalog.collectAsState()
    val variants = remember(c0, vod0.id) { c0.movieVariants(vod0) }
    var vod by remember { mutableStateOf(vod0) }
    val ctx = LocalContext.current
    val c by Repo.catalog.collectAsState()
    val prog by Library.progress.collectAsState()
    val myList by Library.myList.collectAsState()
    var info by remember { mutableStateOf(Repo.cachedVodInfo(vod.id)) }
    val playFr = remember { FocusRequester() }
    val p = prog["movie:${vod.id}"]
    val inList = myList.any { it.kind == "movie" && it.id == vod.id }

    LaunchedEffect(vod.id) {
        info = Repo.cachedVodInfo(vod.id)
        if (info == null) info = Repo.vodInfo(vod.id)
    }
    LaunchedEffect(Unit) { delay(50); runCatching { playFr.requestFocus() } }

    val i: VodInfo? = info
    val related = remember(c, vod.id) {
        val mine = variants.map { it.id }.toSet()
        c.dedupeMovies(c.visMovies.filter { it.cat == vod.cat && it.id !in mine }.sortedByDescending { it.added }).take(24)
    }

    DetailFrame(i?.backdrop?.ifBlank { null } ?: vod.icon) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 44.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth(0.58f).padding(start = 48.dp)) {
                Text(vod.title, color = C.Hi, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                MetaLine(
                    listOf(yearStr(vod.year), TextUtil.duration(i?.durationSecs ?: 0), i?.genre.orEmpty(), i?.country.orEmpty()),
                    rating = i?.rating?.takeIf { it > 0 } ?: vod.rating,
                )
                if (!i?.director.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Dirección: ${i?.director}", color = C.Low, fontSize = 13.sp, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                when {
                    i == null -> Text("Cargando sinopsis…", color = C.Low, fontSize = 15.sp)
                    i.plot.isBlank() -> Text("El proveedor no incluyó sinopsis para esta película.", color = C.Low, fontSize = 15.sp)
                    else -> Text(i.plot, color = C.Mid, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                if (p != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressBar(p.fraction, Modifier.fillMaxWidth(0.4f), 4.dp)
                        Text("  Quedan ${TextUtil.duration(((p.dur - p.pos) / 1000).toInt())}", color = C.Mid, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    if (p != null) "Continuar" else "Reproducir",
                    { movieItem(vod)?.let { nav.play(listOf(it), 0, p?.pos ?: 0L) } },
                    Modifier.focusRequester(playFr), icon = Icons.Filled.PlayArrow, primary = true,
                )
                if (p != null) ActionButton("Desde el inicio", { movieItem(vod)?.let { nav.play(listOf(it), 0, 0L) } }, icon = Icons.Filled.Replay)
                ActionButton("Tráiler", { openTrailer(ctx, i?.trailer.orEmpty(), vod.title, vod.year, i?.tmdb?.ifBlank { null } ?: vod.tmdb, false) }, icon = Icons.Filled.OndemandVideo)
                ActionButton(
                    if (inList) "En mi lista" else "Mi lista",
                    { Library.toggleList("movie", vod.id, vod.title, vod.icon) },
                    icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                )
            }
            if (variants.size > 1) VersionRow(variants.map { versionLabel(it.lang, it.quality) }, variants.indexOf(vod)) { vod = variants[it] }
            if (i != null) CastRow(i.cast, i.director)
            if (related.isNotEmpty()) {
                Column {
                    SectionTitle("Más en ${c.vodCatName[vod.cat] ?: "esta categoría"}", Modifier.padding(start = 48.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(related, key = { it.id }) { v -> PosterCard(v.title, v.icon, { nav.go(Route.MovieDetail(v)) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowDetailScreen(show0: Show) {
    val nav = LocalNav.current
    val c0 by Repo.catalog.collectAsState()
    val variants = remember(c0, show0.id) { c0.showVariants(show0) }
    var show by remember { mutableStateOf(show0) }
    val ctx = LocalContext.current
    val prog by Library.progress.collectAsState()
    val watched by Library.watched.collectAsState()
    val myList by Library.myList.collectAsState()
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var failed by remember { mutableStateOf(false) }
    var season by remember { mutableIntStateOf(-1) }
    var focusedEp by remember { mutableStateOf<Episode?>(null) }
    val playFr = remember { FocusRequester() }
    val inList = myList.any { it.kind == "series" && it.id == show.id }

    LaunchedEffect(show.id) {
        info = null; failed = false; season = -1
        info = Repo.seriesInfo(show.id)
        failed = info == null
    }
    LaunchedEffect(Unit) { delay(50); runCatching { playFr.requestFocus() } }

    val inf = info
    // ¿Qué episodio toca? El que se dejó a medias, o el siguiente al último visto.
    val latest = prog.values.filter { it.kind == "episode" && it.seriesId == show.id }.maxByOrNull { it.updated }
    val all = inf?.allEpisodes.orEmpty()
    val target: Episode? = when {
        all.isEmpty() -> null
        latest != null -> all.firstOrNull { it.id == latest.id } ?: all.first()
        else -> {
            val lastWatched = all.indexOfLast { it.id in watched }
            if (lastWatched >= 0 && lastWatched < all.lastIndex) all[lastWatched + 1] else all.first()
        }
    }
    if (inf != null && season == -1) season = target?.season ?: inf.seasons.firstOrNull() ?: 1
    val anyWatched = all.any { it.id in watched } || latest != null

    DetailFrame((inf?.backdrop?.ifBlank { null }) ?: show.backdrop.ifBlank { show.cover }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(0.6f).padding(start = 48.dp)) {
                Text(show.title, color = C.Hi, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                val seasonsTxt = inf?.seasons?.size?.let { if (it == 1) "1 temporada" else "$it temporadas" }.orEmpty()
                MetaLine(listOf(yearStr(show.year), seasonsTxt, (inf?.genre ?: show.genre)), rating = inf?.rating?.takeIf { it > 0 } ?: show.rating)
                Spacer(Modifier.height(12.dp))
                val plot = inf?.plot?.ifBlank { null } ?: show.plot
                Text(
                    plot.ifBlank { if (inf == null && !failed) "Cargando…" else "Sin sinopsis del proveedor." },
                    color = C.Mid, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val label = when {
                    target == null -> if (failed) "Reintentar" else "Cargando…"
                    latest != null && latest.id == target.id -> "Continuar T${target.season}:E${target.num}"
                    anyWatched -> "Siguiente: T${target.season}:E${target.num}"
                    else -> "Ver T${target.season}:E${target.num}"
                }
                ActionButton(label, {
                    if (inf != null && target != null) playShow(show, inf, target.id, if (latest?.id == target.id) (latest?.pos ?: 0L) else 0L, nav)
                    else if (failed) uiScope.launch { info = Repo.seriesInfo(show.id); failed = info == null }
                }, Modifier.focusRequester(playFr), icon = Icons.Filled.PlayArrow, primary = true)
                ActionButton("Tráiler", { openTrailer(ctx, inf?.trailer?.ifBlank { null } ?: show.trailer, show.title, show.year, show.tmdb, true) }, icon = Icons.Filled.OndemandVideo)
                ActionButton(
                    if (inList) "En mi lista" else "Mi lista",
                    { Library.toggleList("series", show.id, show.title, show.cover) },
                    icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                )
            }
            if (variants.size > 1) VersionRow(variants.map { versionLabel(it.lang, it.quality) }, variants.indexOf(show)) { show = variants[it] }
            if (inf != null && inf.seasons.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(inf.seasons) { s ->
                        val eps = inf.episodes[s].orEmpty()
                        val done = eps.isNotEmpty() && eps.all { it.id in watched }
                        Pill("Temporada $s" + if (done) " ✓" else "", s == season, { season = s }, onFocused = { season = s })
                    }
                }
                val eps = inf.episodes[season].orEmpty()
                val rowState = rememberLazyListState()
                LaunchedEffect(season) {
                    val idx = eps.indexOfFirst { it.id == target?.id }
                    if (idx > 0) rowState.scrollToItem(idx)
                }
                LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(eps, key = { _, e -> e.id }) { _, e ->
                        val pe = prog["episode:${e.id}"]
                        WideCard(
                            title = "E${e.num} · ${episodeName(e, show.title)}",
                            subtitle = TextUtil.duration(e.durationSecs),
                            image = e.image.ifBlank { inf.backdrop.ifBlank { show.backdrop.ifBlank { show.cover } } },
                            onClick = { playShow(show, inf, e.id, pe?.pos ?: 0L, nav) },
                            progress = pe?.fraction,
                            watched = e.id in watched,
                            onFocused = { focusedEp = e },
                            onLongClick = { Library.markWatched(e.id, e.id !in watched) },
                        )
                    }
                }
                Column(Modifier.fillMaxWidth(0.7f).heightIn(min = 60.dp).padding(start = 48.dp)) {
                    val fe = focusedEp
                    if (fe != null && fe.season == season) {
                        Text("T${fe.season}:E${fe.num} · ${episodeName(fe, show.title)}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (fe.plot.isNotBlank()) Text(fe.plot, color = C.Mid, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    }
                    Text("Mantén presionado OK sobre un episodio para marcarlo como visto o no visto.", color = C.Low, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            } else if (inf == null && !failed) {
                Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 26.dp); Text("  Cargando episodios…", color = C.Mid, fontSize = 14.sp)
                }
            } else if (failed) {
                Text("No se pudieron cargar los episodios. Revisa tu conexión y presiona Reintentar.", color = C.Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 48.dp))
            }
            CastRow(inf?.cast?.ifBlank { null } ?: show.cast, inf?.director?.ifBlank { null } ?: show.director)
        }
    }
}

fun versionLabel(lang: com.mauricio.cinecasa.data.Lang?, quality: String): String {
    val l = lang?.label?.ifBlank { null } ?: "Sin etiqueta"
    return if (quality.isNotBlank()) "$l · $quality" else l
}

/** Selector de versión: Latino, Subtitulado, Inglés, 4K… del mismo título. */
@Composable
private fun VersionRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // Si dos versiones se llaman igual, se numeran
    val shown = labels.mapIndexed { i, l -> if (labels.count { it == l } > 1) "$l (${labels.take(i + 1).count { it == l }})" else l }
    Column {
        Text("Versiones disponibles · elige idioma o calidad", color = C.Low, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(shown) { i, l -> Pill(l, i == selected, { onSelect(i) }, icon = if (i == selected) Icons.Filled.Check else null) }
        }
    }
}
