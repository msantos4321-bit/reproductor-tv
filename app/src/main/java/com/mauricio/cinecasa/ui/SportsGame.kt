package com.mauricio.cinecasa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.GForm
import com.mauricio.cinecasa.data.GOdds
import com.mauricio.cinecasa.data.GPlayerGroup
import com.mauricio.cinecasa.data.GStat
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Profiles
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SpEvent
import com.mauricio.cinecasa.data.SpGameDetail
import com.mauricio.cinecasa.data.SpInjury
import com.mauricio.cinecasa.data.SpPlay
import com.mauricio.cinecasa.data.SpTeam
import com.mauricio.cinecasa.data.Sports
import com.mauricio.cinecasa.data.SportsCats
import com.mauricio.cinecasa.data.Teams
import kotlinx.coroutines.delay

// ======================================================================
// Piezas para pantallas de información que se recorren con el control
// ======================================================================

/**
 * Tarjeta que se puede enfocar: así el control baja por la pantalla de tarjeta en tarjeta
 * (sin esto la tele no sabe a dónde ir y el foco se va al menú lateral).
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    pad: Dp = 16.dp,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, C.Accent), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel, contentColor = C.Hi, focusedContainerColor = C.Panel, focusedContentColor = C.Hi),
    ) {
        Column(Modifier.fillMaxWidth().padding(pad), content = content)
    }
}

/** Color del equipo que se vea bien sobre fondo oscuro. */
fun teamTint(t: SpTeam): Color {
    if (t.col.length != 6) return Color(0xFF8E8E93)
    val c = Color(t.color)
    // Colores muy oscuros (azul marino, negro): se aclaran para que se vean sobre el fondo grafito
    return if (c.luminance() < 0.09f) androidx.compose.ui.graphics.lerp(c, Color.White, if (c.luminance() < 0.02f) 0.55f else 0.35f) else c
}

/** Dos colores que no se confundan entre sí. */
fun teamTints(a: SpTeam, h: SpTeam): Pair<Color, Color> {
    val ca = teamTint(a); val ch = teamTint(h)
    val close = kotlin.math.abs(ca.red - ch.red) + kotlin.math.abs(ca.green - ch.green) + kotlin.math.abs(ca.blue - ch.blue) < 0.35f
    return if (close) Color(0xFFB0B0B8) to ch else ca to ch
}

/** Probabilidad que implica un momio americano (+150 = 40 %, −150 = 60 %). */
fun impliedPct(odds: String): Int? {
    val o = odds.replace("+", "").replace("−", "-").trim().toIntOrNull() ?: return null
    if (o == 0) return null
    return if (o < 0) (-o * 100 / (-o + 100)) else (100 * 100 / (o + 100))
}

private fun am(s: String) = s.replace("-", "−").ifBlank { "—" }

// ======================================================================
// Detalle del partido (estilo Gamecast)
// ======================================================================
@Composable
fun GameScreen(ev0: SpEvent) {
    val board = rememberBoard()
    val ev = board.events.firstOrNull { it.key == ev0.key } ?: ev0
    val detail by produceState<SpGameDetail?>(null, ev.key) {
        while (true) {
            value = Sports.game(ev.lg, ev.id, ev.live) ?: value
            delay(if (value?.st == "in") 20_000 else 120_000)
        }
    }
    GameBody(ev, detail)
}

private const val T_SUM = "Resumen"
private const val T_PRE = "Previa"
private const val T_STATS = "Estadísticas"
private const val T_PLAYERS = "Jugadores"
private const val T_LINEUP = "Alineaciones"
private const val T_PLAYS = "Jugadas"
private const val T_BETS = "Apuestas"
private const val T_INJ = "Lesionados"

