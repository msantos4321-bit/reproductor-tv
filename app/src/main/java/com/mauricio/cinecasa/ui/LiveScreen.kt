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

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.width(220.dp).fillMaxHeight().background(Color(0x66000000)),
            contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Text("TV en vivo", color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)) }
            items(cats, key = { it.first }) { (id, name) ->
                val n = when (id) { FAVS -> favs.size; RECENT -> recent.size; ALLCH -> c.visChannels.size; else -> counts[id] ?: 0 }
                if (n > 0) LiveCatItem(name, n, cat == id, Modifier.rememberFocus(mem, "cat:$id")) {
                    cat = id; nav.state["live_cat"] = id
                }
            }
        }
        LazyColumn(
            state = chState,
            modifier = Modifier.width(400.dp).fillMaxHeight(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(list, key = { _, ch -> ch.id }) { i, ch ->
                ChannelRow(
                    ch, ch.id in favs,
                    Modifier.rememberFocus(mem, "ch:$cat:${ch.id}").then(if (i == 0) Modifier.focusRequester(firstFr) else Modifier),
                    onFocused = { focusedCh = ch },
                    onLongClick = { Library.toggleFavChannel(ch.id) },
                ) { nav.playLive(list, i) }
            }
        }
        // Panel de información del canal enfocado
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            val ch = focusedCh
            if (ch != null) ChannelInfoPanel(ch, c.liveCatName[ch.cat].orEmpty())
            else Text("Elige un canal. Mantén presionado OK para agregarlo a Favoritos.", color = C.Low, fontSize = 14.sp)
        }
    }
    RestoreFocus(mem, firstFr)
}

@Composable
private fun ChannelInfoPanel(ch: Channel, catName: String) {
    val epg = rememberEpg(ch, 300)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 160.dp, height = 90.dp).background(C.Panel2, RoundedCornerShape(10.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
            Img(ch.icon, Modifier.fillMaxSize(), ContentScale.Fit)
        }
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
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ch.num > 0) ch.num.toString() else "", Modifier.width(34.dp), fontSize = 12.sp, color = if (focused) Color.DarkGray else C.Low)
            Box(Modifier.size(width = 52.dp, height = 32.dp).background(Color(0x22FFFFFF), RoundedCornerShape(5.dp)).padding(3.dp), contentAlignment = Alignment.Center) {
                Img(ch.icon, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val epg = rememberEpg(ch, 600)
                epg.firstOrNull()?.let { e ->
                    Text(e.title, fontSize = 11.sp, color = if (focused) Color.DarkGray else C.Low, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), fontSize = 11.sp, color = C.Low)
        }
    }
}
