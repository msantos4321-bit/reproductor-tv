package com.mauricio.cinecasa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Catalog
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.Library
import com.mauricio.cinecasa.data.Progress
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.Saved
import com.mauricio.cinecasa.data.Show
import com.mauricio.cinecasa.data.TextUtil
import com.mauricio.cinecasa.data.Vod
import kotlinx.coroutines.delay
import java.util.Calendar

data class Hero(val key: String, val title: String, val meta: List<String>, val rating: Float, val plot: String, val image: String)

sealed interface Tile {
    val key: String
    data class M(val v: Vod) : Tile { override val key = "m${v.id}" }
    data class S(val s: Show) : Tile { override val key = "s${s.id}" }
    data class P(val p: Progress) : Tile { override val key = "p${p.key}" }
    data class L(val s: Saved) : Tile { override val key = "l${s.kind}${s.id}" }
    data class Ch(val ch: Channel, val list: List<Channel>) : Tile { override val key = "c${ch.id}" }
}

data class HomeRow(val id: String, val title: String, val tiles: List<Tile>, val wide: Boolean = false)

fun heroFor(t: Tile, c: Catalog): Hero = when (t) {
    is Tile.Ch -> Hero(t.key, t.ch.name, listOf("En vivo", c.liveCatName[t.ch.cat].orEmpty()), 0f, "Presiona OK para volver a este canal.", "")
    is Tile.M -> {
        val i = Repo.cachedVodInfo(t.v.id)
        Hero(t.key, t.v.title, listOf(yearStr(t.v.year), TextUtil.duration(i?.durationSecs ?: 0), i?.genre.orEmpty(), c.vodCatName[t.v.cat].orEmpty()),
            i?.rating?.takeIf { it > 0 } ?: t.v.rating, i?.plot.orEmpty(), i?.backdrop?.ifBlank { null } ?: t.v.icon)
    }
    is Tile.S -> Hero(t.key, t.s.title, listOf(yearStr(t.s.year), t.s.genre, c.seriesCatName[t.s.cat].orEmpty()), t.s.rating, t.s.plot,
        t.s.backdrop.ifBlank { t.s.cover })
    is Tile.P -> {
        val bg = if (t.p.kind == "movie") Repo.cachedVodInfo(t.p.id)?.backdrop.orEmpty() else c.showById[t.p.seriesId]?.backdrop.orEmpty()
        Hero(t.key, t.p.title, listOf(t.p.subtitle, "Quedan ${TextUtil.duration(((t.p.dur - t.p.pos) / 1000).toInt())}"), 0f, "", bg.ifBlank { t.p.image })
    }
    is Tile.L -> when (t.s.kind) {
        "movie" -> c.movieById[t.s.id]?.let { heroFor(Tile.M(it), c) }
        else -> c.showById[t.s.id]?.let { heroFor(Tile.S(it), c) }
    } ?: Hero(t.key, t.s.title, emptyList(), 0f, "", t.s.image)
}

fun yearStr(y: Int) = if (y > 0) y.toString() else ""

