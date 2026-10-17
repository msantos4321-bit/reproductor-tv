package com.mauricio.cinecasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon

import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.Library
import com.mauricio.cinecasa.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla múltiple: 2 o 4 canales a la vez (ideal para deportes).
 * El audio sigue al cuadro seleccionado. Cada cuadro usa una de las pantallas de tu cuenta.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MultiViewScreen(pool: List<Channel>, first: Channel?) {
    LandscapeWhilePlaying()
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val c by Repo.catalog.collectAsState()
    val favs by Library.favChannels.collectAsState()
    val recent by Library.recentChannels.collectAsState()

    // Se recuerda lo que tenías puesto si sales a ver uno en grande y regresas
    @Suppress("UNCHECKED_CAST")
    val saved = nav.state["mv_slots"] as? List<Int?>
    val slots = remember {
        mutableStateListOf<Channel?>().apply {
            if (saved != null) saved.forEach { id -> add(id?.let { c.channelById[it] }) }
            else { add(first ?: pool.firstOrNull()); add(null); add(null); add(null) }
        }
    }
    var count by remember { mutableIntStateOf((nav.state["mv_count"] as? Int) ?: 2) }
    var focused by remember { mutableIntStateOf(0) }
    var picker by remember { mutableIntStateOf(-1) } // cuadro para el que se está eligiendo canal
    var maxCons by remember { mutableIntStateOf(0) }
    val errors = remember { mutableStateListOf<String?>(null, null, null, null) }
    val ready = remember { mutableStateListOf(false, false, false, false) }
    val tries = remember { IntArray(4) }
    val players = remember { List(4) { buildPlayer(ctx, live = true) } }
    val tileFr = remember { List(4) { FocusRequester() } }

    fun persist() {
        nav.state["mv_slots"] = slots.map { it?.id }
        nav.state["mv_count"] = count
    }

    fun load(i: Int) {
        val ch = slots[i]
        val p = players[i]
        errors[i] = null; ready[i] = false; tries[i] = 0
        if (ch == null || i >= count) { p.stop(); p.clearMediaItems(); return }
        liveMediaItem(ch)?.let { p.setMediaItem(it); p.prepare(); p.playWhenReady = true }
    }

    DisposableEffect(Unit) {
        val listeners = players.mapIndexed { i, p ->
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready[i] = true }
                override fun onPlayerError(error: PlaybackException) {
                    val ch = slots[i] ?: return
                    if (tries[i] < 2) { tries[i]++; liveMediaItem(ch, tries[i])?.let { p.setMediaItem(it); p.prepare() } }
                    else errors[i] = if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                        "El servidor no dio otra pantalla (límite de tu cuenta)"
                    else if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
                        "Tu tele no puede decodificar tantos videos a la vez"
                    else "No se pudo abrir este canal"
                }
            }.also { p.addListener(it) }
        }
        onDispose {
            players.forEachIndexed { i, p -> p.removeListener(listeners[i]); p.release() }
        }
    }

    LaunchedEffect(Unit) {
        for (i in 0 until 4) load(i)
        Repo.accountInfo()?.let { maxCons = it.maxCons }
        delay(100); runCatching { tileFr[0].requestFocus() }
    }
    // El audio solo del cuadro seleccionado
    LaunchedEffect(focused, count) {
        players.forEachIndexed { i, p -> p.volume = if (i == focused) 1f else 0f }
    }

    BackHandler {
        if (picker >= 0) { val i = picker; picker = -1; runCatching { tileFr[i].requestFocus() } } else nav.back()
    }

    val used = slots.take(count).count { it != null }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // Barra superior
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pantalla múltiple", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusProperties { canFocus = picker < 0 }) {
                    Pill("2 cuadros", count == 2, { count = 2; persist(); for (i in 0 until 4) load(i) })
                    Pill("4 cuadros", count == 4, { count = 4; persist(); for (i in 0 until 4) load(i) })
                }
                Spacer(Modifier.weight(1f))
                val warn = maxCons in 1 until used
                Text(
                    if (warn) "Tu cuenta permite $maxCons pantallas a la vez: un cuadro no va a cargar"
                    else "OK: cambiar canal · Mantén OK: ver en grande · El audio sigue al cuadro marcado",
                    color = if (warn) Color(0xFFFFD60A) else Color(0xFFA1A1A6), fontSize = 12.sp,
                )
            }
            // Rejilla
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                @Composable
                fun tile(i: Int, m: Modifier) = MultiTile(
                    ch = slots[i], focusedTile = focused == i, ready = ready[i], error = errors[i],
                    player = players[i],
                    modifier = m.focusRequester(tileFr[i]).focusProperties { canFocus = picker < 0 }
                        .onFocusChanged { if (it.isFocused) focused = i },
                    onClick = { picker = i },
                    onLongClick = {
                        val ch = slots[i] ?: return@MultiTile
                        persist()
                        val idx = pool.indexOfFirst { it.id == ch.id }
                        if (idx >= 0) nav.playLive(pool, idx) else nav.playLive(listOf(ch), 0)
                    },
                )
                if (count == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        tile(0, Modifier.weight(1f).aspectRatio(16f / 9f))
                        tile(1, Modifier.weight(1f).aspectRatio(16f / 9f))
                    }
                } else {
                    Column(Modifier.fillMaxHeight().aspectRatio(16f / 9f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tile(0, Modifier.weight(1f).fillMaxHeight()); tile(1, Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tile(2, Modifier.weight(1f).fillMaxHeight()); tile(3, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }

        // Selector de canal para un cuadro
        if (picker >= 0) {
            val slot = picker
            ChannelPicker(
                c = c, favs = favs, recent = recent, startList = pool,
                current = slots[slot],
                onPick = { ch ->
                    slots[slot] = ch; persist(); load(slot); picker = -1
                    uiScope.launch { delay(80); runCatching { tileFr[slot].requestFocus() } }
                },
                onRemove = {
                    slots[slot] = null; persist(); load(slot); picker = -1
                    uiScope.launch { delay(80); runCatching { tileFr[slot].requestFocus() } }
                },
            )
        }
    }
}

@Composable
private fun MultiTile(
    ch: Channel?,
    focusedTile: Boolean,
    ready: Boolean,
    error: String?,
    player: androidx.media3.exoplayer.ExoPlayer,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF3A3A3C)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF111113), focusedContainerColor = Color(0xFF111113)),
    ) {
        Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.Center) {
            if (ch == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, null, Modifier.size(34.dp), tint = Color(0xFFA1A1A6))
                    Text("Elegir canal", color = Color(0xFFA1A1A6), fontSize = 14.sp)
                }
            } else {
                MiniVideo(player, Modifier.fillMaxSize())
                if (!ready && error == null) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF111113)), contentAlignment = Alignment.Center) {
                        Img(ch.icon, Modifier.fillMaxSize(0.35f), ContentScale.Fit)
                        Spinner(Modifier.align(Alignment.BottomEnd).padding(10.dp), 22.dp)
                    }
                }
                if (error != null) {
                    Box(Modifier.fillMaxSize().background(Color(0xE6111113)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(error, color = Color(0xFFFFD60A), fontSize = 13.sp)
                    }
                }
                // Etiqueta del canal
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xB3000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (focusedTile) Icon(Icons.Filled.VolumeUp, null, Modifier.size(14.dp), tint = Color.White)
                    Text((if (focusedTile) " " else "") + ch.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Lista lateral para escoger el canal de un cuadro: favoritos, recientes o por categoría. */
@Composable
private fun ChannelPicker(
    c: com.mauricio.cinecasa.data.Catalog,
    favs: List<Int>,
    recent: List<Int>,
    startList: List<Channel>,
    current: Channel?,
    onPick: (Channel) -> Unit,
    onRemove: () -> Unit,
) {
    val cats = remember(c, favs, recent) {
        buildList {
            add("__start" to "Lista actual")
            if (favs.isNotEmpty()) add(FAVS to "★ Favoritos")
            if (recent.isNotEmpty()) add(RECENT to "Vistos recientemente")
            add(ALLCH to "Todos los canales")
            c.visLiveCats.forEach { add(it.id to it.name) }
        }
    }
    var cat by remember { mutableStateOf("__start") }
    val list = remember(cat, c, favs, recent) { if (cat == "__start") startList else channelsFor(cat, c, favs, recent) }
    val firstFr = remember { FocusRequester() }

    Row(Modifier.fillMaxHeight().width(600.dp).background(C.Glass)) {
        LazyColumn(
            Modifier.width(210.dp).fillMaxHeight(),
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Text("Elegir canal", color = C.Hi, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) }
            if (current != null) item { ActionButton("Quitar cuadro", onRemove, Modifier.fillMaxWidth().padding(bottom = 8.dp), icon = Icons.Filled.Close) }
            items(cats, key = { it.first }) { (id, name) -> Pill(name, cat == id, { cat = id }, Modifier.fillMaxWidth().padding(vertical = 2.dp)) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(list, key = { _, k -> k.id }) { i, k ->
                ChannelRow(k, k.id in favs, if (i == 0) Modifier.focusRequester(firstFr) else Modifier, selected = k.id == current?.id) { onPick(k) }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstFr.requestFocus() } }
}
