package com.mauricio.cinecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Downloads
import com.mauricio.cinecasa.data.Library
import com.mauricio.cinecasa.data.PlayItem
import com.mauricio.cinecasa.data.Profiles
import kotlinx.coroutines.delay

fun bytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.0f MB".format(b / 1e6)
    b > 0 -> "%.0f KB".format(b / 1e3)
    else -> "0 MB"
}

/** Invitado y perfil privado no descargan (no deben dejar rastro). */
fun canDownload(): Boolean = Profiles.current.value?.let { !it.guest && !it.private } ?: true

@Composable
fun DownloadButton(item: PlayItem?, label: String = "Descargar") {
    if (item == null || !canDownload()) return
    val list by Downloads.list.collectAsState()
    val d = list.firstOrNull { it.key == item.key }
    LaunchedEffect(d?.status) { while (d != null && !d.finished && !d.failed) { delay(2000); Downloads.refresh() } }
    when {
        d == null || d.failed -> ActionButton(if (d?.failed == true) "Reintentar descarga" else label, { Downloads.start(item) }, icon = Icons.Filled.Download)
        d.finished -> ActionButton("Descargada", { }, icon = Icons.Filled.DownloadDone)
        else -> ActionButton("Descargando ${(d.fraction * 100).toInt()}%", { }, icon = Icons.Filled.Download)
    }
}

/** Descargar o borrar un episodio suelto (debajo de cada episodio). */
@Composable
fun EpisodeDownloadPill(item: PlayItem?) {
    if (item == null) return
    val list by Downloads.list.collectAsState()
    val d = list.firstOrNull { it.key == item.key }
    LaunchedEffect(d?.status) { while (d != null && !d.finished && !d.failed) { delay(2000); Downloads.refresh() } }
    when {
        d == null -> Pill("Descargar", false, { Downloads.start(item) }, icon = Icons.Filled.Download)
        d.failed -> Pill("Reintentar", false, { Downloads.start(item) }, icon = Icons.Filled.Download)
        d.finished -> Pill("Descargado · Borrar", true, { Downloads.remove(d.key) }, icon = Icons.Filled.DownloadDone)
        else -> Pill("${(d.fraction * 100).toInt()}% · Cancelar", true, { Downloads.remove(d.key) }, icon = Icons.Filled.Download)
    }
}

@Composable
fun DownloadsScreen() {
    val nav = LocalNav.current
    val list by Downloads.list.collectAsState()
    val prog by Library.progress.collectAsState()
    LaunchedEffect(Unit) { while (true) { Downloads.refresh(); delay(2000) } }

    if (list.isEmpty()) {
        CenterMessage(
            "Aún no tienes descargas",
            "En la ficha de una película o serie elige «Descargar» para verla sin internet (ideal en el celular o la tableta). " +
                "Mientras se descarga, ocupa una de las pantallas de tu cuenta.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Descargas", color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Espacio libre en este aparato: ${bytes(Downloads.freeBytes())}", color = C.Low, fontSize = 12.sp)
            }
        }
        items(list.sortedByDescending { it.added }, key = { it.key }) { d ->
            if (LocalCompact.current) { CompactDownloadRow(d, list, prog, nav); return@items }
            Row(
                Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(12.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(C.Panel2)) {
                    Img(d.image, Modifier.fillMaxSize())
                    prog[d.key]?.let { ProgressBar(it.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.subtitle.isNotBlank()) Text(d.subtitle, color = C.Mid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val st = when {
                        d.finished -> "Lista para ver sin internet · ${bytes(d.total)}"
                        d.failed -> "La descarga falló (el servidor la cortó o no hay espacio)"
                        d.total > 0 -> "Descargando ${(d.fraction * 100).toInt()}% · ${bytes(d.done)} de ${bytes(d.total)}"
                        else -> "En espera…"
                    }
                    Text(st, color = if (d.failed) C.Warn else C.Low, fontSize = 12.sp)
                    if (!d.finished && !d.failed && d.total > 0) ProgressBar(d.fraction, Modifier.fillMaxWidth(0.6f).padding(top = 4.dp), 4.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.finished) ActionButton("Ver", {
                        // Para series, arma la lista con los episodios descargados de esa serie, en orden
                        val group = if (d.kind == "episode") list.filter { it.finished && it.kind == "episode" && it.seriesId == d.seriesId }
                            .sortedWith(compareBy({ it.season }, { it.ep })) else listOf(d)
                        val items = group.map { PlayItem(it.key, it.kind, it.id, it.seriesId, Downloads.localUri(it.key) ?: "", it.title, it.subtitle, it.image, it.season, it.ep) }
                        val idx = items.indexOfFirst { it.key == d.key }.coerceAtLeast(0)
                        nav.play(items, idx, prog[d.key]?.pos ?: 0L)
                    }, primary = true, icon = Icons.Filled.PlayArrow)
                    ActionButton(if (d.finished || d.failed) "Borrar" else "Cancelar", { Downloads.remove(d.key) }, icon = Icons.Filled.Delete)
                }
            }
        }
    }
}


/** En el celular: imagen y textos arriba, botones abajo (sin apretar el texto). */
@Composable
private fun CompactDownloadRow(d: com.mauricio.cinecasa.data.Downloads.Dl, list: List<com.mauricio.cinecasa.data.Downloads.Dl>, prog: Map<String, com.mauricio.cinecasa.data.Progress>, nav: Nav) {
    Column(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(8.dp)).background(C.Panel2)) {
                Img(d.image, Modifier.fillMaxSize())
                prog[d.key]?.let { ProgressBar(it.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (d.subtitle.isNotBlank()) Text(d.subtitle, color = C.Mid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val st = when {
                    d.finished -> "Lista · ${bytes(d.total)}"
                    d.failed -> "La descarga falló"
                    d.total > 0 -> "${(d.fraction * 100).toInt()}% · ${bytes(d.done)} de ${bytes(d.total)}"
                    else -> "En espera…"
                }
                Text(st, color = if (d.failed) C.Warn else C.Low, fontSize = 12.sp)
            }
        }
        if (!d.finished && !d.failed && d.total > 0) ProgressBar(d.fraction, Modifier.fillMaxWidth(), 4.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.finished) ActionButton("Ver", {
                val group = if (d.kind == "episode") list.filter { it.finished && it.kind == "episode" && it.seriesId == d.seriesId }
                    .sortedWith(compareBy({ it.season }, { it.ep })) else listOf(d)
                val items = group.map { PlayItem(it.key, it.kind, it.id, it.seriesId, Downloads.localUri(it.key) ?: "", it.title, it.subtitle, it.image, it.season, it.ep) }
                nav.play(items, items.indexOfFirst { it.key == d.key }.coerceAtLeast(0), prog[d.key]?.pos ?: 0L)
            }, Modifier.weight(1f), primary = true, icon = Icons.Filled.PlayArrow)
            ActionButton(if (d.finished || d.failed) "Borrar" else "Cancelar", { Downloads.remove(d.key) }, Modifier.weight(1f), icon = Icons.Filled.Delete)
        }
    }
}
