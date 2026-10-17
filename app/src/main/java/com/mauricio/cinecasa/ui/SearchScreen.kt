package com.mauricio.cinecasa.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon

import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SearchResults
import com.mauricio.cinecasa.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(initial: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val c by Repo.catalog.collectAsState()
    var query by remember { mutableStateOf(initial.ifBlank { (nav.state["search_q"] as? String).orEmpty() }) }
    var results by remember { mutableStateOf(SearchResults()) }
    var searchingPerson by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(Prefs.recentSearches) }
    val fieldFr = remember { FocusRequester() }
    val mem = remember { nav.memory("search") }

    val canVoice = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                query = it
                Prefs.addRecentSearch(it); recents = Prefs.recentSearches
            }
        }
    }
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Di una película, serie, actor o canal")
        runCatching { voice.launch(i) }
    }

    // Resultados mientras se escribe
    val lf = com.mauricio.cinecasa.data.LangFilter.current
    LaunchedEffect(query, c, lf) {
        nav.state["search_q"] = query
        if (query.trim().length < 2) { results = SearchResults(); return@LaunchedEffect }
        delay(220)
        val local = withContext(Dispatchers.Default) { Repo.searchLocal(query) }
        results = local
        if (Tmdb.enabled && query.trim().length >= 4) {
            delay(500)
            searchingPerson = true
            val p = withContext(Dispatchers.IO) { Repo.searchPerson(query) }
            searchingPerson = false
            if (p != null && (p.second.isNotEmpty() || p.third.isNotEmpty())) {
                results = local.copy(person = p.first, personMovies = p.second, personShows = p.third)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 26.dp, bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Wrap(Modifier.padding(horizontal = if (LocalCompact.current) 16.dp else 28.dp), 12.dp) {
                TvTextField(
                    query, { query = it }, "Título, actor, director, canal o año…",
                    Modifier.paneWidth(520.dp), imeAction = ImeAction.Search, leading = Icons.Filled.Search, boxRequester = fieldFr,
                    onImeAction = { Prefs.addRecentSearch(query); recents = Prefs.recentSearches },
                )
                if (canVoice) RoundIcon(Icons.Filled.Mic, "Buscar por voz", primary = true) { listen() }
                if (query.isNotEmpty()) RoundIcon(Icons.Filled.Clear, "Borrar") { query = "" }
                Pill(lf.label, lf.id != "all", { com.mauricio.cinecasa.data.LangFilter.next() }, icon = Icons.Filled.Translate)
            }
        }
        val q = query.trim()
        if (q.length < 2) {
            if (recents.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 28.dp)) {
                    SectionTitle("Búsquedas recientes")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recents.forEach { r -> Pill(r, false, { query = r }, icon = Icons.Filled.History) }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 28.dp)) {
                    SectionTitle("Explorar películas por categoría")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        c.visVodCats.take(40).forEach { k -> Pill(k.name, false, { nav.go(Route.Movies(k.id)) }) }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 28.dp)) {
                    SectionTitle("Explorar series por categoría")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        c.visSeriesCats.take(30).forEach { k -> Pill(k.name, false, { nav.go(Route.Series(k.id)) }) }
                    }
                }
            }
            item {
                Text(
                    "Consejos: puedes escribir solo una parte del nombre («piratas carib»), sin acentos, con errores pequeños, " +
                        "o agregar el año («batman 2022»). " + if (Tmdb.enabled) "La búsqueda por actor usa TMDB." else
                        "Para buscar por actor en todas las películas, agrega una clave gratuita de TMDB en Ajustes.",
                    color = C.Low, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 28.dp).fillMaxWidth(0.8f),
                )
            }
        } else {
            val r = results
            if (r.empty && !searchingPerson) item {
                Text("Sin resultados para «$q». Prueba con menos palabras o revisa la ortografía.", color = C.Mid, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 28.dp))
            }
            if (r.person != null) item {
                Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.person.photo.isNotBlank()) Img(r.person.photo, Modifier.size(44.dp).roundedClip(22.dp))
                    Text("  Con ${r.person.name} en tu catálogo", color = C.Hi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (r.personMovies.isNotEmpty()) item { ResultRow("Películas de ${r.person?.name}", r.personMovies.map { Tile.M(it) }, mem, "pm") }
            if (r.personShows.isNotEmpty()) item { ResultRow("Series de ${r.person?.name}", r.personShows.map { Tile.S(it) }, mem, "ps") }
            if (r.movies.isNotEmpty()) item { ResultRow("Películas (${r.movies.size})", r.movies.map { Tile.M(it) }, mem, "m") }
            if (r.shows.isNotEmpty()) item { ResultRow("Series (${r.shows.size})", r.shows.map { Tile.S(it) }, mem, "s") }
            val pm = r.peopleMovies.filter { v -> r.personMovies.none { it.id == v.id } }
            val ps = r.peopleShows.filter { s -> r.personShows.none { it.id == s.id } }
            if (pm.isNotEmpty() || ps.isNotEmpty()) item {
                ResultRow("Reparto o dirección: «$q»", pm.map { Tile.M(it) } + ps.map { Tile.S(it) }, mem, "pp")
            }
            if (r.channels.isNotEmpty()) item {
                Column {
                    SectionTitle("Canales (${r.channels.size})", Modifier.padding(start = 28.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(r.channels, key = { it.id }) { ch ->
                            WideCard(ch.name, c.liveCatName[ch.cat].orEmpty(), ch.icon, { nav.playLive(r.channels, r.channels.indexOf(ch)) },
                                Modifier.rememberFocus(mem, "ch${ch.id}"), width = 170.dp)
                        }
                    }
                }
            }
            if (searchingPerson) item {
                Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 22.dp); Text("  Buscando por actor o director…", color = C.Low, fontSize = 13.sp)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (mem.key == null) { delay(80); runCatching { fieldFr.requestFocus() } }
    }
    RestoreFocus(mem)
}

@Composable
private fun ResultRow(title: String, tiles: List<Tile>, mem: FocusMemory, prefix: String) {
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    Column {
        SectionTitle(title, Modifier.padding(start = 28.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tiles, key = { it.key }) { t ->
                TileCard(t, false, Modifier.rememberFocus(mem, "$prefix/${t.key}"), c) {
                    nav.state["search_q"] = (nav.state["search_q"] as? String).orEmpty()
                    Prefs.addRecentSearch((nav.state["search_q"] as? String).orEmpty())
                    openTile(t, nav, c)
                }
            }
        }
    }
}

@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) C.Strong else C.Panel,
            contentColor = if (primary) C.OnStrong else C.Hi,
            focusedContainerColor = C.Accent,
            focusedContentColor = C.OnAccent,
        ),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, desc, Modifier.size(24.dp)) }
    }
}
