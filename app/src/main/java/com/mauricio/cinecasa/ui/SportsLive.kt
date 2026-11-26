package com.mauricio.cinecasa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Quiniela
import com.mauricio.cinecasa.data.Recorder
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SpEvent
import com.mauricio.cinecasa.data.Sports
import com.mauricio.cinecasa.data.SportsCats
import com.mauricio.cinecasa.data.Teams
import com.mauricio.cinecasa.data.TextUtil
import kotlinx.coroutines.delay

// ======================================================================
// Avisos de mis equipos (mientras la app está abierta)
// ======================================================================
private data class Banner(val title: String, val body: String, val ev: SpEvent?)

/**
 * Vigila los partidos de los equipos que sigue el perfil:
 * - «Empieza en 15 minutos» (con botón para verlo si no estás viendo nada),
 * - cambios de marcador y resultado final (aviso pequeño en la esquina).
 * Respeta «sin marcadores».
 */
@Composable
fun BoxScope.SportsWatcher(playing: Boolean) {
    val teams by Teams.list.collectAsState()
    if (teams.isEmpty() || !Prefs.sportAlerts) return
    val board = rememberBoard()
    val nav = LocalNav.current
    val last = remember { HashMap<String, String>() }
    val warned = remember { HashSet<String>() }
    var banner by remember { mutableStateOf<Banner?>(null) }
    var startDlg by remember { mutableStateOf<SpEvent?>(null) }
    val keys = remember(teams) { teams.map { it.key }.toSet() }

    LaunchedEffect(board.at) {
        if (board.events.isEmpty()) return@LaunchedEffect
        Quiniela.settle(board.events)
        val now = System.currentTimeMillis()
        val first = last.isEmpty()
        for (e in board.events) {
            if (keys.none { e.has(it) }) continue
            val state = "${e.st}|${e.a.sc}|${e.h.sc}"
            val prev = last[e.key]
            last[e.key] = state
            if (e.upcoming && e.start - now in 0..16 * 60_000 && warned.add(e.key)) {
                if (!playing) startDlg = e
                else banner = Banner("Empieza en ${maxOf(1, ((e.start - now) / 60_000).toInt())} min", e.title, e)
                continue
            }
            if (first || prev == null || prev == state) continue
            val hide = Teams.hidden(e)
            val score = if (hide) "" else "${e.a.n} ${e.a.sc} – ${e.h.sc} ${e.h.n}"
            banner = when {
                e.done && !prev.startsWith("post") -> Banner("Terminó", if (hide) "${e.title} · marcador oculto" else score, e)
                e.live && prev.startsWith("pre") -> Banner("Ya empezó", e.title, e)
                e.live && !hide -> Banner(if (e.sp == "baseball") "Carrera" else if (e.sp == "soccer") "Gol" else "Anotación", "$score · ${e.det}", e)
                else -> banner
            }
        }
    }
    LaunchedEffect(banner) { if (banner != null) { delay(8000); banner = null } }

    AnimatedVisibility(banner != null, Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 22.dp), enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        val b = banner
        if (b != null) Row(
            Modifier.widthIn(max = 420.dp).background(Color(0xF0202022), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            b.ev?.let { e -> TeamBadge(e.a, 28.dp); Spacer(Modifier.width(4.dp)); TeamBadge(e.h, 28.dp); Spacer(Modifier.width(12.dp)) }
            Column {
                Text(b.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(b.body, color = Color(0xFFD1D1D6), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    startDlg?.let { e ->
        val c by Repo.catalog.collectAsState()
        val chans = remember(e.key, c) { Sports.channelsFor(e, c, SportsCats.ids(c)) }
        val fr = remember { FocusRequester() }
        Dialog(onDismissRequest = { startDlg = null }) {
            Column(Modifier.cardWidth(480.dp).background(C.Panel, RoundedCornerShape(16.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tu partido ${countdown(e.start)}", color = C.Hi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(e.a, 34.dp); Text("  vs  ", color = C.Mid); TeamBadge(e.h, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(e.title, color = C.Hi, fontSize = 16.sp, maxLines = 2)
                }
                if (chans.isNotEmpty()) Text("Canal: ${chans.first().name}", color = C.Mid, fontSize = 14.sp)
                Wrap(gap = 10.dp) {
                    if (chans.isNotEmpty()) ActionButton("Verlo", { startDlg = null; nav.playLive(chans, 0) }, Modifier.focusRequester(fr), primary = true, icon = Icons.Filled.PlayArrow)
                    ActionButton("Ir a Deportes", { startDlg = null; nav.go(Route.Sports) }, if (chans.isEmpty()) Modifier.focusRequester(fr) else Modifier)
                    ActionButton("Después", { startDlg = null })
                }
            }
        }
        LaunchedEffect(e.key) { delay(120); runCatching { fr.requestFocus() } }
    }
}

// ======================================================================
// Marcador chiquito en la esquina mientras ves otra cosa
// ======================================================================
@Composable
fun BoxScope.MiniScore() {
    if (!Prefs.miniScore) return
    val teams by Teams.list.collectAsState()
    val pinned by SportsPin.key.collectAsState()
    if (teams.isEmpty() && pinned == null) return
    val board = rememberBoard()
    val rv by Teams.revealed.collectAsState()
    val ns by Teams.noSpoil.collectAsState()
    val keys = teams.map { it.key }.toSet()
    val ev = board.events.firstOrNull { it.key == pinned && !it.done }
        ?: board.events.firstOrNull { e -> e.live && keys.any { e.has(it) } }
        ?: return
    val hide = remember(ev, rv, ns) { Teams.hidden(ev) }
    Row(
        Modifier.align(Alignment.TopStart).padding(18.dp).background(Color(0xD9121214), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(ev.a, 20.dp)
        Text(" ${ev.a.initials} ${if (hide) "" else ev.a.sc}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("  –  ", color = Color(0xFF8E8E93), fontSize = 13.sp)
        Text("${if (hide) "" else ev.h.sc} ${ev.h.initials} ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        TeamBadge(ev.h, 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Live))
        Text(" ${ev.det}", color = Color(0xFFD1D1D6), fontSize = 12.sp, maxLines = 1)
    }
}

// ======================================================================
// Grabaciones (dentro de Descargas)
// ======================================================================
@Composable
fun RecordingsSection(compact: Boolean) {
    val nav = LocalNav.current
    val recs by Recorder.list.collectAsState()
    val visible = recs.filter { it.status != Recorder.CANCELED }.sortedWith(compareByDescending<Recorder.Rec> { it.recording }.thenByDescending { it.scheduled }.thenByDescending { it.start })
    if (visible.isEmpty()) return
    var confirm by remember { mutableStateOf<Recorder.Rec?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Grabaciones", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        visible.forEach { r ->
            val status = when {
                r.recording -> "Grabando · ${bytes(r.bytes)} · termina ${TextUtil.hhmm(r.end)}"
                r.scheduled -> "Programada: ${whenLabel(r.start)} a ${TextUtil.hhmm(r.end)}"
                r.finished -> "Lista · ${bytes(r.bytes)}" + if (r.err.isNotBlank()) " · ${r.err}" else ""
                else -> r.err.ifBlank { "No se pudo grabar." } + if (r.bytes > 2_000_000) " (se guardó una parte)" else ""
            }
            val rowContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                Box(Modifier.size(width = if (compact) 96.dp else 150.dp, height = if (compact) 54.dp else 84.dp).clip(RoundedCornerShape(8.dp)).background(C.Panel2), contentAlignment = Alignment.Center) {
                    Img(r.image, Modifier.fillMaxSize().padding(10.dp), androidx.compose.ui.layout.ContentScale.Fit)
                    if (r.recording) Row(Modifier.align(Alignment.TopStart).padding(5.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(9.dp), tint = Live)
                        Text(" REC", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (r.finished && r.dur > 0 && r.pos > 0) ProgressBar(r.pos.toFloat() / r.dur, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.chName, color = C.Mid, fontSize = 13.sp, maxLines = 1)
                    Text(status, color = if (r.status == Recorder.FAILED) C.Warn else if (r.recording) Live else C.Low, fontSize = 12.sp, maxLines = 2)
                }
            }
            val buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                if (r.playable) ActionButton(if (r.recording) "Ver desde el inicio" else "Ver", { playRecording(r, nav) }, primary = true, icon = Icons.Filled.PlayArrow)
                when {
                    r.recording -> ActionButton("Detener", { Recorder.stop(r.id) }, icon = Icons.Filled.Stop)
                    r.scheduled -> ActionButton("Cancelar", { Recorder.delete(r.id) })
                    else -> ActionButton("Borrar", { confirm = r }, icon = Icons.Filled.Delete)
                }
            }
            if (compact) Column(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { rowContent() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            } else Row(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                rowContent()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            }
        }
        Text("Las grabaciones ocupan espacio en este aparato (unos ${bytes(Recorder.BYTES_PER_HOUR)} por hora). Libre: ${bytes(Recorder.freeBytes())}.", color = C.Low, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }
    confirm?.let { r ->
        ConfirmDialog("¿Borrar la grabación?", "«${r.title}» se borrará de este aparato.", "Borrar", { Recorder.delete(r.id); confirm = null }, { confirm = null })
    }
}