@Composable
fun GameBody(ev: SpEvent, d: SpGameDetail?, initialTab: String? = null) {
    val c by Repo.catalog.collectAsState()
    val sportsCats = remember(c) { SportsCats.ids(c) }
    var sheet by remember { mutableStateOf(false) }
    val rv by Teams.revealed.collectAsState()
    val ns by Teams.noSpoil.collectAsState()
    val hide = remember(ev, rv, ns) { Teams.hidden(ev) }
    val fr = remember { FocusRequester() }
    val compact = LocalCompact.current
    val side = if (compact) 16.dp else 28.dp
    val odds = d?.odds?.takeIf { Prefs.showOdds && Profiles.current.value?.kids != true }

    val tabs = remember(d, ev.st, odds) {
        buildList {
            if (ev.upcoming) add(T_PRE) else add(T_SUM)
            if (!ev.upcoming && d?.stats?.isNotEmpty() == true) add(T_STATS)
            if (d?.lineups?.isNotEmpty() == true) add(T_LINEUP)
            if (d?.players?.isNotEmpty() == true) add(T_PLAYERS)
            if (d?.plays?.isNotEmpty() == true) add(T_PLAYS)
            if (odds != null) add(T_BETS)
            if (d?.inj?.isNotEmpty() == true) add(T_INJ)
        }
    }
    var tab by remember(ev.key) { mutableStateOf(initialTab ?: if (ev.upcoming) T_PRE else T_SUM) }
    if (tab !in tabs && tabs.isNotEmpty()) tab = tabs.first()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, top = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("head") { GameHeader(ev, d, hide, compact) }
        item("actions") {
            Wrap(gap = 10.dp) {
                ActionButton("Ver o grabar", { sheet = true }, Modifier.focusRequester(fr), primary = true, icon = Icons.Filled.PlayArrow)
                if (hide) ActionButton("Mostrar marcador", { Teams.reveal(ev.key) })
            }
        }
        if (d == null) item("load") { Spinner() }
        else if (hide && !ev.upcoming) item("hidden") {
            FocusCard { Text("Activaste «sin marcadores» para este equipo. Elige «Mostrar marcador» para ver estadísticas y jugadas.", color = C.Mid, fontSize = 14.sp) }
        } else {
            item("tabs") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(tabs, key = { it }) { t -> Pill(t, tab == t, { tab = t }, onFocused = { tab = t }) }
                }
            }
            when (tab) {
                T_SUM -> summary(ev, d)
                T_PRE -> preview(ev, d, odds)
                T_STATS -> statsTab(ev, d.stats, "Estadísticas del partido")
                T_LINEUP -> lineupsTab(ev, d)
                T_PLAYERS -> playersTab(ev, d)
                T_PLAYS -> playsTab(ev, d.plays, d.drive)
                T_BETS -> if (odds != null) betsTab(ev, d, odds)
                T_INJ -> injuriesTab(ev, d.inj)
            }
            item("src") {
                Text(
                    "Datos de ESPN." + if (odds != null) " Momios de ${odds.by.ifBlank { "la casa de apuestas" }}; solo informativos." else "",
                    color = C.Low, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    if (sheet) EventSheet(ev, c, sportsCats) { sheet = false }
}

/** Encabezado grande: los dos equipos, el marcador y el estado. */
@Composable
private fun GameHeader(ev: SpEvent, d: SpGameDetail?, hide: Boolean, compact: Boolean) {
    val (ca, ch) = teamTints(ev.a, ev.h)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(ca.copy(alpha = 0.45f), C.Panel, C.Panel, ch.copy(alpha = 0.45f)))),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderTeam(ev.a, "Visitante", ev, end = false, compact = compact, modifier = Modifier.weight(1f))
            Column(Modifier.widthIn(min = if (compact) 110.dp else 240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!ev.upcoming && !hide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreNum(ev.a, ev, compact)
                        Text("  –  ", color = C.Low, fontSize = if (compact) 22.sp else 34.sp)
                        ScoreNum(ev.h, ev, compact)
                    }
                } else if (ev.upcoming) {
                    Text(com.mauricio.cinecasa.data.TextUtil.hhmm(ev.start), color = C.Hi, fontSize = if (compact) 26.sp else 40.sp, fontWeight = FontWeight.Bold)
                    Text(whenLabel(ev.start).substringBeforeLast(' ') + " · " + countdown(ev.start), color = C.Mid, fontSize = 13.sp)
                } else Text("Marcador oculto", color = C.Mid, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                when {
                    ev.live -> LiveDot(ev.det.ifBlank { "EN VIVO" }, 14)
                    ev.done -> Text(ev.det.ifBlank { "Final" }, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
                    else -> {}
                }
                val sub = listOfNotNull(
                    Sports.leagueByKey[ev.lg]?.name,
                    (d?.tv?.ifEmpty { null } ?: ev.tv.ifEmpty { null })?.joinToString(", "),
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = C.Mid, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                d?.venue?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Low, fontSize = 11.sp, maxLines = 1) }
            }
            HeaderTeam(ev.h, "Local", ev, end = true, compact = compact, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreNum(t: SpTeam, ev: SpEvent, compact: Boolean) {
    val lost = ev.done && !t.win && (ev.h.win || ev.a.win)
    Text(t.sc.ifBlank { "0" }, color = if (lost) C.Low else C.Hi, fontSize = if (compact) 36.sp else 56.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun HeaderTeam(t: SpTeam, role: String, ev: SpEvent, end: Boolean, compact: Boolean, modifier: Modifier) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
            TeamBadge(t, if (compact) 54.dp else 80.dp)
            Spacer(Modifier.height(8.dp))
            Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = if (compact) 15.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                textAlign = if (end) TextAlign.End else TextAlign.Start)
            Text(listOf(t.rec, role).filter { it.isNotBlank() }.joinToString(" · "), color = C.Mid, fontSize = 12.sp)
        }
    }
    Box(modifier, contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) { content() }
}

