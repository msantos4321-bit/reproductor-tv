package com.mauricio.cinecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.LangFilter
import com.mauricio.cinecasa.data.Library
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import com.mauricio.cinecasa.data.Repo
import kotlinx.coroutines.delay

private const val ALL = "__all"

@Composable
fun BrowseScreen(isSeries: Boolean, initialCat: String?) {
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    val prog by Library.progress.collectAsState()
    val kind = if (isSeries) "series" else "movies"
    val mem = remember { nav.memory("browse_$kind") }
    var cat by remember { mutableStateOf(initialCat ?: (nav.state["cat_$kind"] as? String) ?: ALL) }
    var pendingCat by remember { mutableStateOf(cat) }
    var sort by remember { mutableStateOf((nav.state["sort_$kind"] as? String) ?: "new") }
    val gridState = remember(cat, sort) {
        nav.state.getOrPut("grid_${kind}_${cat}_$sort") { LazyGridState() } as LazyGridState
    }
    val catState = remember { nav.state.getOrPut("cats_$kind") { LazyListState() } as LazyListState }
    val firstFr = remember { FocusRequester() }

    LaunchedEffect(pendingCat) {
        if (pendingCat != cat) {
            delay(280)
            cat = pendingCat
            nav.state["cat_$kind"] = cat
        }
    }

    val cats = if (isSeries) c.visSeriesCats else c.visVodCats
    val counts = remember(c, isSeries) {
        if (isSeries) c.visShows.groupingBy { it.cat }.eachCount() else c.visMovies.groupingBy { it.cat }.eachCount()
    }
    val lf = LangFilter.current
    val tiles: List<Tile> = remember(c, cat, sort, isSeries, lf) {
        if (isSeries) {
            val l = if (cat == ALL) c.visShows else c.visShows.filter { it.cat == cat }
            when (sort) {
                "az" -> l.sortedBy { it.title.lowercase() }
                "rating" -> l.sortedByDescending { if (it.rating > 10) it.rating / 10 else it.rating }
                "year" -> l.sortedWith(compareByDescending<com.mauricio.cinecasa.data.Show> { it.year }.thenByDescending { it.lastMod })
                else -> l.sortedByDescending { it.lastMod }
            }.let { c.dedupeShows(it) }.map { Tile.S(it) }
        } else {
            val l = if (cat == ALL) c.visMovies else c.visMovies.filter { it.cat == cat }
            when (sort) {
                "az" -> l.sortedBy { it.title.lowercase() }
                "rating" -> l.sortedByDescending { if (it.rating > 10) it.rating / 10 else it.rating }
                "year" -> l.sortedWith(compareByDescending<com.mauricio.cinecasa.data.Vod> { it.year }.thenByDescending { it.added })
                else -> l.sortedByDescending { it.added }
            }.let { c.dedupeMovies(it) }.map { Tile.M(it) }
        }
    }
    val catName = if (cat == ALL) (if (isSeries) "Todas las series" else "Todas las películas")
    else (if (isSeries) c.seriesCatName[cat] else c.vodCatName[cat]).orEmpty()

    Row(Modifier.fillMaxSize()) {
        // Categorías
        LazyColumn(
            state = catState,
            modifier = Modifier.width(210.dp).fillMaxHeight().background(C.Panel.copy(alpha = 0.75f)),
            contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(if (isSeries) "Series" else "Películas", color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
            }
            item(key = ALL) {
                CatItem(if (isSeries) "Todas" else "Todas", if (isSeries) c.visShows.size else c.visMovies.size, cat == ALL,
                    Modifier.rememberFocus(mem, "cat:$ALL")) { pendingCat = ALL; cat = ALL; runCatching { firstFr.requestFocus() } }
            }
            items(cats, key = { it.id }) { k ->
                val n = counts[k.id] ?: 0
                if (n > 0) CatItem(k.name, n, cat == k.id, Modifier.rememberFocus(mem, "cat:${k.id}").onFocusChanged { if (it.isFocused) pendingCat = k.id }) {
                    pendingCat = k.id; cat = k.id; runCatching { firstFr.requestFocus() }
                }
            }
        }
        // Rejilla
        Column(Modifier.fillMaxSize().padding(top = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(catName, color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${"%,d".format(tiles.size)} títulos · Idioma: ${lf.label.lowercase()} (cámbialo con el botón de la derecha)", color = C.Low, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(lf.label, lf.id != "all", { LangFilter.next() }, icon = Icons.Filled.Translate)
                    for ((k, label) in listOf("new" to "Recientes", "year" to "Año", "rating" to "Calificación", "az" to "A–Z")) {
                        Pill(label, sort == k, { sort = k; nav.state["sort_$kind"] = k })
                    }
                }
            }
            Spacer(Modifier.padding(top = 4.dp))
            if (tiles.isEmpty()) {
                CenterMessage("Sin títulos en esta categoría")
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(tiles, key = { it.key }) { t ->
                        val m = Modifier.rememberFocus(mem, "g:${t.key}").then(if (t === tiles.first()) Modifier.focusRequester(firstFr) else Modifier)
                        when (t) {
                            is Tile.M -> PosterCard(t.v.title, t.v.icon, { nav.go(Route.MovieDetail(t.v)) }, m, width = 104.dp,
                                progress = prog["movie:${t.v.id}"]?.fraction, badge = if (sort == "year" && t.v.year > 0) t.v.year.toString() else c.movieLangs(t.v).ifBlank { null })
                            is Tile.S -> PosterCard(t.s.title, t.s.cover, { nav.go(Route.ShowDetail(t.s)) }, m, width = 104.dp,
                                badge = if (sort == "year" && t.s.year > 0) t.s.year.toString() else c.showLangs(t.s).ifBlank { null })
                            else -> {}
                        }
                    }
                }
            }
        }
    }
    RestoreFocus(mem, firstFr)
}

