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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.GridView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Catalog
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.EpgItem
import com.mauricio.cinecasa.data.Library
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.TextUtil
import kotlinx.coroutines.delay

const val FAVS = "__fav"
const val RECENT = "__recent"
const val ALLCH = "__all"

fun channelsFor(cat: String, c: Catalog, favs: List<Int>, recent: List<Int>): List<Channel> = when (cat) {
    FAVS -> favs.mapNotNull { c.channelById[it] }.filter { c.visibleCat(it.cat) }
    RECENT -> recent.mapNotNull { c.channelById[it] }.filter { c.visibleCat(it.cat) }
    ALLCH -> c.visChannels
    else -> c.visChannels.filter { it.cat == cat }
}

/** Guía corta (ahora / después) que se pide solo cuando el canal se queda en pantalla. */
@Composable
fun rememberEpg(ch: Channel, delayMs: Long = 350): List<EpgItem> {
    val st by produceState(initialValue = emptyList<EpgItem>(), ch.id) {
        delay(delayMs)
        value = Repo.epg(ch.id)
        while (true) { // refresca cuando termina el programa
            val end = value.firstOrNull()?.end ?: (System.currentTimeMillis() + 10 * 60_000)
            delay((end - System.currentTimeMillis()).coerceIn(30_000, 15 * 60_000))
            value = Repo.epg(ch.id)
        }
    }
    return st
}