/** Abre lo que representa la tarjeta. */
fun openTile(t: Tile, nav: Nav, c: Catalog) {
    when (t) {
        is Tile.M -> nav.go(Route.MovieDetail(t.v))
        is Tile.S -> nav.go(Route.ShowDetail(t.s))
        is Tile.L -> if (t.s.kind == "movie") c.movieById[t.s.id]?.let { nav.go(Route.MovieDetail(it)) }
        else c.showById[t.s.id]?.let { nav.go(Route.ShowDetail(it)) }
        is Tile.P -> resumeProgress(t.p, nav, c)
        is Tile.Ch -> nav.playLive(t.list, t.list.indexOf(t.ch).coerceAtLeast(0))
    }
}

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    val progress by Library.progress.collectAsState()
    val myList by Library.myList.collectAsState()
    val mem = remember { nav.memory("home") }
    val colState = remember { nav.state.getOrPut("home_col") { LazyListState() } as LazyListState }
    val firstFr = remember { FocusRequester() }

    val lf = com.mauricio.cinecasa.data.LangFilter.current
    val rows = remember(c, progress, myList, lf) { buildHomeRows(c) }
    var hero by remember { mutableStateOf<Hero?>(null) }
    var focusedTile by remember { mutableStateOf<Tile?>(null) }

    // Al detenerse en una película, se pide su ficha para mostrar sinopsis y fondo
    LaunchedEffect(focusedTile) {
        val t = focusedTile ?: return@LaunchedEffect
        hero = heroFor(t, c)
        delay(450)
        val id = when (t) {
            is Tile.M -> t.v.id
            is Tile.P -> if (t.p.kind == "movie") t.p.id else 0
            is Tile.L -> if (t.s.kind == "movie") t.s.id else 0
            else -> 0
        }
        if (id > 0 && Repo.cachedVodInfo(id) == null) {
            Repo.vodInfo(id)
            if (focusedTile == t) hero = heroFor(t, c)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Fondo del destacado
        AnimatedContent(hero?.image, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bg",
            modifier = Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.62f).align(Alignment.TopEnd)) { img ->
            Box(Modifier.fillMaxSize()) {
                Img(img, Modifier.fillMaxSize())
                Scrim()
            }
        }
        Column(Modifier.fillMaxSize()) {
            // Texto del destacado
            Column(Modifier.fillMaxWidth(0.55f).height(196.dp).padding(start = 28.dp, top = 30.dp)) {
                val h = hero
                if (h != null) {
                    Text(h.title, color = C.Hi, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    MetaLine(h.meta, rating = h.rating)
                    Spacer(Modifier.height(10.dp))
                    Text(h.plot, color = C.Mid, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                }
            }
            LazyColumn(
                state = colState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                rows.forEachIndexed { ri, row ->
                    item(key = row.id) {
                        val rowState = remember { nav.state.getOrPut("home_row_${row.id}") { LazyListState() } as LazyListState }
                        Column {
                            SectionTitle(row.title, Modifier.padding(start = 28.dp))
                            LazyRow(
                                state = rowState,
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(row.tiles, key = { it.key }) { t ->
                                    val fk = "${row.id}/${t.key}"
                                    val m = Modifier
                                        .rememberFocus(mem, fk)
                                        .then(if (ri == 0 && t === row.tiles.first()) Modifier.focusRequester(firstFr) else Modifier)
                                    TileCard(t, row.wide, m, c, onFocused = { focusedTile = t },
                                        onLongClick = if (t is Tile.P) ({ Library.clearProgress(t.p.key) }) else null) { openTile(t, nav, c) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    RestoreFocus(mem, firstFr)
}

@Composable
fun TileCard(t: Tile, wide: Boolean, modifier: Modifier, c: Catalog, onFocused: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    when (t) {
        is Tile.Ch -> WideCard(t.ch.name, c.liveCatName[t.ch.cat].orEmpty(), t.ch.icon, onClick, modifier, width = 170.dp, fit = true, onFocused = onFocused)
        is Tile.P -> {
            val img = if (t.p.kind == "movie") Repo.cachedVodInfo(t.p.id)?.backdrop?.ifBlank { null } ?: t.p.image
            else c.showById[t.p.seriesId]?.backdrop?.ifBlank { null } ?: t.p.image
            WideCard(t.p.title, t.p.subtitle, img, onClick, modifier, progress = t.p.fraction, onFocused = onFocused, onLongClick = onLongClick)
        }
        is Tile.M -> PosterCard(t.v.title, t.v.icon, onClick, modifier, onFocused = onFocused,
            badge = c.movieLangs(t.v).ifBlank { null },
            progress = Library.progressOf("movie:${t.v.id}")?.fraction, onLongClick = onLongClick)
        is Tile.S -> PosterCard(t.s.title, t.s.cover, onClick, modifier, onFocused = onFocused, onLongClick = onLongClick,
            badge = c.showLangs(t.s).ifBlank { null })
        is Tile.L -> PosterCard(t.s.title, t.s.image, onClick, modifier, onFocused = onFocused)
    }
}

private fun buildHomeRows(c: Catalog): List<HomeRow> {
    val rows = ArrayList<HomeRow>()
    val cw = Library.continueWatching()
    if (cw.isNotEmpty()) rows += HomeRow("cw", "Continuar viendo", cw.map { Tile.P(it) }, wide = true)
    val recentCh = Library.recentChannels.value.mapNotNull { c.channelById[it] }.filter { c.visibleCat(it.cat) }.take(15)
    if (recentCh.isNotEmpty()) rows += HomeRow("rch", "Canales que viste", recentCh.map { Tile.Ch(it, recentCh) }, wide = true)
    val ml = Library.myList.value
    if (ml.isNotEmpty()) rows += HomeRow("ml", "Mi lista", ml.map { Tile.L(it) })

    val movies = c.visMovies
    val shows = c.visShows
    val year = Calendar.getInstance().get(Calendar.YEAR)
    fun List<Vod>.uniq() = c.dedupeMovies(this)
    fun List<Show>.uniqS() = c.dedupeShows(this)
    if (movies.isNotEmpty()) {
        rows += HomeRow("newm", "Películas agregadas recientemente", movies.sortedByDescending { it.added }.uniq().take(40).map { Tile.M(it) })
        val estrenos = movies.filter { it.year >= year - 1 }.sortedWith(compareByDescending<Vod> { it.year }.thenByDescending { it.added }).uniq().take(40)
        if (estrenos.size >= 6) rows += HomeRow("est", "Estrenos ${year - 1}–$year", estrenos.map { Tile.M(it) })
    }
    if (shows.isNotEmpty()) {
        rows += HomeRow("news", "Series con episodios nuevos", shows.sortedByDescending { it.lastMod }.uniqS().take(40).map { Tile.S(it) })
    }
    if (movies.isNotEmpty()) {
        val top = movies.filter { r -> val x = if (r.rating > 10) r.rating / 10 else r.rating; x >= 7.5f && x < 9.8f }
            .sortedByDescending { it.added }.uniq().take(40)
        if (top.size >= 6) rows += HomeRow("top", "Muy bien calificadas", top.map { Tile.M(it) })
    }
    val showsTop = shows.filter { val x = if (it.rating > 10) it.rating / 10 else it.rating; x >= 7.8f && x < 9.8f }.sortedByDescending { it.lastMod }.uniqS().take(40)
    if (showsTop.size >= 6) rows += HomeRow("tops", "Series muy bien calificadas", showsTop.map { Tile.S(it) })

    // Filas por categoría (las más grandes primero, alternando películas y series)
    val byCatM = movies.groupBy { it.cat }
    val byCatS = shows.groupBy { it.cat }
    val mc = c.visVodCats.filter { (byCatM[it.id]?.size ?: 0) >= 8 }.take(8)
    val sc = c.visSeriesCats.filter { (byCatS[it.id]?.size ?: 0) >= 8 }.take(5)
    val n = maxOf(mc.size, sc.size)
    for (i in 0 until n) {
        mc.getOrNull(i)?.let { cat ->
            rows += HomeRow("cm${cat.id}", cat.name, byCatM[cat.id].orEmpty().sortedByDescending { it.added }.uniq().take(30).map { Tile.M(it) })
        }
        sc.getOrNull(i)?.let { cat ->
            rows += HomeRow("cs${cat.id}", "Series · ${cat.name}", byCatS[cat.id].orEmpty().sortedByDescending { it.lastMod }.uniqS().take(30).map { Tile.S(it) })
        }
    }
    return rows.filter { it.tiles.isNotEmpty() }
}