@Composable
private fun CatItem(name: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.AccentSoft else Color.Transparent,
            contentColor = if (selected) C.Accent else C.Mid,
            focusedContainerColor = C.Accent,
            focusedContentColor = C.OnAccent,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), fontSize = 11.sp, color = C.Low)
        }
    }
}

@Composable
fun MyListScreen() {
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    val list by Library.myList.collectAsState()
    val progress by Library.progress.collectAsState()
    val mem = remember { nav.memory("mylist") }
    val firstFr = remember { FocusRequester() }
    val cw = remember(progress) { Library.continueWatching() }

    if (list.isEmpty() && cw.isEmpty()) {
        CenterMessage("Tu lista está vacía", "Entra a una película o serie y elige «Mi lista» para guardarla aquí.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Mi lista", color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp)) }
        if (cw.isNotEmpty()) item {
            Column {
                SectionTitle("Continuar viendo", Modifier.padding(start = 28.dp))
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(cw, key = { it.key }) { p ->
                        val t = Tile.P(p)
                        TileCard(t, true, Modifier.rememberFocus(mem, t.key).then(if (p === cw.first()) Modifier.focusRequester(firstFr) else Modifier), c,
                            onLongClick = { Library.clearProgress(p.key) }, onClick = { openTile(t, nav, c) })
                    }
                }
                Text("Mantén OK sobre una tarjeta para quitarla de «Continuar viendo».", color = C.Low, fontSize = 11.sp, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
            }
        }
        if (list.isNotEmpty()) item {
            Column {
                SectionTitle("Guardadas (${list.size})", Modifier.padding(start = 28.dp))
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.kind + it.id }) { s ->
                        val t = Tile.L(s)
                        PosterCard(s.title, s.image, { openTile(t, nav, c) },
                            Modifier.rememberFocus(mem, t.key).then(if (cw.isEmpty() && s === list.first()) Modifier.focusRequester(firstFr) else Modifier),
                            badge = if (s.kind == "series") "SERIE" else null,
                            onLongClick = { Library.toggleList(s.kind, s.id, s.title, s.image) })
                    }
                }
                Text("Mantén OK sobre un título para quitarlo de tu lista.", color = C.Low, fontSize = 11.sp, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
            }
        }
    }
    RestoreFocus(mem, firstFr)
}
