package com.mauricio.cinecasa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mauricio.cinecasa.BuildConfig
import com.mauricio.cinecasa.data.Catalog
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.FavTeam
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Profiles
import com.mauricio.cinecasa.data.Quiniela
import com.mauricio.cinecasa.data.Recorder
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SpEvent
import com.mauricio.cinecasa.data.SpTeam
import com.mauricio.cinecasa.data.Sports
import com.mauricio.cinecasa.data.SportsCats
import com.mauricio.cinecasa.data.Teams
import com.mauricio.cinecasa.data.TextUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar

// ======================================================================
// Piezas comunes
// ======================================================================

/** Marcadores al día mientras la pantalla esté abierta. */
@Composable
fun rememberBoard(): Sports.Board {
    DisposableEffect(Unit) { Sports.subscribe(); onDispose { Sports.unsubscribe() } }
    val b by Sports.board.collectAsState()
    return b
}

/** Partido fijado para el marcador de la esquina. */
object SportsPin { val key = MutableStateFlow<String?>(null) }

val Live = Color(0xFFFF3B30)

/** «Hoy 19:30», «Mañana 13:00», «jue 27 · 17:00». */
fun whenLabel(t: Long): String {
    val c = Calendar.getInstance()
    val today = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    c.timeInMillis = t
    val d = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    val c2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val tomorrow = c2.get(Calendar.DAY_OF_YEAR) to c2.get(Calendar.YEAR)
    val c3 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterday = c3.get(Calendar.DAY_OF_YEAR) to c3.get(Calendar.YEAR)
    val day = when (d) {
        today -> "Hoy"
        tomorrow -> "Mañana"
        yesterday -> "Ayer"
        else -> listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH)
    }
    return "$day ${TextUtil.hhmm(t)}"
}

/** «en 3 h 20 min», «en 15 min». */
fun countdown(t: Long, now: Long = System.currentTimeMillis()): String {
    val m = ((t - now) / 60_000).toInt()
    return when {
        m <= 0 -> "ya empieza"
        m < 60 -> "en $m min"
        m < 24 * 60 -> "en ${m / 60} h" + if (m % 60 >= 5) " ${m % 60} min" else ""
        else -> whenLabel(t)
    }
}

/** Línea de momios: «Favorito: CHI −120 · Total 55.5». */
fun oddsLine(ev: SpEvent): String? {
    if (!Prefs.showOdds || Profiles.current.value?.kids == true) return null
    val o = ev.odds ?: return null
    val parts = mutableListOf<String>()
    if (ev.sp == "soccer" && o.mlh.isNotBlank() && o.mla.isNotBlank()) {
        parts += "${ev.h.initials} ${pm(o.mlh)} · Empate ${pm(o.mld)} · ${ev.a.initials} ${pm(o.mla)}".replace(" ·  · ", " · ")
    } else {
        val fav = when (o.fav) { "h" -> ev.h to o.mlh; "a" -> ev.a to o.mla; else -> null }
        if (fav != null && fav.second.isNotBlank()) parts += "Favorito: ${fav.first.initials} ${pm(fav.second)}"
        else if (o.spread.isNotBlank()) parts += o.spread
    }
    o.ou?.let { if (it > 0) parts += "Total ${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()}" }
    return parts.joinToString(" · ").ifBlank { null }
}
private fun pm(s: String) = s.replace("-", "−")

fun leagueShort(lg: String) = Sports.leagueByKey[lg]?.short ?: lg.uppercase()