@Composable
fun LiveScreen() {
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    val favs by Library.favChannels.collectAsState()
    val recent by Library.recentChannels.collectAsState()
    val mem = remember { nav.memory("live") }
    var cat by remember { mutableStateOf((nav.state["live_cat"] as? String) ?: if (favs.isNotEmpty()) FAVS else ALLCH) }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    val chState = remember(cat) { nav.state.getOrPut("live_list_$cat") { LazyListState() } as LazyListState }
    val firstFr = remember { FocusRequester() }
    val ctx = LocalContext.current

    // Vista previa: al detenerte en un canal, se reproduce en chico a la derecha
    val preview = remember { buildPlayer(ctx, live = true) }
    var previewCh by remember { mutableStateOf<Channel?>(null) }
    var previewReady by remember { mutableStateOf(false) }
    var previewFail by remember { mutableStateOf(false) }
    var previewTry by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) previewReady = true }
            override fun onPlayerError(error: PlaybackException) {
                val ch = previewCh ?: return
                if (previewTry < 1) { previewTry++; liveMediaItem(ch, previewTry)?.let { preview.setMediaItem(it); preview.prepare() } }
                else previewFail = true
            }
        }
        preview.addListener(l)
        onDispose { preview.removeListener(l); preview.release() }
    }
    LaunchedEffect(focusedCh) {
        val ch = focusedCh ?: return@LaunchedEffect
        if (ch.id == previewCh?.id && preview.playbackState != Player.STATE_IDLE) return@LaunchedEffect
        preview.stop()
        previewReady = false; previewFail = false
        delay(650) // solo si te detienes en el canal
        previewCh = ch; previewTry = 0
        liveMediaItem(ch)?.let { preview.setMediaItem(it); preview.prepare(); preview.playWhenReady = true }
    }

    val cats = remember(c, favs, recent) {
        buildList {
            if (favs.isNotEmpty()) add(FAVS to "★ Favoritos")
            if (recent.isNotEmpty()) add(RECENT to "Vistos recientemente")
            add(ALLCH to "Todos los canales")
            c.visLiveCats.forEach { add(it.id to it.name) }
        }
    }
    val counts = remember(c) { c.visChannels.groupingBy { it.cat }.eachCount() }
    val list = remember(c, cat, favs, recent) { channelsFor(cat, c, favs, recent) }

    if (c.channels.isEmpty()) {
        CenterMessage("Tu cuenta no tiene canales en vivo")
        return
    }

    if (LocalCompact.current) {
        // Celular: el canal que tocas se ve arriba (como YouTube/Apple TV); toca el video para verlo en grande
        val pc = previewCh
        fun full() { val i = list.indexOfFirst { it.id == pc?.id }; if (pc != null) { preview.stop(); nav.playLive(if (i >= 0) list else listOf(pc), i.coerceAtLeast(0)) } }
        val video: @Composable (Modifier) -> Unit = { m ->
            Box(m.clip(RoundedCornerShape(14.dp)).background(Color.Black).clickable { full() }, contentAlignment = Alignment.Center) {
                if (pc != null && !previewFail) MiniVideo(preview, Modifier.fillMaxSize())
                if (pc == null) Text("Toca un canal para verlo aquí", color = Color(0xFFA1A1A6), fontSize = 14.sp)
                else if (!previewReady || previewFail) {
                    Img(pc.icon, Modifier.fillMaxSize(0.4f), ContentScale.Fit)
                    if (previewFail) Text("No se pudo abrir el canal", color = Color(0xFFFFD60A), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
                    else Spinner(Modifier.align(Alignment.BottomEnd).padding(8.dp), 22.dp)
                }
                if (pc != null) Icon(Icons.Filled.Fullscreen, "Pantalla completa", Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp), tint = Color.White)
            }
        }
        val listUi: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(cats.filter { (id, _) -> when (id) { FAVS -> favs.isNotEmpty(); RECENT -> recent.isNotEmpty(); ALLCH -> true; else -> (counts[id] ?: 0) > 0 } }, key = { it.first }) { (id, name) ->
                        Pill(name, cat == id, { cat = id; nav.state["live_cat"] = id })
                    }
                }
                LazyColumn(state = chState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(list, key = { _, ch -> ch.id }) { _, ch ->
                        ChannelRow(ch, ch.id in favs, Modifier, selected = ch.id == pc?.id, onLongClick = { Library.toggleFavChannel(ch.id) }) {
                            if (pc?.id == ch.id) full() else focusedCh = ch
                        }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        if (pc != null) PhoneNowPlaying(pc, c.liveCatName[pc.cat].orEmpty()) { full() }
                    }
                    listUi(Modifier.width(320.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    PhoneTopBar("En vivo")
                    Spacer(Modifier.height(8.dp))
                    video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    if (pc != null) PhoneNowPlaying(pc, c.liveCatName[pc.cat].orEmpty()) { full() }
                    listUi(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.width(220.dp).fillMaxHeight().background(C.Panel.copy(alpha = 0.75f)),
            contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Text("TV en vivo", color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)) }
            item {
                ActionButton("Pantalla múltiple", { preview.stop(); nav.state.remove("mv_slots"); nav.go(Route.MultiView(list, focusedCh)) }, Modifier.fillMaxWidth().padding(bottom = 10.dp), icon = Icons.Filled.GridView)
            }
            items(cats, key = { it.first }) { (id, name) ->
                val n = when (id) { FAVS -> favs.size; RECENT -> recent.size; ALLCH -> c.visChannels.size; else -> counts[id] ?: 0 }
                if (n > 0) LiveCatItem(name, n, cat == id, Modifier.rememberFocus(mem, "cat:$id")) {
                    cat = id; nav.state["live_cat"] = id
                }
            }
        }
        LazyColumn(
            state = chState,
            modifier = Modifier.width(350.dp).fillMaxHeight(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(list, key = { _, ch -> ch.id }) { i, ch ->
                ChannelRow(
                    ch, ch.id in favs,
                    Modifier.rememberFocus(mem, "ch:$cat:${ch.id}").then(if (i == 0) Modifier.focusRequester(firstFr) else Modifier),
                    onFocused = { focusedCh = ch },
                    onLongClick = { Library.toggleFavChannel(ch.id) },
                ) { preview.stop(); nav.playLive(list, i) }
            }
        }
        // Panel de información del canal enfocado
        Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)) {
            val ch = focusedCh
            if (ch != null) ChannelInfoPanel(ch, c.liveCatName[ch.cat].orEmpty()) {
                // Vista previa 16:9
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (previewCh?.id == ch.id && !previewFail) MiniVideo(preview, Modifier.fillMaxSize())
                    if (previewCh?.id != ch.id || !previewReady || previewFail) {
                        Box(Modifier.fillMaxSize().background(C.Panel2), contentAlignment = Alignment.Center) {
                            Img(ch.icon, Modifier.fillMaxSize(0.5f), ContentScale.Fit)
                            if (previewFail) Text("Vista previa no disponible", color = C.Low, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
                            else if (previewCh?.id == ch.id) Spinner(Modifier.align(Alignment.BottomEnd).padding(8.dp), 22.dp)
                        }
                    }
                }
            }
            else Text("Elige un canal. Mantén presionado OK para agregarlo a Favoritos.", color = C.Low, fontSize = 14.sp)
        }
    }
    RestoreFocus(mem, firstFr)
}

