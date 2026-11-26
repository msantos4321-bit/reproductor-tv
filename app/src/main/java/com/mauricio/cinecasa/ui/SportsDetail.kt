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
    val nav = LocalNav.current
    val c by Repo.catalog.collectAsState()
    val board = rememberBoard()
    val page by produceState<com.mauricio.cinecasa.data.SpTeamPage?>(null, lg, team.id) { value = Sports.team(lg, team.id) }
    val table by produceState<com.mauricio.cinecasa.data.SpTable?>(null, lg) { value = if (lg == "lmp" || lg == "ufc") null else Sports.table(lg) }
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, top = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item("head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(t, if (compact) 64.dp else 88.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = if (compact) 24.sp else 30.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(listOfNotNull(Sports.leagueByKey[lg]?.name, page?.rec?.ifBlank { null }?.let { "Récord $it" }, page?.standing?.ifBlank { null }).joinToString(" · "), color = C.Mid, fontSize = 14.sp)
                    val p = page
                    if (p != null && (p.home.isNotBlank() || p.streak.isNotBlank()) && !noSpoil) Text(
                        listOfNotNull(p.home.ifBlank { null }?.let { "En casa $it" }, p.away.ifBlank { null }?.let { "De visita $it" }, p.streak.ifBlank { null }?.let { "Racha: ${streakEs(it)}" }).joinToString(" · "),
                        color = C.Low, fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Wrap(gap = 10.dp) {
                ActionButton(if (following) "Siguiendo" else "Seguir equipo", { Teams.toggle(lg, t) }, Modifier.focusRequester(fr), primary = !following, icon = if (following) Icons.Filled.Star else Icons.Filled.StarBorder)
                Pill(if (noSpoil) "Sin marcadores: activado" else "Sin marcadores", noSpoil, { Teams.setNoSpoil(key, !noSpoil) })
            }
            if (noSpoil) Text("Los marcadores de este equipo quedan ocultos hasta que tú los pidas. Ideal si lo vas a ver grabado.", color = C.Low, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val p = page
        if (p == null) item("load") { Spinner() }
        else {
            // En vivo ahora (del tablero)
            board.events.firstOrNull { it.live && it.has(key) }?.let { live ->
                item("live") {
                    Column { SectionTitle("En vivo ahora"); ScoreCard(live, onFocused = {}) { sheet = live } }
                }
            }
            if (p.next.isNotEmpty()) item("next") {
                Column {
                    SectionTitle("Próximos partidos")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(p.next, key = { it.id }) { g ->
                            val ev = board.events.firstOrNull { it.lg == lg && it.id == g.id } ?: gameToEvent(lg, t, g)
                            ScoreCard(ev, channel = g.tv.joinToString(" · ").ifBlank { null }) { sheet = ev }
                        }
                    }
                }
            }
            if (p.past.isNotEmpty()) item("past") {
                Column {
                    SectionTitle("Últimos resultados")
                    if (noSpoil) Text("Ocultos (sin marcadores activado).", color = C.Low, fontSize = 13.sp)
                    else Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.past.reversed().forEach { g ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ResultChip(g.res)
                                Spacer(Modifier.width(10.dp))
                                Text(whenLabel(g.start).substringBeforeLast(' '), color = C.Low, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                                Text(if (g.home) "vs" else "en", color = C.Low, fontSize = 13.sp, modifier = Modifier.width(28.dp))
                                TeamBadge(g.opp, 22.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(g.opp.n, color = C.Hi, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${g.us} – ${g.them}", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item("inj") {
                Column {
                    SectionTitle("Lesionados")
                    if (p.inj.isEmpty()) Text(if (lg == "lmp") "Esta liga no publica reporte de lesionados." else "Sin reporte de lesionados por ahora.", color = C.Low, fontSize = 13.sp)
                    else Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.inj.forEach { i ->
                            Row {
                                Text(i.n, color = C.Hi, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(listOf(i.pos, i.d).filter { it.isNotBlank() }.joinToString(" · "), color = C.Low, fontSize = 12.sp, modifier = Modifier.width(150.dp), maxLines = 1)
                                Text(i.st, color = if (i.st == "Fuera" || i.st.startsWith("Lista")) Live else C.Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(130.dp))
                            }
                        }
                    }
                }
            }
            if (p.leaders.isNotEmpty()) item("lead") {
                Column {
                    SectionTitle("Líderes del equipo")
                    Wrap(gap = 12.dp) {
                        p.leaders.forEach { l ->
                            Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp).width(230.dp)) {
                                Text(leaderEs(l.cat), color = C.Low, fontSize = 12.sp)
                                Text(l.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(l.v, color = C.Mid, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            table?.let { tb ->
                val g = tb.groups.firstOrNull { gr -> gr.second.any { it.id == team.id } } ?: tb.groups.firstOrNull()
                if (g != null) item("table") { Column { SectionTitle("Tabla · ${g.first}"); TableBlock(tb.cols, g.second, team.id) } }
            }
            if (p.news.isNotEmpty()) item("news") {
                Column {
                    SectionTitle("Noticias")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.news.forEach { n ->
                            Row(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                if (n.img.isNotBlank()) {
                                    Img(n.img, Modifier.width(140.dp).height(80.dp).roundedClip(8.dp))
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(n.t, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (n.d.isNotBlank()) Text(n.d, color = C.Mid, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    val at = Sports.parseTime(n.at)
                                    if (at > 0) Text(whenLabel(at), color = C.Low, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(120); runCatching { fr.requestFocus() } }
    sheet?.let { ev -> EventSheet(ev, c, sportsCats) { sheet = null } }
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

@Composable
private fun TableBlock(cols: List<String>, rows: List<com.mauricio.cinecasa.data.SpTableRow>, highlight: String?) {
    Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text("#", color = C.Low, fontSize = 12.sp, modifier = Modifier.width(28.dp))
            Text("Equipo", color = C.Low, fontSize = 12.sp, modifier = Modifier.weight(1f))
            cols.forEach { Text(it, color = C.Low, fontSize = 12.sp, modifier = Modifier.width(54.dp)) }
        }
        rows.forEachIndexed { i, r ->
            val me = r.id == highlight
            Row(Modifier.then(if (me) Modifier.background(C.AccentSoft, RoundedCornerShape(6.dp)) else Modifier).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = C.Low, fontSize = 13.sp, modifier = Modifier.width(28.dp))
                TeamBadge(SpTeam(r.id, r.n, r.n, r.ab, r.logo, ""), 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(r.n, color = C.Hi, fontSize = 14.sp, fontWeight = if (me) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
                r.v.forEach { Text(it, color = C.Mid, fontSize = 13.sp, modifier = Modifier.width(54.dp)) }
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
    val t = table
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, top = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Tabla · ${Sports.leagueByKey[lg]?.name ?: lg}", color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        if (t == null) item { Spinner() }
        else if (t.groups.isEmpty()) item { Text("Esta liga no tiene tabla disponible.", color = C.Mid, fontSize = 14.sp) }
        else t.groups.forEach { g ->
            item(g.first) { Column { SectionTitle(g.first); TableBlock(t.cols, g.second, g.second.firstOrNull { it.id in mine }?.id) } }
        }
    }
}

// ======================================================================
// Detalle del partido
// ======================================================================
@Composable
fun GameScreen(ev0: SpEvent) {
    val board = rememberBoard()
    val ev = board.events.firstOrNull { it.key == ev0.key } ?: ev0
    val detail by produceState<com.mauricio.cinecasa.data.SpGameDetail?>(null, ev.key) {
        while (true) {
            value = Sports.game(ev.lg, ev.id, ev.live)
            delay(if (value?.st == "in") 20_000 else 120_000)
        }
    }
    val c by Repo.catalog.collectAsState()
    val sportsCats = remember(c) { SportsCats.ids(c) }
    var sheet by remember { mutableStateOf(false) }
    val rv by Teams.revealed.collectAsState()
    val ns by Teams.noSpoil.collectAsState()
    val hide = remember(ev, rv, ns) { Teams.hidden(ev) }
    val fr = remember { FocusRequester() }
    val side = if (LocalCompact.current) 16.dp else 28.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, top = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item("head") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Sports.leagueByKey[ev.lg]?.name ?: "", color = C.Low, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    if (ev.live) LiveDot(ev.det.ifBlank { "EN VIVO" }, 13) else Text(statusText(ev), color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                listOf(ev.a, ev.h).forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(t, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(t.full.ifBlank { t.n }, color = C.Hi, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!ev.upcoming && !hide) Text(t.sc, color = C.Hi, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Wrap(gap = 10.dp) {
                    ActionButton("Opciones (ver, grabar)", { sheet = true }, Modifier.focusRequester(fr), primary = true)
                    if (hide) ActionButton("Mostrar marcador", { Teams.reveal(ev.key) })
                }
            }
        }
        val d = detail
        if (d == null) item("load") { Spinner() }
        else if (!hide) {
            if (d.line.isNotEmpty() && d.line.any { it.ls.isNotEmpty() }) item("line") {
                Column {
                    SectionTitle(if (ev.sp == "baseball") "Carreras por entrada" else "Marcador por periodo")
                    Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        val n = d.line.maxOf { it.ls.size }
                        Row {
                            Text("", modifier = Modifier.width(70.dp))
                            (1..n).forEach { Text("$it", color = C.Low, fontSize = 12.sp, modifier = Modifier.width(34.dp)) }
                            Text("T", color = C.Low, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        d.line.sortedBy { it.home }.forEach { l ->
                            Row(Modifier.padding(top = 4.dp)) {
                                Text(l.ab, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp))
                                (0 until n).forEach { i -> Text(l.ls.getOrElse(i) { "" }, color = C.Mid, fontSize = 14.sp, modifier = Modifier.width(34.dp)) }
                                Text(l.sc, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (d.wp.size > 2) item("wp") {
                Column {
                    SectionTitle("Probabilidad de ganar")
                    WinProbChart(d.wp, ev.h, ev.a, Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(14.dp))
                }
            } else d.predHome?.let { ph ->
                item("pred") {
                    Column {
                        SectionTitle("Pronóstico antes del partido")
                        Row(Modifier.fillMaxWidth().height(28.dp).roundedClip(8.dp)) {
                            Box(Modifier.weight(ph.coerceIn(5f, 95f)).fillMaxSize().background(Color(ev.h.color)), contentAlignment = Alignment.CenterStart) {
                                Text("  ${ev.h.initials} ${ph.toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(Modifier.weight((100f - ph).coerceIn(5f, 95f)).fillMaxSize().background(Color(ev.a.color)), contentAlignment = Alignment.CenterEnd) {
                                Text("${ev.a.initials} ${(100 - ph).toInt()}%  ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (d.plays.isNotEmpty()) item("plays") {
                Column {
                    SectionTitle(if (ev.done) "Últimas jugadas" else "Jugada por jugada")
                    if (d.drive.isNotBlank()) Text("Serie actual: ${d.drive}", color = C.Mid, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Column(Modifier.background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.plays.forEach { pl ->
                            Row {
                                Text(pl.clk, color = C.Low, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                                Text(rememberSpanish(pl.t), color = if (pl.scoring) C.Hi else C.Mid, fontSize = 14.sp, fontWeight = if (pl.scoring) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
        val d2 = detail
        if (d2 != null && d2.inj.isNotEmpty()) item("inj") {
            Column {
                SectionTitle("Lesionados")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(ev.a, ev.h).forEach { t ->
                        Column(Modifier.weight(1f).background(C.Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(t.n, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val l = d2.inj[t.id].orEmpty()
                            if (l.isEmpty()) Text("Sin reporte", color = C.Low, fontSize = 13.sp)
                            l.forEach { (n, st) -> Row { Text(n, color = C.Mid, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1); Text(st, color = C.Warn, fontSize = 12.sp) } }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(120); runCatching { fr.requestFocus() } }
    if (sheet) EventSheet(ev, c, sportsCats) { sheet = false }
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