// ---------------------------------------------------------------- Resumen
private fun LazyListScope.summary(ev: SpEvent, d: SpGameDetail) {
    if (d.line.any { it.ls.isNotEmpty() }) item("ls") { LineScore(ev, d) }
    if (d.leaders.isNotEmpty()) item("lead") { LeadersCard(ev, d) }
    if (d.wp.size > 2) item("wp") {
        FocusCard {
            CardTitle("Probabilidad de ganar")
            WinProb(d.wp, ev)
        }
    }
    if (d.keyPlays.isNotEmpty()) {
        item("kpt") { SectionTitle(if (ev.sp == "soccer") "Momentos clave" else "Anotaciones") }
        itemsIndexed(d.keyPlays.reversed(), key = { i, _ -> "k$i" }) { _, p -> PlayRow(ev, p, highlight = true) }
    }
    if (d.drive.isNotBlank()) item("drive") { FocusCard { CardTitle("Serie actual"); Text(rememberSpanish(d.drive), color = C.Hi, fontSize = 15.sp) } }
}

@Composable
private fun CardTitle(t: String, sub: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Text(t, color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotBlank()) Text("  $sub", color = C.Low, fontSize = 12.sp)
    }
}

@Composable
private fun LineScore(ev: SpEvent, d: SpGameDetail) {
    val n = d.line.maxOf { it.ls.size }
    val reg = when (ev.sp) { "baseball" -> 9; "hockey" -> 3; "soccer" -> 2; else -> 4 }
    fun head(i: Int) = when {
        ev.sp == "soccer" -> if (i == 0) "1T" else if (i == 1) "2T" else "TE"
        i >= reg -> if (ev.sp == "baseball") "${i + 1}" else if (i == reg) "TE" else "TE${i - reg + 1}"
        else -> "${i + 1}"
    }
    val title = when (ev.sp) { "baseball" -> "Carreras por entrada"; "soccer" -> "Goles por tiempo"; "hockey" -> "Goles por periodo"; else -> "Puntos por cuarto" }
    FocusCard {
        CardTitle(title)
        val colW = if (n > 9) 30.dp else 44.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(150.dp))
            (0 until n).forEach { Text(head(it), color = C.Low, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(colW)) }
            Text("T", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        }
        listOf(ev.a, ev.h).forEach { t ->
            val l = d.line.firstOrNull { it.id == t.id } ?: d.line.firstOrNull { it.home == (t === ev.h) }
            val lost = ev.done && !t.win && (ev.h.win || ev.a.win)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(150.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, 28.dp); Spacer(Modifier.width(10.dp))
                    Text(t.n.ifBlank { t.ab }, color = if (lost) C.Mid else C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                (0 until n).forEach { i -> Text(l?.ls?.getOrNull(i).orEmpty(), color = C.Mid, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.width(colW)) }
                Text(l?.sc.orEmpty().ifBlank { t.sc }, color = if (lost) C.Mid else C.Hi, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
            }
        }
    }
}