@Composable
private fun ChannelInfoPanel(ch: Channel, catName: String, video: @Composable () -> Unit) {
    val epg = rememberEpg(ch, 300)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        video()
        Text("OK para ver en pantalla completa", color = C.Low, fontSize = 11.sp)
        Text(ch.name, color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(listOfNotNull(if (ch.num > 0) "Canal ${ch.num}" else null, catName.ifBlank { null }, if (ch.archiveDays > 0) "Guarda ${ch.archiveDays} días" else null).joinToString(" · "), color = C.Low, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        if (epg.isEmpty()) Text("Sin guía de programación para este canal.", color = C.Low, fontSize = 13.sp)
        epg.take(3).forEachIndexed { i, e ->
            Column {
                Text(
                    (if (i == 0) "AHORA  " else "${TextUtil.hhmm(e.start)}  ") + e.title,
                    color = if (i == 0) C.Hi else C.Mid, fontSize = if (i == 0) 15.sp else 13.sp,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (i == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        ProgressBar(e.progress(), Modifier.width(180.dp))
                        Text("  ${TextUtil.hhmm(e.start)}–${TextUtil.hhmm(e.end)}", color = C.Low, fontSize = 11.sp)
                    }
                    if (e.desc.isNotBlank()) Text(e.desc, color = C.Mid, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
fun ChannelRow(ch: Channel, fav: Boolean, modifier: Modifier, selected: Boolean = false, onFocused: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.AccentSoft else C.Panel.copy(alpha = 0.7f),
            contentColor = C.Hi,
            focusedContainerColor = C.Accent,
            focusedContentColor = C.OnAccent,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ch.num > 0) ch.num.toString() else "", Modifier.width(34.dp), fontSize = 12.sp, color = if (focused) Color(0xDDFFFFFF) else C.Low)
            Box(Modifier.size(width = 52.dp, height = 32.dp).background(C.Panel2, RoundedCornerShape(5.dp)).padding(3.dp), contentAlignment = Alignment.Center) {
                Img(ch.icon, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val epg = rememberEpg(ch, 600)
                epg.firstOrNull()?.let { e ->
                    Text(e.title, fontSize = 11.sp, color = if (focused) Color(0xDDFFFFFF) else C.Low, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressBar(e.progress(), Modifier.fillMaxWidth().padding(top = 3.dp), 2.dp)
                }
            }
            if (fav) Icon(Icons.Filled.Star, "Favorito", Modifier.size(16.dp), tint = C.Gold)
        }
    }
}

@Composable
private fun LiveCatItem(name: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
private fun PhoneNowPlaying(ch: Channel, catName: String, onFull: () -> Unit) {
    val nav = LocalNav.current
    val epg = rememberEpg(ch, 0)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(epg.firstOrNull()?.let { "Ahora: " + it.title } ?: catName, color = C.Mid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            epg.firstOrNull()?.let { ProgressBar(it.progress(), Modifier.fillMaxWidth(0.7f).padding(top = 4.dp)) }
        }
        Icon(Icons.Filled.GridView, "Pantalla múltiple", Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { nav.state.remove("mv_slots"); nav.go(Route.MultiView(listOf(ch), ch)) }.padding(8.dp), tint = C.Mid)
        Icon(Icons.Filled.Fullscreen, "Pantalla completa", Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onFull() }.padding(8.dp), tint = C.Accent)
    }
}