/** Escudo del equipo; si no carga (o en la versión de Google Play), círculo con su color e iniciales. */
@Composable
fun TeamBadge(t: SpTeam, size: Dp, modifier: Modifier = Modifier) {
    val url = if (!BuildConfig.PLAY && t.logo.isNotBlank() && !androidx.compose.ui.platform.LocalInspectionMode.current) t.logo else null
    var ok by remember(url) { mutableStateOf(false) }
    Box(modifier.size(size).clip(CircleShape).background(if (ok) Color(0xFFF2F2F7) else Color(t.color)), contentAlignment = Alignment.Center) {
        if (!ok) Text(t.initials, color = Color.White, fontSize = (size.value * 0.30f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (url != null) coil.compose.AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(size * 0.14f),
            onSuccess = { ok = true }, onError = { ok = false },
        )
    }
}

@Composable
fun LiveDot(text: String = "EN VIVO", size: Int = 11) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Live))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Live, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Estado corto del partido. */
fun statusText(ev: SpEvent): String = when {
    ev.live -> ev.det.ifBlank { "En juego" }
    ev.done -> ev.det.ifBlank { "Final" }
    else -> ev.det.ifBlank { whenLabel(ev.start) }
}

// ======================================================================
// Tarjeta de marcador
// ======================================================================
@Composable
fun ScoreCard(ev: SpEvent, modifier: Modifier = Modifier, width: Dp = 300.dp, channel: String? = null, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ns by Teams.noSpoil.collectAsState()
    val rv by Teams.revealed.collectAsState()
    val hide = remember(ev, ns, rv) { Teams.hidden(ev) }
    val teams by Teams.list.collectAsState()
    val rec = Recorder.list.collectAsState().value.any { it.evKey == ev.key && (it.scheduled || it.recording) }
    val shape = RoundedCornerShape(12.dp)
    val favKeys = remember(teams) { teams.map { it.key }.toSet() }
    val mine = favKeys.any { ev.has(it) }
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = if (mine) Border(BorderStroke(1.dp, C.Gold.copy(alpha = 0.6f)), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel, contentColor = C.Hi, focusedContainerColor = C.Panel, focusedContentColor = C.Hi),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(leagueShort(ev.lg), color = C.Low, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                if (ev.live) LiveDot(ev.det.ifBlank { "EN VIVO" })
                else Text(statusText(ev), color = if (ev.done) C.Mid else C.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (rec) Icon(Icons.Filled.FiberManualRecord, "Grabando", Modifier.size(12.dp), tint = Live)
                if (mine) Icon(Icons.Filled.Star, null, Modifier.size(13.dp), tint = C.Gold)
            }
            Spacer(Modifier.height(8.dp))
            TeamLine(ev.a, ev, hide)
            Spacer(Modifier.height(5.dp))
            TeamLine(ev.h, ev, hide)
            Spacer(Modifier.height(8.dp))
            val foot = when {
                hide -> "Marcador oculto"
                ev.live && ev.sp == "baseball" && ev.sit?.outs != null -> "${ev.sit.outs} out" + (if (ev.sit.outs == 1) "" else "s") + basesText(ev)
                ev.live && ev.sit?.down?.isNotBlank() == true -> ev.sit.down
                channel != null -> channel
                ev.upcoming -> oddsLine(ev) ?: ev.tv.joinToString(" · ").ifBlank { countdown(ev.start) }
                else -> ev.note
            }
            Text(foot, color = C.Low, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun basesText(ev: SpEvent): String {
    val s = ev.sit ?: return ""
    val b = listOfNotNull(if (s.b1) "1ª" else null, if (s.b2) "2ª" else null, if (s.b3) "3ª" else null)
    return if (b.isEmpty()) "" else " · corredor en " + b.joinToString(", ")
}

@Composable
private fun TeamLine(t: SpTeam, ev: SpEvent, hide: Boolean) {
    val lost = ev.done && !t.win && (ev.h.win || ev.a.win)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, 26.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.n.ifBlank { t.full }, color = if (lost) C.Low else C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.rec.isNotBlank() && (ev.upcoming || hide)) Text(t.rec, color = C.Low, fontSize = 11.sp)
        if (!ev.upcoming && !hide) Text(t.sc, color = if (lost) C.Low else C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Tarjeta de un equipo que sigo: próximo partido, último resultado y racha. */
@Composable
fun MyTeamCard(f: FavTeam, events: List<SpEvent>, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val page by produceState<com.mauricio.cinecasa.data.SpTeamPage?>(null, f.key) { value = Sports.team(f.lg, f.id) }
    val mineEvents = events.filter { it.has(f.key) }
    val live = mineEvents.firstOrNull { it.live }
    val next = mineEvents.firstOrNull { it.upcoming }
    val nextG = page?.next?.firstOrNull()
    val last = page?.past?.lastOrNull()
    val shape = RoundedCornerShape(12.dp)
    val hideAll = f.key in Teams.noSpoil.collectAsState().value
    Surface(
        onClick = onClick,
        modifier = modifier.width(280.dp).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel, contentColor = C.Hi, focusedContainerColor = C.Panel, focusedContentColor = C.Hi),
    ) {
        Column {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(f.asTeam().color)))
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(f.asTeam(), 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.n.ifBlank { f.full }, color = C.Hi, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(leagueShort(f.lg), page?.rec?.ifBlank { null }, page?.standing?.ifBlank { null }).joinToString(" · "),
                        color = C.Low, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                live != null -> {
                    LiveDot(live.det.ifBlank { "EN VIVO" })
                    Text(if (Teams.hidden(live)) "${live.a.initials} vs ${live.h.initials} · marcador oculto" else "${live.a.initials} ${live.a.sc} – ${live.h.sc} ${live.h.initials}",
                        color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                next != null -> {
                    val opp = if (next.h.id == f.id) next.a else next.h
                    Text("Próximo: ${if (next.h.id == f.id) "vs" else "en"} ${opp.n}", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${whenLabel(next.start)} · ${countdown(next.start)}", color = C.Accent, fontSize = 12.sp)
                }
                nextG != null -> {
                    Text("Próximo: ${if (nextG.home) "vs" else "en"} ${nextG.opp.n}", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(whenLabel(nextG.start), color = C.Accent, fontSize = 12.sp)
                }
                else -> Text(if (page == null) "Cargando…" else "Sin partidos próximos", color = C.Low, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            if (!hideAll && last != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultChip(last.res)
                    Spacer(Modifier.width(6.dp))
                    Text("${last.us}-${last.them} ${if (last.home) "vs" else "en"} ${last.opp.initials}", color = C.Mid, fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    page?.streak?.takeIf { it.isNotBlank() }?.let { Text("Racha ${streakEs(it)}", color = C.Low, fontSize = 11.sp) }
                }
            } else if (hideAll) Text("Sin marcadores activado", color = C.Low, fontSize = 12.sp)
        }
        }
    }
}

fun streakEs(s: String): String = s.replace("G", " ganados").replace("P", " perdidos").replace("E", " empates")

@Composable
fun ResultChip(r: String) {
    val (bg, t) = when (r) { "G" -> C.Good to "G"; "P" -> Live to "P"; "E" -> C.Low to "E"; else -> C.Panel2 to "·" }
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(t, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Tile de canal (para PPV y canales de deportes). */
@Composable
fun ChannelTile(ch: Channel, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val epg = rememberEpg(ch, 900)
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(220.dp).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel, contentColor = C.Hi, focusedContainerColor = C.Panel, focusedContentColor = C.Hi),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(6.dp)).background(C.Panel2).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(ch.name, color = C.Mid, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
                Img(ch.icon, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            Spacer(Modifier.height(6.dp))
            Text(ch.name, color = C.Hi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(epg.firstOrNull()?.title ?: " ", color = C.Low, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ======================================================================
// Pantalla Deportes
// ======================================================================
private const val F_ALL = "all"
private const val F_LIVE = "live"
private const val F_MINE = "mine"
private const val F_PPV = "ppv"
private const val F_CH = "ch"

private data class SRow(val id: String, val title: String, val events: List<SpEvent> = emptyList(), val channels: List<Channel> = emptyList(), val teams: List<FavTeam> = emptyList())

@Composable
fun SportsScreen() {
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    val board = rememberBoard()
    val teams by Teams.list.collectAsState()
    val sportsCats = remember(c) { SportsCats.ids(c) }
    var filter by remember { mutableStateOf((nav.state["sp_filter"] as? String) ?: F_ALL) }
    var focused by remember { mutableStateOf<SpEvent?>(null) }
    var sheet by remember { mutableStateOf<SpEvent?>(null) }
    var picker by remember { mutableStateOf(false) }
    val mem = remember { nav.memory("sports") }
    val firstFr = remember { FocusRequester() }
    val colState = remember { nav.state.getOrPut("sp_col") { LazyListState() } as LazyListState }
    val compact = LocalCompact.current
    val side = if (compact) 16.dp else 28.dp

    LaunchedEffect(board.at) { Quiniela.settle(board.events) }

    val favKeys = remember(teams) { teams.map { it.key }.toSet() }
    val leaguesPresent = remember(board.events) { Sports.leagues.filter { l -> board.events.any { it.lg == l.k } } }
    val ppvChannels = remember(c, sportsCats) { c.visChannels.filter { it.cat in sportsCats && SportsCats.isPpv(c.liveCatName[it.cat].orEmpty()) } }
    val sportCatsList = remember(c, sportsCats) { c.visLiveCats.filter { it.id in sportsCats && !SportsCats.isPpv(it.name) } }

    val rows = remember(board.events, filter, favKeys, ppvChannels, sportCatsList) {
        val now = System.currentTimeMillis()
        fun mineFirst(l: List<SpEvent>) = l.sortedByDescending { e -> favKeys.any { e.has(it) } }
        val evs = when (filter) {
            F_ALL, F_LIVE, F_PPV, F_CH -> board.events
            F_MINE -> board.events.filter { e -> favKeys.any { e.has(it) } }
            else -> board.events.filter { it.lg == filter }
        }
        val endOfToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        buildList {
            if (filter == F_ALL || filter == F_MINE) {
                if (teams.isNotEmpty()) add(SRow("teams", "Mis equipos", teams = teams))
            }
            if (filter != F_PPV && filter != F_CH) {
                val live = mineFirst(evs.filter { it.live })
                if (live.isNotEmpty()) add(SRow("live", "En vivo ahora", live))
                if (filter != F_LIVE) {
                    val today = mineFirst(evs.filter { it.upcoming && it.start <= endOfToday })
                    if (today.isNotEmpty()) add(SRow("today", "Hoy", today))
                    val later = mineFirst(evs.filter { it.upcoming && it.start > endOfToday }).take(40)
                    if (later.isNotEmpty()) add(SRow("later", "Próximos días", later.sortedBy { it.start }))
                    val done = evs.filter { it.done && it.start > now - 30 * 3600_000 }.sortedByDescending { it.start }
                    if (done.isNotEmpty()) add(SRow("done", "Resultados", mineFirst(done).take(40)))
                }
            }
            if (filter == F_ALL || filter == F_PPV || filter == F_LIVE) {
                if (ppvChannels.isNotEmpty()) add(SRow("ppv", "Pago por evento", channels = ppvChannels.take(60)))
            }
            if (filter == F_ALL || filter == F_CH || filter == F_LIVE) {
                sportCatsList.forEach { cat ->
                    val l = c.visChannels.filter { it.cat == cat.id }
                    if (l.isNotEmpty()) add(SRow("cat:${cat.id}", cat.name, channels = l.take(50)))
                }
            }
        }
    }

    // Destacado: el que tienes enfocado, o el mejor partido del momento
    val featured = remember(board.events, favKeys) {
        val e = board.events
        e.firstOrNull { it.live && favKeys.any { k -> it.has(k) } }
            ?: e.firstOrNull { it.upcoming && favKeys.any { k -> it.has(k) } && it.start < System.currentTimeMillis() + 24 * 3600_000 }
            ?: e.filter { it.live }.minByOrNull { Sports.leagues.indexOfFirst { l -> l.k == it.lg }.let { i -> if (i < 0) 99 else i } }
            ?: e.firstOrNull { it.upcoming }
    }
    var heroEv by remember { mutableStateOf<SpEvent?>(null) }
    LaunchedEffect(focused) { delay(350); heroEv = focused }
    val hero = (heroEv ?: featured)?.let { h -> board.events.firstOrNull { it.key == h.key } ?: h }

    if (c.channels.isEmpty() && board.events.isEmpty() && board.at == 0L) {
        CenterMessage("Deportes", "Cargando marcadores…", loading = true); return
    }

    val filters = buildList {
        add(F_ALL to "Todo")
        if (board.events.any { it.live }) add(F_LIVE to "En vivo")
        add(F_MINE to "★ Mis equipos")
        leaguesPresent.forEach { add(it.k to it.short) }
        if (ppvChannels.isNotEmpty()) add(F_PPV to "Pago por evento")
        if (sportCatsList.isNotEmpty()) add(F_CH to "Canales")
    }

    LazyColumn(state = colState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item("hero") {
            Column {
                if (compact) Box(Modifier.padding(horizontal = side)) { PhoneTopBar("Deportes") }
                SportsHero(hero, c, sportsCats, compact, side) { sheet = it }
            }
        }
        item("filters") {
            LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters, key = { it.first }) { (k, t) ->
                    Pill(t, filter == k, { filter = k; nav.state["sp_filter"] = k }, Modifier.rememberFocus(mem, "f:$k").then(if (k == F_ALL) Modifier.focusRequester(firstFr) else Modifier))
                }
                item("add") { Pill("Elegir equipos", false, { picker = true }, icon = Icons.Filled.Add) }
            }
        }
        if (board.error != null && board.events.isEmpty()) item("err") {
            Text(board.error, color = C.Warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = side))
        }
        if (filter == F_MINE && teams.isEmpty()) item("noteams") {
            Column(Modifier.padding(horizontal = side)) {
                Text("Aún no sigues equipos", color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Elige tus equipos y aquí verás cuándo juegan, en qué canal, sus resultados, la tabla, los lesionados y las noticias.", color = C.Mid, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                ActionButton("Elegir mis equipos", { picker = true }, primary = true, icon = Icons.Filled.Add)
            }
        }
        if (Sports.leagueByKey.containsKey(filter)) item("tablebtn") {
            Row(Modifier.padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (filter != "lmp" && filter != "ufc") ActionButton("Tabla de posiciones", { nav.go(Route.Table(filter)) })
            }
        }
        rows.forEach { row ->
            item(row.id) {
                val rs = remember { nav.state.getOrPut("sp_row_${row.id}") { LazyListState() } as LazyListState }
                Column {
                    SectionTitle(row.title, Modifier.padding(start = side))
                    LazyRow(state = rs, contentPadding = PaddingValues(horizontal = side, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        when {
                            row.teams.isNotEmpty() -> items(row.teams, key = { it.key }) { f ->
                                MyTeamCard(f, board.events, Modifier.rememberFocus(mem, "${row.id}/${f.key}"),
                                    onFocused = { focused = board.events.firstOrNull { e -> e.has(f.key) && !e.done } }) {
                                    nav.go(Route.Team(f.lg, f.asTeam()))
                                }
                            }
                            row.events.isNotEmpty() -> items(row.events, key = { it.key }) { e ->
                                ScoreCard(e, Modifier.rememberFocus(mem, "${row.id}/${e.key}"), width = if (compact) 270.dp else 300.dp, onFocused = { focused = e }) { sheet = e }
                            }
                            else -> items(row.channels, key = { it.id }) { ch ->
                                ChannelTile(ch, Modifier.rememberFocus(mem, "${row.id}/${ch.id}")) {
                                    val list = row.channels
                                    nav.playLive(list, list.indexOf(ch).coerceAtLeast(0))
                                }
                            }
                        }
                    }
                }
            }
        }
        item("quiniela") { QuinielaPanel(board.events, side) }
        item("foot") {
            Text(
                "Marcadores: ESPN y TheSportsDB. Los canales se buscan por nombre y guía de tu proveedor; si no aparece, búscalo en «Canales». " +
                    if (Prefs.showOdds) "Momios solo informativos." else "",
                color = C.Low, fontSize = 11.sp, modifier = Modifier.padding(horizontal = side),
            )
        }
    }
    RestoreFocus(mem, firstFr)

    sheet?.let { ev -> EventSheet(board.events.firstOrNull { it.key == ev.key } ?: ev, c, sportsCats) { sheet = null } }
    if (picker) TeamPickerDialog { picker = false }
}

/** Destacado de arriba: marcador grande y, a la derecha, el canal en vivo en chico (sin sonido). */
@Composable
private fun SportsHero(ev: SpEvent?, c: Catalog, sportsCats: Set<String>, compact: Boolean, side: Dp, onOpen: (SpEvent) -> Unit) {
    val nav = LocalNav.current
    if (ev == null) {
        Column(Modifier.padding(horizontal = side, vertical = 20.dp)) {
            Text("Deportes", color = C.Hi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Aquí verás los partidos en vivo, de hoy y de los próximos días.", color = C.Mid, fontSize = 14.sp)
        }
        return
    }
    val chans = remember(ev.key, c) { Sports.channelsFor(ev, c, sportsCats) }
    val ch = chans.firstOrNull()
    val hide = Teams.hidden(ev)
    Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = if (compact) 8.dp else 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Sports.leagueByKey[ev.lg]?.name ?: ev.lg, color = C.Low, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                if (ev.live) LiveDot(ev.det.ifBlank { "EN VIVO" }, 13) else Text(statusText(ev) + if (ev.upcoming) " · ${countdown(ev.start)}" else "", color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            HeroTeam(ev.a, ev, hide, compact)
            Spacer(Modifier.height(6.dp))
            HeroTeam(ev.h, ev, hide, compact)
            Spacer(Modifier.height(10.dp))
            val extra = listOfNotNull(
                if (ev.live && ev.sit?.last?.isNotBlank() == true && !hide) ev.sit.last else null,
                if (ev.upcoming) oddsLine(ev) else null,
                ch?.let { "Canal: ${it.name}" } ?: ev.tv.joinToString(" · ").ifBlank { null },
            )
            extra.forEach { Text(it, color = C.Mid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(12.dp))
            Wrap(gap = 10.dp) {
                if (ch != null && (ev.live || ev.start - System.currentTimeMillis() < 30 * 60_000)) ActionButton("Ver en vivo", { nav.playLive(chans, 0) }, primary = true, icon = Icons.Filled.PlayArrow)
                ActionButton("Opciones", { onOpen(ev) })
            }
        }
        if (!compact) {
            Spacer(Modifier.width(24.dp))
            Box(Modifier.width(420.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                if (ch != null && ev.live && !androidx.compose.ui.platform.LocalInspectionMode.current) HeroPreview(ch)
                else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(ev.a.color).copy(alpha = 0.55f), Color(0xFF1C1C1E), Color(ev.h.color).copy(alpha = 0.55f)))))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(ev.a, 84.dp)
                        Text("  vs  ", color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        TeamBadge(ev.h, 84.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTeam(t: SpTeam, ev: SpEvent, hide: Boolean, compact: Boolean) {
    val lost = ev.done && !t.win && (ev.h.win || ev.a.win)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, if (compact) 34.dp else 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(t.full.ifBlank { t.n }, color = if (lost) C.Low else C.Hi, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (t.rec.isNotBlank() && (ev.upcoming || hide)) Text("  ${t.rec}", color = C.Low, fontSize = 13.sp)
        if (!ev.upcoming && !hide) Text(t.sc, color = if (lost) C.Low else C.Hi, fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroPreview(ch: Channel) {
    val ctx = LocalContext.current
    val player = remember { buildPlayer(ctx, live = true).apply { volume = 0f } }
    var ready by remember { mutableStateOf(false) }
    var fail by remember { mutableStateOf(false) }
    var tryN by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready = true }
            override fun onPlayerError(error: PlaybackException) {
                if (tryN < 1) { tryN++; liveMediaItem(ch, tryN)?.let { player.setMediaItem(it); player.prepare() } } else fail = true
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(ch.id) {
        ready = false; fail = false; tryN = 0
        delay(900)
        liveMediaItem(ch)?.let { player.setMediaItem(it); player.prepare(); player.playWhenReady = true }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!fail) MiniVideo(player, Modifier.fillMaxSize())
        if (!ready || fail) {
            Img(ch.icon, Modifier.fillMaxSize(0.35f), ContentScale.Fit)
            if (!fail) Spinner(Modifier.align(Alignment.BottomEnd).padding(10.dp), 22.dp)
        }
        Text(ch.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Quiniela de la casa: quién lleva más aciertos en los últimos 30 días. */
@Composable
private fun QuinielaPanel(events: List<SpEvent>, side: Dp) {
    val picks by Quiniela.picks.collectAsState()
    val profiles by Profiles.list.collectAsState()
    if (picks.isEmpty()) return
    val table = remember(picks) { Quiniela.standings() }
    val pending = remember(picks, events) {
        val now = System.currentTimeMillis()
        picks.values.filter { it.result.isBlank() && it.start > now - 6 * 3600_000 }.sortedBy { it.start }.take(6)
    }
    Column(Modifier.padding(horizontal = side)) {
        SectionTitle("Quiniela de la casa")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(14.dp).width(320.dp)) {
                Text("Aciertos (últimos 30 días)", color = C.Low, fontSize = 12.sp)
                if (table.isEmpty()) Text("Todavía no termina ningún partido elegido.", color = C.Mid, fontSize = 13.sp)
                table.forEachIndexed { i, (pid, ok, n) ->
                    val name = profiles.firstOrNull { it.id == pid }?.name ?: "Perfil"
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", color = if (i == 0) C.Gold else C.Low, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                        Text(name, color = C.Hi, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("$ok de $n", color = C.Mid, fontSize = 14.sp)
                    }
                }
            }
            if (pending.isNotEmpty()) Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(14.dp).width(360.dp)) {
                Text("Por jugarse", color = C.Low, fontSize = 12.sp)
                pending.forEach { p ->
                    val name = profiles.firstOrNull { it.id == p.profile }?.name ?: "Perfil"
                    val ev = events.firstOrNull { it.key == p.ev }
                    val who = when (p.pick) { "h" -> ev?.h?.n; "a" -> ev?.a?.n; else -> "Empate" } ?: p.pick
                    Text("$name: $who · ${p.title}", color = C.Mid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

/** Gráfica de probabilidad de ganar (local arriba, visitante abajo). */
@Composable
fun WinProbChart(points: List<Float>, home: SpTeam, away: SpTeam, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val hc = Color(home.color); val ac = Color(away.color)
    val line = C.Hi; val grid = C.Line
    Column(modifier) {
        Row {
            Text("${home.initials} ${points.last().toInt()}%", color = C.Hi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${away.initials} ${(100 - points.last()).toInt()}%", color = C.Mid, fontSize = 13.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp)) {
            val w = size.width; val h = size.height
            drawRect(hc.copy(alpha = 0.10f), size = androidx.compose.ui.geometry.Size(w, h / 2))
            drawRect(ac.copy(alpha = 0.10f), topLeft = Offset(0f, h / 2), size = androidx.compose.ui.geometry.Size(w, h / 2))
            drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), 1f)
            val p = Path()
            points.forEachIndexed { i, v ->
                val x = w * i / (points.size - 1)
                val y = h * (1f - v / 100f)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, line, style = Stroke(width = 3f))
        }
    }
}
