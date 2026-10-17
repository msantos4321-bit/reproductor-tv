package com.mauricio.cinecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Cloud
import com.mauricio.cinecasa.data.License
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** Primera pantalla en un aparato sin activar. */
@Composable
fun ActivationScreen(msg: String, onPair: () -> Unit) {
    var code by remember { mutableStateOf(License.code) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(msg) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    fun go() {
        if (code.isBlank()) { error = "Escribe tu código."; return }
        busy = true; error = ""
        scope.launch { error = License.activate(code) ?: ""; busy = false }
    }
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(320.dp)) {
                Text("CINECASA", color = C.Hi, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
                Spacer(Modifier.height(12.dp))
                Text("Activa este aparato con el código que te dieron.", color = C.Mid, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("¿No tienes código? Pídeselo a quien te compartió la app.", color = C.Low, fontSize = 13.sp)
            }
            Column(
                Modifier.width(420.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TvTextField(code, { code = it.uppercase() }, "Código (ej. CC-7F3K-92QD)", Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Go, leading = Icons.Filled.Key, boxRequester = fr, onImeAction = { go() })
                if (error.isNotEmpty()) Text(error, color = C.Warn, fontSize = 13.sp)
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) { Spinner(size = 24.dp); Text("  Activando…", color = C.Mid, fontSize = 14.sp) }
                else ActionButton("Activar", { go() }, Modifier.fillMaxWidth(), primary = true)
                ActionButton("Tengo CineCasa en otro aparato", onPair, Modifier.fillMaxWidth())
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

/** Panel del dueño para crear y controlar códigos. */
@Composable
fun LicensesSection() {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var devices by remember { mutableIntStateOf(1) }
    var created by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "MX")) }
    val iso = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") } }
    fun date(s: String) = try { fmt.format(iso.parse(s.take(19))!!) } catch (_: Exception) { s.take(16) }

    fun reload() {
        loading = true
        scope.launch {
            val a = Cloud.licList()
            list = a?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } } ?: list
            msg = if (a == null) "No se pudo cargar (¿sin internet?)" else ""
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Cada aparato que no sea de tu hogar necesita un código para abrir la app. Tus aparatos y los que emparejas con «Agregar aparato» nunca lo piden. " +
                "Si desactivas un código, esos aparatos dejan de funcionar la próxima vez que revisen (al abrir o cada media hora).",
            color = C.Mid, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth(0.9f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(note, { note = it }, "¿Para quién es? (ej. Primo Luis)", Modifier.width(300.dp))
            for (n in listOf(1, 2, 3, 5)) Pill("$n ${if (n == 1) "aparato" else "aparatos"}", devices == n, { devices = n })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton("Generar código", {
                scope.launch {
                    val c = Cloud.licCreate(note.trim(), devices)
                    if (c == null) msg = "No se pudo generar (¿sin internet?)" else { created = c; note = ""; reload() }
                }
            }, icon = Icons.Filled.Add, primary = true)
            ActionButton("Actualizar lista", { reload() }, icon = Icons.Filled.Refresh)
            if (loading) Spinner(size = 22.dp)
        }
        if (created.isNotEmpty()) {
            Column(Modifier.background(C.AccentSoft, RoundedCornerShape(12.dp)).padding(16.dp)) {
                Text("Código nuevo", color = C.Accent, fontSize = 12.sp)
                Text(created, color = C.Hi, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
                Text("Compártelo junto con la liga de descarga. Se escribe tal cual (las letras pueden ir en minúsculas).", color = C.Mid, fontSize = 12.sp)
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
        list.forEach { o ->
            val code = o.optString("code")
            val revoked = o.optBoolean("revoked")
            val devs = o.optJSONArray("devices")
            val used = devs?.length() ?: 0
            Row(Modifier.fillMaxWidth().background(C.Panel2, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(code + "  ·  " + o.optString("note").ifBlank { "sin nombre" }, color = if (revoked) C.Low else C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    val names = devs?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.joinToString(", ") { d -> d.optString("name").ifBlank { "aparato" } + " (visto " + date(d.optString("seen")) + ")" } }.orEmpty()
                    Text(
                        (if (revoked) "DESACTIVADO · " else "") + "$used de ${o.optInt("max")} aparatos" + if (names.isNotBlank()) " · $names" else " · sin usar",
                        color = if (revoked) C.Warn else C.Low, fontSize = 11.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (revoked) "Activar" else "Desactivar", false, { scope.launch { Cloud.licSet(code, !revoked, false); reload() } })
                    if (used > 0) Pill("Liberar aparatos", false, { scope.launch { Cloud.licSet(code, revoked, true); reload() } })
                }
            }
        }
        if (list.isEmpty() && !loading && msg.isEmpty()) Text("Aún no has generado códigos.", color = C.Low, fontSize = 12.sp)
    }
}