@Composable
private fun LeadersCard(ev: SpEvent, d: SpGameDetail) {
    FocusCard {
        CardTitle(if (ev.upcoming) "Líderes de la temporada" else "Figuras del partido")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(ev.a, ev.h).forEach { t ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(t, 22.dp); Spacer(Modifier.width(8.dp))
                        Text(t.n, color = C.Mid, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    d.leaders[t.id].orEmpty().forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(C.Panel2), contentAlignment = Alignment.Center) {
                                Text(l.n.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), color = C.Mid, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (l.head.isNotBlank() && !androidx.compose.ui.platform.LocalInspectionMode.current) Img(l.head, Modifier.fillMaxSize(), ContentScale.Crop)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.cat, color = C.Low, fontSize = 11.sp)
                                Text(l.n + if (l.pos.isNotBlank()) "  ${l.pos}" else "", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(l.v, color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gráfica de probabilidad con los colores de cada equipo. */
@Composable
private fun WinProb(points: List<Float>, ev: SpEvent) {
    val (ca, ch) = teamTints(ev.a, ev.h)
    val last = points.last()
    val grid = C.Line; val line = C.Hi
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(ev.h, 22.dp); Spacer(Modifier.width(8.dp))
        Text("${ev.h.n} ${last.toInt()}%", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("${ev.a.n} ${(100 - last).toInt()}%", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp)); TeamBadge(ev.a, 22.dp)
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
        val w = size.width; val h = size.height
        drawRect(ch.copy(alpha = 0.16f), size = androidx.compose.ui.geometry.Size(w, h / 2))
        drawRect(ca.copy(alpha = 0.16f), topLeft = Offset(0f, h / 2), size = androidx.compose.ui.geometry.Size(w, h / 2))
        listOf(0.25f, 0.75f).forEach { f -> drawLine(grid.copy(alpha = 0.4f), Offset(0f, h * f), Offset(w, h * f), 1f) }
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), 2f)
        val p = Path()
        points.forEachIndexed { i, v ->
            val x = w * i / (points.size - 1); val y = h * (1f - v / 100f)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, line, style = Stroke(width = 4f))
    }
    Row {
        Text("Inicio", color = C.Low, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("Arriba: ${ev.h.n} · Abajo: ${ev.a.n}", color = C.Low, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(if (ev.done) "Final" else "Ahora", color = C.Low, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- Previa
private fun LazyListScope.preview(ev: SpEvent, d: SpGameDetail, odds: GOdds?) {
    d.predHome?.let { ph ->
        item("pred") {
            FocusCard {
                CardTitle("Pronóstico", "según ESPN")
                ProbBar(ev, ph)
                odds?.let { o ->
                    val ih = impliedPct(o.mlH.now); val ia = impliedPct(o.mlA.now)
                    if (ih != null && ia != null) Text("Lo que dicen los momios: ${ev.h.n} $ih% · ${ev.a.n} $ia%", color = C.Mid, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    if (odds != null) item("oddsmini") { OddsSummary(ev, odds) }
    if (d.last5.isNotEmpty()) item("form") { FormCard(ev, d) }
    if (d.stats.isNotEmpty()) statsTab(ev, d.stats, "Comparativo de la temporada")
    if (d.h2h.isNotEmpty()) item("h2h") { H2HCard(ev, d) }
    if (d.leaders.isNotEmpty()) item("lead") { LeadersCard(ev, d) }
    item("where") {
        FocusCard {
            CardTitle("Cuándo y dónde")
            Text(whenLabel(ev.start) + " · " + countdown(ev.start), color = C.Hi, fontSize = 15.sp)
            if (d.venue.isNotBlank()) Text(d.venue + if (d.city.isNotBlank()) ", ${d.city}" else "", color = C.Mid, fontSize = 14.sp)
            val tv = d.tv.ifEmpty { ev.tv }
            if (tv.isNotEmpty()) Text("Transmite: ${tv.joinToString(", ")}", color = C.Mid, fontSize = 14.sp)
            d.temp?.let { t -> Text("Clima: ${((t - 32) * 5 / 9)} °C" + (d.rain?.let { " · $it % de lluvia" } ?: ""), color = C.Mid, fontSize = 14.sp) }
        }
    }
    if (d.inj.isNotEmpty()) injuriesTab(ev, d.inj)
}

@Composable
private fun ProbBar(ev: SpEvent, ph: Float) {
    val (ca, ch) = teamTints(ev.a, ev.h)
    val pa = 100f - ph
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        TeamBadge(ev.a, 26.dp); Spacer(Modifier.width(8.dp))
        Text("${ev.a.n} ${pa.toInt()}%", color = C.Hi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("${ev.h.n} ${ph.toInt()}%", color = C.Hi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp)); TeamBadge(ev.h, 26.dp)
    }
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        Box(Modifier.weight(pa.coerceIn(3f, 97f)).fillMaxSize().background(ca))
        Spacer(Modifier.width(3.dp))
        Box(Modifier.weight(ph.coerceIn(3f, 97f)).fillMaxSize().background(ch))
    }
}

@Composable
private fun FormCard(ev: SpEvent, d: SpGameDetail) {
    FocusCard {
        CardTitle("Cómo llegan", "últimos 5 partidos")
        listOf(ev.a, ev.h).forEach { t ->
            val l = d.last5[t.id].orEmpty()
            if (l.isEmpty()) return@forEach
            val g = l.count { it.r == "G" }; val p = l.count { it.r == "P" }; val e = l.count { it.r == "E" }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(170.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, 26.dp); Spacer(Modifier.width(8.dp))
                    Column {
                        Text(t.n, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("$g G · $p P" + if (e > 0) " · $e E" else "", color = C.Low, fontSize = 11.sp)
                    }
                }
                l.forEach { f -> FormChip(f) }
            }
        }
    }
}

@Composable
private fun FormChip(f: GForm) {
    val bg = when (f.r) { "G" -> C.Good; "P" -> Live; else -> C.Low }
    Column(Modifier.padding(end = 8.dp).width(66.dp).background(C.Panel2, RoundedCornerShape(8.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(f.r.ifBlank { "·" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("${f.at} ${f.o}", color = C.Mid, fontSize = 11.sp, maxLines = 1)
        Text(f.s, color = C.Hi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun H2HCard(ev: SpEvent, d: SpGameDetail) {
    FocusCard {
        CardTitle("Enfrentamientos recientes")
        d.h2h.reversed().forEach { m ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (m.d > 0) java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es", "MX")).format(java.util.Date(m.d)) else "", color = C.Low, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                Text(m.a, color = if (m.w == "a") C.Hi else C.Mid, fontSize = 15.sp, fontWeight = if (m.w == "a") FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(56.dp))
                Text("${m.`as`} – ${m.hs}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                Text(m.h, color = if (m.w == "h") C.Hi else C.Mid, fontSize = 15.sp, fontWeight = if (m.w == "h") FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ---------------------------------------------------------------- Estadísticas
private fun LazyListScope.statsTab(ev: SpEvent, stats: List<GStat>, title: String) {
    item("st_t") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            TeamBadge(ev.a, 24.dp); Spacer(Modifier.width(8.dp))
            Text(ev.a.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(title, color = C.Mid, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(ev.h.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp)); TeamBadge(ev.h, 24.dp)
        }
    }
    itemsIndexed(stats, key = { i, _ -> "st_$i" }) { _, s -> StatCompare(ev, s) }
}

@Composable
private fun StatCompare(ev: SpEvent, s: GStat) {
    val (ca, ch) = teamTints(ev.a, ev.h)
    val a = if (s.an.isNaN()) 0.0 else s.an; val h = if (s.hn.isNaN()) 0.0 else s.hn
    val aBetter = if (s.low) a < h else a > h
    val hBetter = if (s.low) h < a else h > a
    FocusCard(pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.a.ifBlank { "—" }, color = if (aBetter) C.Hi else C.Mid, fontSize = 18.sp, fontWeight = if (aBetter) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(110.dp))
            Text(s.l, color = C.Mid, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(s.h.ifBlank { "—" }, color = if (hBetter) C.Hi else C.Mid, fontSize = 18.sp, fontWeight = if (hBetter) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
        }
        Spacer(Modifier.height(8.dp))
        val tot = (a + h).takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(8.dp)) {
            // Cada lado crece desde el centro según su parte del total
            Row(Modifier.weight(1f).fillMaxSize(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.fillMaxWidth((a / tot).toFloat().coerceIn(0.02f, 1f)).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(if (aBetter) ca else ca.copy(alpha = 0.4f)))
            }
            Spacer(Modifier.width(4.dp))
            Row(Modifier.weight(1f).fillMaxSize()) {
                Box(Modifier.fillMaxWidth((h / tot).toFloat().coerceIn(0.02f, 1f)).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(if (hBetter) ch else ch.copy(alpha = 0.4f)))
            }
        }
    }
}

// ---------------------------------------------------------------- Jugadores y alineaciones
private fun LazyListScope.playersTab(ev: SpEvent, d: SpGameDetail) {
    listOf(ev.a, ev.h).forEach { t ->
        d.players[t.id].orEmpty().forEachIndexed { i, g -> item("pl_${t.id}_$i") { PlayersTable(t, g) } }
    }
}

@Composable
private fun PlayersTable(t: SpTeam, g: GPlayerGroup) {
    FocusCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            TeamBadge(t, 26.dp); Spacer(Modifier.width(10.dp))
            Text(t.n, color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("  ·  ${g.g}", color = C.Mid, fontSize = 15.sp)
        }
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("Jugador", color = C.Low, fontSize = 12.sp, modifier = Modifier.weight(1f))
            g.cols.forEach { Text(it, color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(70.dp)) }
        }
        g.rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().then(if (i % 2 == 0) Modifier.background(C.Panel2.copy(alpha = 0.45f), RoundedCornerShape(6.dp)) else Modifier).padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.num, color = C.Low, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                Text(r.n, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (r.pos.isNotBlank()) Text(r.pos, color = C.Low, fontSize = 11.sp, modifier = Modifier.width(34.dp))
                r.v.forEachIndexed { k, v ->
                    Text(v, color = if (k == 0) C.Hi else C.Mid, fontSize = 14.sp, fontWeight = if (k == 0) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
                }
            }
        }
    }
}

private fun LazyListScope.lineupsTab(ev: SpEvent, d: SpGameDetail) {
    item("lu") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(ev.a, ev.h).forEach { t ->
                val l = d.lineups[t.id]
                FocusCard(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        TeamBadge(t, 26.dp); Spacer(Modifier.width(10.dp))
                        Text(t.n, color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (l != null && l.formation.isNotBlank()) Text(l.formation, color = C.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (l == null) Text("Sin alineación todavía.", color = C.Low, fontSize = 13.sp)
                    l?.starters?.forEach { p ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.num, color = C.Low, fontSize = 13.sp, modifier = Modifier.width(30.dp))
                            Text(p.n, color = C.Hi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            p.ev.forEach { e -> EventMark(e) }
                            Text(p.pos, color = C.Low, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventMark(e: String) {
    when (e) {
        "gol" -> Text(" GOL ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp).background(C.Good, RoundedCornerShape(4.dp)))
        "amarilla" -> Box(Modifier.padding(start = 4.dp).size(9.dp, 13.dp).background(Color(0xFFFFD60A), RoundedCornerShape(2.dp)))
        "roja" -> Box(Modifier.padding(start = 4.dp).size(9.dp, 13.dp).background(Live, RoundedCornerShape(2.dp)))
        "cambio" -> Text(" ⇄", color = C.Low, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- Jugadas
private fun LazyListScope.playsTab(ev: SpEvent, plays: List<SpPlay>, drive: String) {
    if (drive.isNotBlank()) item("drv") { FocusCard { CardTitle("Serie actual"); Text(rememberSpanish(drive), color = C.Hi, fontSize = 15.sp) } }
    itemsIndexed(plays, key = { i, _ -> "p$i" }) { _, p -> PlayRow(ev, p, highlight = p.scoring) }
}

@Composable
private fun PlayRow(ev: SpEvent, p: SpPlay, highlight: Boolean) {
    val team = when (p.team) { ev.h.id -> ev.h; ev.a.id -> ev.a; else -> null }
    val text = if (ev.sp == "soccer") p.t else rememberSpanish(p.t)
    FocusCard(pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).background(if (highlight) (team?.let { teamTint(it) } ?: C.Accent) else Color.Transparent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(64.dp)) {
                Text(p.clk, color = C.Hi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (p.per > 0) Text(periodShort(ev.sp, p.per), color = C.Low, fontSize = 11.sp)
            }
            if (team != null) { TeamBadge(team, 26.dp); Spacer(Modifier.width(10.dp)) }
            Text(text, color = if (highlight) C.Hi else C.Mid, fontSize = 14.sp, fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (p.kind == "amarilla" || p.kind == "roja") EventMark(p.kind)
            if (p.score.isNotBlank()) Text("  " + p.score.replace("-", " – "), color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun periodShort(sp: String, n: Int) = when (sp) {
    "basketball", "football" -> if (n > 4) "TE" else "${n}º cuarto"
    "hockey" -> if (n > 3) "TE" else "${n}º periodo"
    "soccer" -> "${n}º tiempo"
    "baseball" -> "${n}ª entrada"
    else -> "$n"
}

// ---------------------------------------------------------------- Apuestas
@Composable
private fun OddsSummary(ev: SpEvent, o: GOdds) {
    FocusCard {
        CardTitle("Momios", o.by)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OddsBox("Ganador", Modifier.weight(1f)) {
                OddsLine(ev.a, am(o.mlA.now), o.favA); OddsLine(ev.h, am(o.mlH.now), o.favH)
                if (o.mlD.isNotBlank()) Text("Empate ${am(o.mlD)}", color = C.Mid, fontSize = 13.sp)
            }
            if (o.spH.now.isNotBlank()) OddsBox("Hándicap", Modifier.weight(1f)) {
                OddsLine(ev.a, o.spA.now.replace("-", "−"), false, am(o.spA.oddsNow)); OddsLine(ev.h, o.spH.now.replace("-", "−"), false, am(o.spH.oddsNow))
            }
            if (o.ouNow.isNotBlank()) OddsBox("Total de puntos", Modifier.weight(1f)) {
                Text("Más de ${o.ouNow}  ${am(o.over)}", color = C.Hi, fontSize = 14.sp)
                Text("Menos de ${o.ouNow}  ${am(o.under)}", color = C.Hi, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun OddsBox(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(C.Panel2.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun OddsLine(t: SpTeam, main: String, fav: Boolean, extra: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, 20.dp); Spacer(Modifier.width(8.dp))
        Text(t.ab.ifBlank { t.n }, color = C.Mid, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        Text(main, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (extra.isNotBlank() && extra != "—") Text("  $extra", color = C.Low, fontSize = 12.sp)
        if (fav) Text("  favorito", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun LazyListScope.betsTab(ev: SpEvent, d: SpGameDetail, o: GOdds) {
    item("ml") {
        FocusCard {
            CardTitle("Ganador del partido (moneyline)")
            BetRow(ev.a, o.mlA.open, o.mlA.now, o.favA)
            BetRow(ev.h, o.mlH.open, o.mlH.now, o.favH)
            if (o.mlD.isNotBlank()) Text("Empate: ${am(o.mlD)}" + (impliedPct(o.mlD)?.let { "  ($it %)" } ?: ""), color = C.Mid, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Text("El porcentaje es la probabilidad que implica el momio.", color = C.Low, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (o.spH.now.isNotBlank()) item("sp") {
        FocusCard {
            CardTitle("Hándicap (spread)")
            listOf(ev.a to o.spA, ev.h to o.spH).forEach { (t, s) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, 26.dp); Spacer(Modifier.width(10.dp))
                    Text(t.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(s.now.replace("-", "−"), color = C.Hi, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
                    Text(am(s.oddsNow), color = C.Mid, fontSize = 14.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                    Text(if (s.open.isNotBlank()) "abrió ${s.open.replace("-", "−")}" else "", color = C.Low, fontSize = 12.sp, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
    if (o.ouNow.isNotBlank()) item("ou") {
        FocusCard {
            CardTitle(if (ev.sp == "soccer" || ev.sp == "hockey") "Total de goles (más/menos)" else if (ev.sp == "baseball") "Total de carreras (más/menos)" else "Total de puntos (más/menos)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.ouNow, color = C.Hi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Más  ${am(o.over)}", color = C.Hi, fontSize = 15.sp)
                    Text("Menos  ${am(o.under)}", color = C.Hi, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                val op = o.ouOpen.toDoubleOrNull(); val nw = o.ouNow.toDoubleOrNull()
                if (op != null && nw != null && op != nw) Text("Abrió en ${o.ouOpen}: " + if (nw > op) "subió" else "bajó", color = if (nw > op) C.Good else Live, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    item("move") {
        val notes = buildList {
            if ((o.favOpenH && o.favA) || (o.favOpenA && o.favH)) add("Cambió el favorito: abrió ${if (o.favOpenH) ev.h.n else ev.a.n} y ahora es ${if (o.favH) ev.h.n else ev.a.n}.")
            fun moveTxt(t: SpTeam, open: String, now: String) {
                val a = open.replace("+", "").toIntOrNull(); val b = now.replace("+", "").toIntOrNull()
                if (a != null && b != null && a != b) add("${t.n}: de ${am(open)} a ${am(now)} (" + (if ((impliedPct(now) ?: 0) > (impliedPct(open) ?: 0)) "más apostado" else "menos apostado") + ").")
            }
            moveTxt(ev.a, o.mlA.open, o.mlA.now); moveTxt(ev.h, o.mlH.open, o.mlH.now)
            d.predHome?.let { ph ->
                val ih = impliedPct(o.mlH.now)
                if (ih != null) add("Pronóstico ESPN: ${ev.h.n} ${ph.toInt()} % · momio: ${ih} %.")
            }
        }
        if (notes.isNotEmpty()) FocusCard {
            CardTitle("Cómo se ha movido la línea")
            notes.forEach { Text("• $it", color = C.Hi, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
    if (d.ats.isNotEmpty()) item("ats") {
        FocusCard {
            CardTitle("Contra la línea esta temporada")
            listOf(ev.a, ev.h).forEach { t ->
                d.ats[t.id]?.let { recs -> Text("${t.n}: " + recs.joinToString(" · ") { (k, v) -> "${atsEs(k)} $v" }, color = C.Hi, fontSize = 14.sp) }
            }
        }
    }
    if (d.last5.isNotEmpty()) item("form2") { FormCard(ev, d) }
}

private fun atsEs(k: String) = when (k.lowercase()) {
    "ats" -> "Contra la línea"; "home" -> "En casa"; "away", "road" -> "De visita"; "favorite" -> "De favorito"; "underdog" -> "De no favorito"; else -> k
}

@Composable
private fun BetRow(t: SpTeam, open: String, now: String, fav: Boolean) {
    val pct = impliedPct(now)
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, 30.dp); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (fav) Text("Favorito", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(am(now), color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
        Text(pct?.let { "$it %" } ?: "", color = C.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
        Text(if (open.isNotBlank() && open != now) "abrió ${am(open)}" else "", color = C.Low, fontSize = 12.sp, modifier = Modifier.width(100.dp), textAlign = TextAlign.End)
    }
    if (pct != null) Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.Panel2)) {
        Box(Modifier.fillMaxWidth(pct / 100f).fillMaxSize().background(teamTint(t)))
    }
}

// ---------------------------------------------------------------- Lesionados
private fun LazyListScope.injuriesTab(ev: SpEvent, inj: Map<String, List<SpInjury>>) {
    listOf(ev.a, ev.h).forEach { t ->
        val l = inj[t.id].orEmpty()
        if (l.isEmpty()) return@forEach
        item("inj_${t.id}") {
            FocusCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    TeamBadge(t, 26.dp); Spacer(Modifier.width(10.dp))
                    Text("Lesionados · ${t.n}", color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                l.forEach { i ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(i.n, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(listOf(i.pos, injuryPartEs(i.d)).filter { it.isNotBlank() }.joinToString(" · "), color = C.Low, fontSize = 13.sp, modifier = Modifier.width(170.dp))
                        StatusChip(i.st)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(st: String) {
    val col = when {
        st == "Fuera" || st.startsWith("Lista") || st == "Suspendido" -> Live
        st == "Dudoso" || st == "En duda" -> C.Warn
        st == "Probable" || st.startsWith("Día") -> C.Good
        else -> C.Low
    }
    Text(st, color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.width(140.dp).background(col.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

fun injuryPartEs(s: String): String = when (s.lowercase()) {
    "knee" -> "Rodilla"; "ankle" -> "Tobillo"; "hamstring" -> "Isquiotibial"; "shoulder" -> "Hombro"; "back" -> "Espalda"; "foot" -> "Pie"
    "hip" -> "Cadera"; "groin" -> "Ingle"; "calf" -> "Pantorrilla"; "concussion" -> "Conmoción"; "illness" -> "Enfermedad"; "wrist" -> "Muñeca"
    "hand" -> "Mano"; "neck" -> "Cuello"; "elbow" -> "Codo"; "thumb" -> "Pulgar"; "toe" -> "Dedo del pie"; "quadriceps", "quad" -> "Cuádriceps"
    "achilles" -> "Aquiles"; "rest" -> "Descanso"; "personal" -> "Personal"; "chest" -> "Pecho"; "rib", "ribs" -> "Costillas"; "abdomen" -> "Abdomen"
    "head" -> "Cabeza"; "finger" -> "Dedo"; "leg" -> "Pierna"; "lower body" -> "Tren inferior"; "upper body" -> "Tren superior"
    else -> s
}
