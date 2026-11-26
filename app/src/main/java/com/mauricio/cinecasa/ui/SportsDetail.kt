package com.mauricio.cinecasa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Catalog
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.EpgItem
import com.mauricio.cinecasa.data.Profiles
import com.mauricio.cinecasa.data.Quiniela
import com.mauricio.cinecasa.data.Recorder
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SpEvent
import com.mauricio.cinecasa.data.SpGame
import com.mauricio.cinecasa.data.SpTeam
import com.mauricio.cinecasa.data.Sports
import com.mauricio.cinecasa.data.SportsCats
import com.mauricio.cinecasa.data.Teams
import com.mauricio.cinecasa.data.TextUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ======================================================================
// Opciones de un partido
// ======================================================================
@Composable
fun EventSheet(ev: SpEvent, c: Catalog, sportsCats: Set<String>, onDismiss: () -> Unit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    var chans by remember(ev.key) { mutableStateOf(Sports.channelsFor(ev, c, sportsCats)) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var recDlg by remember { mutableStateOf(false) }
    val recs by Recorder.list.collectAsState()
    val rec = recs.firstOrNull { it.evKey == ev.key && (it.scheduled || it.recording) }
    val teams by Teams.list.collectAsState()
    val rv by Teams.revealed.collectAsState()
    val ns by Teams.noSpoil.collectAsState()
    val hide = remember(ev, rv, ns) { Teams.hidden(ev) }
    val me by Profiles.current.collectAsState()
    val picks by Quiniela.picks.collectAsState()
    val myPick = me?.let { p -> picks["${ev.key}|${p.id}"] }
    val pinned by SportsPin.key.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.cardWidth(600.dp).heightIn(max = 640.dp).background(C.Panel, RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Sports.leagueByKey[ev.lg]?.name ?: ev.lg, color = C.Low, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                if (ev.live) LiveDot(ev.det.ifBlank { "EN VIVO" }, 13) else Text(statusText(ev) + if (ev.upcoming) " · ${countdown(ev.start)}" else "", color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (ev.name.isNotBlank()) Text(ev.name, color = C.Hi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            listOf(ev.a, ev.h).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.rec.isNotBlank()) Text(t.rec + "   ", color = C.Low, fontSize = 12.sp)
                    if (!ev.upcoming && !hide) Text(t.sc, color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            oddsLine(ev)?.let { if (ev.upcoming) Text(it, color = C.Mid, fontSize = 13.sp) }
            if (hide) ActionButton("Mostrar marcador", { Teams.reveal(ev.key) })

            // ---------- Dónde verlo ----------
            Text("Dónde verlo", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (chans.isEmpty()) {
                Text(
                    if (scanned) "No lo encontré en la guía de tus canales de deportes. Búscalo en «Canales de deportes»."
                    else "No encontré el canal por su nombre." + (if (ev.tv.isNotEmpty()) " Lo transmite: ${ev.tv.joinToString(", ")}." else ""),
                    color = C.Mid, fontSize = 13.sp,
                )
            }
            Wrap(gap = 10.dp) {
                chans.take(5).forEachIndexed { i, ch ->
                    ActionButton("Ver en ${ch.name}", { onDismiss(); nav.playLive(chans, i) },
                        if (i == 0) Modifier.focusRequester(fr) else Modifier, primary = i == 0, icon = Icons.Filled.PlayArrow)
                }
                if (!scanned) ActionButton(if (scanning) "Buscando en la guía…" else "Buscar en la guía", {
                    if (!scanning) {
                        scanning = true
                        scope.launch {
                            val more = Sports.scanEpg(ev, c, sportsCats)
                            chans = (more + chans).distinctBy { it.id }
                            scanning = false; scanned = true
                        }
                    }
                }, if (chans.isEmpty()) Modifier.focusRequester(fr) else Modifier)
                ActionButton("Canales de deportes", { onDismiss(); nav.go(Route.SportsLive) })
            }

            // ---------- Grabar ----------
            Text("Grabar", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (rec != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(12.dp), tint = Live)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (rec.recording) "Grabando en ${rec.chName} · ${bytes(rec.bytes)}" else "Se grabará de ${whenLabel(rec.start)} a ${TextUtil.hhmm(rec.end)} en ${rec.chName}",
                        color = C.Hi, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                }
                Wrap(gap = 10.dp) {
                    if (rec.recording) ActionButton("Ver desde el inicio", { onDismiss(); playRecording(rec, nav) }, icon = Icons.Filled.PlayArrow)
                    ActionButton(if (rec.recording) "Detener grabación" else "Cancelar grabación", { Recorder.stop(rec.id) })
                }
            } else if (!ev.done) {
                ActionButton("Grabar este partido", { recDlg = true }, icon = Icons.Filled.FiberManualRecord)
            } else Text("El partido ya terminó.", color = C.Mid, fontSize = 13.sp)

            // ---------- Quiniela ----------
            val p = me
            if (ev.upcoming && p != null && !p.guest) {
                Text("Quiniela de la casa: ¿quién gana?", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Wrap(gap = 8.dp) {
                    Pill(ev.a.n, myPick?.pick == "a", { Quiniela.choose(ev, p.id, "a") }, icon = if (myPick?.pick == "a") Icons.Filled.Check else null)
                    if (ev.sp == "soccer") Pill("Empate", myPick?.pick == "d", { Quiniela.choose(ev, p.id, "d") }, icon = if (myPick?.pick == "d") Icons.Filled.Check else null)
                    Pill(ev.h.n, myPick?.pick == "h", { Quiniela.choose(ev, p.id, "h") }, icon = if (myPick?.pick == "h") Icons.Filled.Check else null)
                }
            }

            // ---------- Más ----------
            Text("Más", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Wrap(gap = 10.dp) {
                if (ev.lg != "lmp" && ev.lg != "ufc") ActionButton("Detalle del partido", { onDismiss(); nav.go(Route.Game(ev)) })
                if (ev.name.isBlank()) listOf(ev.a, ev.h).forEach { t ->
                    ActionButton("Ficha de ${t.n}", { onDismiss(); nav.go(Route.Team(ev.lg, t)) })
                }
                if (ev.name.isBlank()) listOf(ev.a, ev.h).forEach { t ->
                    val k = "${ev.lg}:${t.id}"
                    val on = teams.any { it.key == k }
                    ActionButton(if (on) "Siguiendo a ${t.n}" else "Seguir a ${t.n}", { Teams.toggle(ev.lg, t) }, icon = if (on) Icons.Filled.Star else Icons.Filled.StarBorder)
                }
                if (ev.live) ActionButton(if (pinned == ev.key) "Quitar marcador de la esquina" else "Marcador en la esquina", {
                    SportsPin.key.value = if (pinned == ev.key) null else ev.key
                })
            }
            ActionButton("Cerrar", onDismiss)
        }
    }
    LaunchedEffect(Unit) { delay(120); runCatching { fr.requestFocus() } }
    if (recDlg) RecordEventDialog(ev, chans, c, sportsCats) { recDlg = false }
}

fun playRecording(r: Recorder.Rec, nav: Nav) {
    val f = java.io.File(r.path)
    if (!f.exists()) return
    val item = com.mauricio.cinecasa.data.PlayItem(
        key = "rec:${r.id}", kind = "rec", id = r.id, seriesId = 0, url = android.net.Uri.fromFile(f).toString(),
        title = r.title, subtitle = "Grabado en ${r.chName} · ${whenLabel(r.start)}", image = r.image,
    )
    nav.play(listOf(item), 0, if (r.recording) 0 else r.pos)
}

// ======================================================================
// Grabar un partido
// ======================================================================
@Composable
fun RecordEventDialog(ev: SpEvent, candidates: List<Channel>, c: Catalog, sportsCats: Set<String>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    var ch by remember { mutableStateOf(candidates.firstOrNull()) }
    var choosing by remember { mutableStateOf(candidates.isEmpty()) }
    var extra by remember { mutableStateOf(30) }
    var done by remember { mutableStateOf<String?>(null) }
    val start = maxOf(System.currentTimeMillis(), ev.start - 5 * 60_000)
    val end = (if (ev.live) maxOf(ev.start + ev.durationMs, System.currentTimeMillis() + 90 * 60_000) else ev.start + ev.durationMs) + extra * 60_000L
    val need = Recorder.estimate(end - start)
    val free = Recorder.freeBytes()
    val pool = remember(c, sportsCats) { c.visChannels.filter { it.cat in sportsCats }.ifEmpty { c.visChannels }.take(400) }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.cardWidth(580.dp).heightIn(max = 640.dp).background(C.Panel, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Grabar ${ev.title}", color = C.Hi, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            val msg = done
            if (msg != null) {
                Text(msg, color = C.Mid, fontSize = 15.sp)
                ActionButton("Listo", onDone, Modifier.focusRequester(fr), primary = true)
                LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
                return@Column
            }
            if (choosing) {
                Text("Elige el canal donde pasa:", color = C.Mid, fontSize = 14.sp)
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(candidates + pool.filter { p -> candidates.none { it.id == p.id } }, key = { it.id }) { k ->
                        ChannelRow(k, false, if (k == (candidates.firstOrNull() ?: pool.firstOrNull())) Modifier.focusRequester(fr) else Modifier, selected = k.id == ch?.id) {
                            ch = k; choosing = false
                        }
                    }
                }
                ActionButton("Cancelar", onDone)
            } else {
                val sel = ch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Canal: ", color = C.Low, fontSize = 14.sp)
                    Text(sel?.name ?: "—", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Pill("Cambiar canal", false, { choosing = true })
                }
                Text("Por si se alarga:", color = C.Low, fontSize = 13.sp)
                Wrap(gap = 8.dp) {
                    listOf(0 to "Sin extra", 30 to "+30 min", 60 to "+1 hora", 90 to "+1 h 30").forEach { (m, t) -> Pill(t, extra == m, { extra = m }) }
                }
                Text(
                    "Empieza ${whenLabel(start)}${if (!ev.live) " (5 min antes)" else ""} y termina ${TextUtil.hhmm(end)}.",
                    color = C.Hi, fontSize = 14.sp,
                )
                Text("Ocupará unos ${bytes(need)}; tienes ${bytes(free)} libres.", color = if (free < need) C.Warn else C.Mid, fontSize = 13.sp)
                if (free < need) Text("Puede no alcanzar el espacio: se guardará hasta donde quepa. Borra grabaciones o descargas viejas.", color = C.Warn, fontSize = 12.sp)
                Text("Deja la tele conectada a la corriente y al internet (la pantalla puede estar apagada). Mientras graba, usa una de las pantallas de tu cuenta del proveedor.", color = C.Low, fontSize = 12.sp)
                if (!Recorder.canExact()) {
                    Text("Para que empiece a la hora exacta, Android pide permitir «Alarmas y recordatorios» para CineCasa.", color = C.Warn, fontSize = 12.sp)
                    ActionButton("Permitir alarmas", { Recorder.exactSettingsIntent()?.let { runCatching { ctx.startActivity(it) } } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(if (start <= System.currentTimeMillis() + 20_000) "Grabar ahora" else "Programar grabación", {
                        val k = sel ?: return@ActionButton
                        val r = Recorder.schedule(k, ev.title, start, end, ev.key, "")
                        done = if (r.start <= System.currentTimeMillis() + 20_000) "Ya está grabando. Puedes verlo desde el inicio en Descargas → Grabaciones."
                        else "Listo. Se grabará ${whenLabel(r.start)} en ${k.name}. Cuando llegues, lo encuentras en Descargas → Grabaciones (o en Deportes, en las opciones del partido)."
                    }, Modifier.focusRequester(fr), primary = true, icon = Icons.Filled.FiberManualRecord)
                    ActionButton("Cancelar", onDone)
                }
            }
        }
    }
    LaunchedEffect(choosing) { delay(120); runCatching { fr.requestFocus() } }
}

/** Grabar un canal: ahora mismo o un programa de la guía. */
@Composable
fun RecordChannelDialog(ch: Channel, onDone: () -> Unit) {
    val fr = remember { FocusRequester() }
    val epg = rememberEpg(ch, 0)
    var done by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.cardWidth(560.dp).heightIn(max = 640.dp).background(C.Panel, RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Grabar ${ch.name}", color = C.Hi, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            val msg = done
            if (msg != null) {
                Text(msg, color = C.Mid, fontSize = 15.sp)
                ActionButton("Listo", onDone, Modifier.focusRequester(fr), primary = true)
                return@Column
            }
            fun go(title: String, s: Long, e: Long) {
                val r = Recorder.schedule(ch, title, s, e)
                done = if (r.start <= System.currentTimeMillis() + 20_000) "Grabando hasta las ${TextUtil.hhmm(r.end)}. Está en Descargas → Grabaciones." else "Listo. Se grabará ${whenLabel(r.start)}. Está en Descargas → Grabaciones."
            }
            val now = System.currentTimeMillis()
            Text("Desde ahora", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Wrap(gap = 8.dp) {
                listOf(30, 60, 120, 180).forEachIndexed { i, m ->
                    ActionButton(if (m < 60) "$m min" else "${m / 60} h", { go("${ch.name} · ${whenLabel(now)}", now, now + m * 60_000L) }, if (i == 1) Modifier.focusRequester(fr) else Modifier)
                }
            }
            if (epg.isNotEmpty()) {
                Text("De la guía", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                epg.take(5).forEachIndexed { i, e: EpgItem ->
                    val s = if (i == 0) now else e.start - 2 * 60_000
                    ActionButton((if (i == 0) "Lo que queda de: " else "${whenLabel(e.start)}  ") + e.title, { go(e.title, s, e.end + 5 * 60_000) }, Modifier.fillMaxWidth())
                }
            }
            Text("Ocupa unos ${bytes(Recorder.BYTES_PER_HOUR)} por hora; tienes ${bytes(Recorder.freeBytes())} libres. Mientras graba, usa una de las pantallas de tu cuenta.", color = C.Low, fontSize = 12.sp)
            if (!Recorder.canExact()) ActionButton("Permitir alarmas (para grabar a la hora exacta)", { Recorder.exactSettingsIntent()?.let { runCatching { ctx.startActivity(it) } } })
            ActionButton("Cancelar", onDone)
        }
    }
    LaunchedEffect(done) { delay(120); runCatching { fr.requestFocus() } }
}

// ======================================================================
// Ficha del equipo
// ======================================================================
private fun gameToEvent(lg: String, me: SpTeam, g: SpGame): SpEvent {
    val sp = Sports.leagueByKey[lg]?.sp ?: ""
    val us = me.copy(sc = g.us, win = g.res == "G"); val them = g.opp.copy(sc = g.them, win = g.res == "P")
    return SpEvent(g.id, lg, sp, "", g.start, g.st, "", if (g.home) us else them, if (g.home) them else us, g.tv, null, "", null)
}

@Composable
fun TeamScreen(lg: String, team: SpTeam) {
    val page by produceState<com.mauricio.cinecasa.data.SpTeamPage?>(null, lg, team.id) { value = Sports.team(lg, team.id) }
    val table by produceState<com.mauricio.cinecasa.data.SpTable?>(null, lg) { value = if (lg == "lmp" || lg == "ufc") null else Sports.table(lg) }
    TeamBody(lg, team, page, table)
}

@Composable
fun TeamBody(lg: String, team: SpTeam, page: com.mauricio.cinecasa.data.SpTeamPage?, table: com.mauricio.cinecasa.data.SpTable?) {
    val c by Repo.catalog.collectAsState()
    val board = rememberBoard()
    val teams by Teams.list.collectAsState()
    val ns by Teams.noSpoil.collectAsState()
    val key = "$lg:${team.id}"
    val following = teams.any { it.key == key }
    val noSpoil = key in ns
    val sportsCats = remember(c) { SportsCats.ids(c) }
    var sheet by remember { mutableStateOf<SpEvent?>(null) }
    val fr = remember { FocusRequester() }
    val compact = LocalCompact.current
    val side = if (compact) 16.dp else 28.dp
    val t = page?.team?.let { it.copy(logo = it.logo.ifBlank { team.logo }, col = it.col.ifBlank { team.col }) } ?: team

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, top = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("head") {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(teamTint(t).copy(alpha = 0.5f), C.Panel)))) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, if (compact) 64.dp else 96.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(listOfNotNull(Sports.leagueByKey[lg]?.name, page?.standing?.ifBlank { null }).joinToString(" · "), color = C.Mid, fontSize = 15.sp)
                        val p = page
                        if (p != null && !noSpoil) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (p.rec.isNotBlank()) StatPill("Récord", p.rec)
                            if (p.home.isNotBlank()) StatPill("En casa", p.home)
                            if (p.away.isNotBlank()) StatPill("De visita", p.away)
                            if (p.streak.isNotBlank()) StatPill("Racha", streakEs(p.streak))
                        }
                    }
                }
            }
        }
        item("actions") {
            Wrap(gap = 10.dp) {
                ActionButton(if (following) "Siguiendo" else "Seguir equipo", { Teams.toggle(lg, t) }, Modifier.focusRequester(fr), primary = !following, icon = if (following) Icons.Filled.Star else Icons.Filled.StarBorder)
                Pill(if (noSpoil) "Sin marcadores: activado" else "Sin marcadores", noSpoil, { Teams.setNoSpoil(key, !noSpoil) })
            }
        }
        if (noSpoil) item("nsp") { Text("Los marcadores de este equipo quedan ocultos hasta que tú los pidas. Ideal si lo vas a ver grabado.", color = C.Low, fontSize = 12.sp) }
        val p = page
        if (p == null) item("load") { Spinner() }
        else {
            board.events.firstOrNull { it.live && it.has(key) }?.let { live ->
                item("live") { Column { SectionTitle("En vivo ahora"); ScoreCard(live, onFocused = {}) { sheet = live } } }
            }
            val liveId = board.events.firstOrNull { it.live && it.has(key) }?.id
            val upcoming = p.next.filter { it.id != liveId && it.st != "in" }
            if (upcoming.isNotEmpty()) item("next") {
                Column {
                    SectionTitle("Próximos partidos")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(upcoming, key = { it.id }) { g ->
                            val ev = board.events.firstOrNull { it.lg == lg && it.id == g.id } ?: gameToEvent(lg, t, g)
                            ScoreCard(ev, channel = g.tv.joinToString(" · ").ifBlank { null }) { sheet = ev }
                        }
                    }
                }
            }
            if (p.past.isNotEmpty()) {
                item("pastT") { SectionTitle("Últimos resultados", Modifier.padding(top = 6.dp)) }
                if (noSpoil) item("pastH") { FocusCard { Text("Ocultos (sin marcadores activado).", color = C.Low, fontSize = 13.sp) } }
                else items(p.past.reversed(), key = { "past" + it.id }) { g ->
                    val ev = board.events.firstOrNull { it.lg == lg && it.id == g.id } ?: gameToEvent(lg, t, g)
                    FocusCard(pad = 12.dp, onClick = { if (lg != "lmp") sheet = ev }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ResultChip(g.res)
                            Spacer(Modifier.width(12.dp))
                            Text(whenLabel(g.start).substringBeforeLast(' '), color = C.Low, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                            Text(if (g.home) "vs" else "en", color = C.Low, fontSize = 14.sp, modifier = Modifier.width(30.dp))
                            TeamBadge(g.opp, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(g.opp.full.ifBlank { g.opp.n }, color = C.Hi, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${g.us} – ${g.them}", color = C.Hi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item("injT") { SectionTitle("Lesionados", Modifier.padding(top = 6.dp)) }
            if (p.inj.isEmpty()) item("injE") { FocusCard { Text(if (lg == "lmp") "Esta liga no publica reporte de lesionados." else "Sin reporte de lesionados por ahora.", color = C.Low, fontSize = 13.sp) } }
            else items(p.inj.size, key = { "inj$it" }) { ix ->
                val i = p.inj[ix]
                FocusCard(pad = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(i.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(listOf(i.pos, injuryPartEs(i.d)).filter { it.isNotBlank() }.joinToString(" · "), color = C.Low, fontSize = 13.sp, modifier = Modifier.width(190.dp), maxLines = 1)
                        StatusChip(i.st)
                    }
                }
            }
            if (p.leaders.isNotEmpty()) item("lead") {
                FocusCard {
                    Text("Líderes del equipo", color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        p.leaders.forEach { l ->
                            Column(Modifier.weight(1f).background(C.Panel2.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(12.dp)) {
                                Text(leaderEs(l.cat), color = C.Low, fontSize = 12.sp)
                                Text(l.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(l.v, color = C.Accent, fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
            table?.let { tb ->
                val g = tb.groups.firstOrNull { gr -> gr.second.any { it.id == team.id } } ?: tb.groups.firstOrNull()
                if (g != null) {
                    item("tableT") { SectionTitle("Tabla · ${g.first}", Modifier.padding(top = 6.dp)) }
                    tableItems("tb", tb.cols, g.second, team.id)
                }
            }
            if (p.news.isNotEmpty()) {
                item("newsT") { SectionTitle("Noticias", Modifier.padding(top = 6.dp)) }
                items(p.news.size, key = { "n$it" }) { nx ->
                    val n = p.news[nx]
                    FocusCard(pad = 12.dp) {
                        Row {
                            if (n.img.isNotBlank()) {
                                Img(n.img, Modifier.width(150.dp).height(86.dp).roundedClip(8.dp))
                                Spacer(Modifier.width(14.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(rememberSpanish(n.t), color = C.Hi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (n.d.isNotBlank()) Text(rememberSpanish(n.d), color = C.Mid, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                val at = Sports.parseTime(n.at)
                                if (at > 0) Text(whenLabel(at), color = C.Low, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    sheet?.let { ev -> EventSheet(ev, c, sportsCats) { sheet = null } }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(Modifier.background(Color(0x22FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, color = C.Mid, fontSize = 11.sp)
        Text(value, color = C.Hi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun leaderEs(cat: String): String = when {
    cat.contains("Passing", true) -> "Pases"
    cat.contains("Rushing", true) -> "Acarreos"
    cat.contains("Receiving", true) -> "Recepciones"
    cat.contains("Points", true) -> "Puntos"
    cat.contains("Rebounds", true) -> "Rebotes"
    cat.contains("Assists", true) -> "Asistencias"
    cat.contains("Goals", true) -> "Goles"
    cat.contains("Batting", true) || cat.contains("Average", true) -> "Bateo"
    cat.contains("Home Runs", true) -> "Jonrones"
    cat.contains("RBI", true) -> "Carreras impulsadas"
    cat.contains("ERA", true) || cat.contains("Pitching", true) -> "Pitcheo"
    else -> cat
}

/** Renglones de una tabla de posiciones; cada uno se puede enfocar para bajar con el control. */
fun androidx.compose.foundation.lazy.LazyListScope.tableItems(prefix: String, cols: List<String>, rows: List<com.mauricio.cinecasa.data.SpTableRow>, highlight: String?, firstFocus: FocusRequester? = null) {
    item("${prefix}_h") {
        Row(Modifier.padding(horizontal = 14.dp)) {
            Text("#", color = C.Low, fontSize = 12.sp, modifier = Modifier.width(34.dp))
            Text("Equipo", color = C.Low, fontSize = 12.sp, modifier = Modifier.weight(1f))
            cols.forEach { Text(it, color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(64.dp)) }
        }
    }
    items(rows.size, key = { "${prefix}_$it" }) { i ->
        val r = rows[i]
        val me = r.id == highlight
        val shape = RoundedCornerShape(10.dp)
        Surface(
            onClick = {},
            modifier = Modifier.fillMaxWidth().then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, C.Accent), shape = shape)),
            colors = ClickableSurfaceDefaults.colors(containerColor = if (me) C.AccentSoft else C.Panel, contentColor = C.Hi, focusedContainerColor = if (me) C.AccentSoft else C.Panel2, focusedContentColor = C.Hi),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = C.Low, fontSize = 14.sp, modifier = Modifier.width(34.dp))
                TeamBadge(SpTeam(r.id, r.n, r.n, r.ab, r.logo, ""), 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(r.n, color = C.Hi, fontSize = 16.sp, fontWeight = if (me) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                r.v.forEachIndexed { k, v -> Text(v, color = if (k == 0) C.Hi else C.Mid, fontSize = 15.sp, fontWeight = if (k == 0) FontWeight.SemiBold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(64.dp)) }
            }
        }
    }
}

@Composable
fun TableScreen(lg: String) {
    val table by produceState<com.mauricio.cinecasa.data.SpTable?>(null, lg) { value = Sports.table(lg) }
    val teams by Teams.list.collectAsState()
    val mine = teams.filter { it.lg == lg }.map { it.id }.toSet()
    val side = if (LocalCompact.current) 16.dp else 28.dp
    val fr = remember { FocusRequester() }
    val t = table
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, top = 20.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item("title") { Text("Tabla · ${Sports.leagueByKey[lg]?.name ?: lg}", color = C.Hi, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        if (t == null) item("load") { Spinner() }
        else if (t.groups.isEmpty()) item("empty") { Text("Esta liga no tiene tabla disponible.", color = C.Mid, fontSize = 14.sp) }
        else t.groups.forEachIndexed { gi, g ->
            item("g$gi") { SectionTitle(g.first, Modifier.padding(top = 12.dp)) }
            tableItems("t$gi", t.cols, g.second, g.second.firstOrNull { it.id in mine }?.id, if (gi == 0) fr else null)
        }
    }
    // En cuanto llega la tabla, el foco entra a ella (si no, se va al menú lateral)
    LaunchedEffect(t) { if (t != null) { delay(120); runCatching { fr.requestFocus() } } }
}

// ======================================================================
// Elegir equipos
// ======================================================================
@Composable
fun TeamPickerDialog(onDone: () -> Unit) {
    var lg by remember { mutableStateOf("lmp") }
    val list by produceState(emptyList<SpTeam>(), lg) { value = emptyList(); value = Sports.teams(lg) }
    val teams by Teams.list.collectAsState()
    val fr = remember { FocusRequester() }
    val perRow = if (LocalCompact.current) 2 else 3
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.cardWidth(760.dp).heightIn(max = 660.dp).background(C.Panel, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mis equipos", color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Elige los equipos que sigues. Te avisamos cuando jueguen y verás sus partidos, tabla, lesionados y noticias.", color = C.Mid, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Sports.leagues, key = { it.k }) { l ->
                    Pill(l.short, lg == l.k, { lg = l.k }, if (l.k == "lmp") Modifier.focusRequester(fr) else Modifier)
                }
            }
            if (list.isEmpty()) Spinner()
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list.chunked(perRow), key = { r -> r.joinToString { it.id } }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            val on = teams.any { it.key == "$lg:${t.id}" }
                            val shape = RoundedCornerShape(10.dp)
                            Surface(
                                onClick = { Teams.toggle(lg, t) },
                                modifier = Modifier.weight(1f),
                                shape = ClickableSurfaceDefaults.shape(shape),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                border = ClickableSurfaceDefaults.border(
                                    border = if (on) Border(BorderStroke(2.dp, C.Gold), shape = shape) else Border.None,
                                    focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = shape),
                                ),
                                colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel2, contentColor = C.Hi, focusedContainerColor = C.Panel2, focusedContentColor = C.Hi),
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TeamBadge(t, 30.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (on) Icon(Icons.Filled.Star, "Siguiendo", Modifier.size(16.dp), tint = C.Gold)
                                }
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("Siguiendo: ${teams.size}", color = C.Low, fontSize = 12.sp)
            ActionButton("Listo", onDone, primary = true)
        }
    }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
}
