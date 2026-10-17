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
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
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

const val DOWNLOAD_URL = "https://github.com/msantos4321-bit/reproductor-tv/releases/latest/download/CineCasa.apk"

fun inviteText(code: String, max: Int, name: String) =
    (if (name.isNotBlank()) "Hola $name, " else "Hola, ") + "te comparto CineCasa 🎬\n\n" +
        "1. Descarga la app (en el celular, tablet o tele Android):\n$DOWNLOAD_URL\n\n" +
        "2. Ábrela y escribe tu código de activación:\n$code\n\n" +
        "Sirve para $max ${if (max == 1) "aparato" else "aparatos"}. En la tele puedes instalarla con la app «Downloader» escribiendo la liga de arriba.\n" +
        "Si Android pregunta, permite «instalar apps desconocidas»."

/** Comparte la invitación por WhatsApp, correo, etc. Devuelve false si el aparato no tiene cómo compartir. */
fun shareInvite(ctx: Context, code: String, max: Int, name: String, email: Boolean): Boolean {
    val body = inviteText(code, max, name)
    val subject = "Tu invitación a CineCasa"
    val intent = if (email) Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, body)
    } else Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, body)
    }, "Enviar invitación")
    return try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
}

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
        AdaptivePane(56.dp) {
            Column(Modifier.paneWidth(320.dp)) {
                Text("CINECASA", color = C.Hi, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
                Spacer(Modifier.height(12.dp))
                Text("Activa este aparato con el código que te dieron.", color = C.Mid, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("¿No tienes código? Pídeselo a quien te compartió la app.", color = C.Low, fontSize = 13.sp)
            }
            Column(
                Modifier.paneWidth(420.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(28.dp),
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
    var createdFor by remember { mutableStateOf("") }
    var createdMax by remember { mutableIntStateOf(1) }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    fun share(code: String, max: Int, name: String, email: Boolean) {
        if (!shareInvite(ctx, code, max, name, email)) msg = "Este aparato no puede compartir; hazlo desde tu celular."
    }
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
        Wrap {
            TvTextField(note, { note = it }, "¿Para quién es? (ej. Primo Luis)", Modifier.paneWidth(300.dp))
            for (n in listOf(1, 2, 3, 5)) Pill("$n ${if (n == 1) "aparato" else "aparatos"}", devices == n, { devices = n })
        }
        Wrap {
            ActionButton("Generar código", {
                scope.launch {
                    val c = Cloud.licCreate(note.trim(), devices)
                    if (c == null) msg = "No se pudo generar (¿sin internet?)" else { created = c; createdFor = note.trim(); createdMax = devices; note = ""; reload() }
                }
            }, icon = Icons.Filled.Add, primary = true)
            ActionButton("Actualizar lista", { reload() }, icon = Icons.Filled.Refresh)
            if (loading) Spinner(size = 22.dp)
        }
        if (created.isNotEmpty()) {
            Column(Modifier.background(C.AccentSoft, RoundedCornerShape(12.dp)).padding(16.dp)) {
                Text("Código nuevo", color = C.Accent, fontSize = 12.sp)
                Text(created, color = C.Hi, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
                Text("Mándalo junto con la liga de descarga: la invitación ya lleva las dos cosas y los pasos.", color = C.Mid, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Wrap {
                    ActionButton("Enviar invitación", { share(created, createdMax, createdFor, false) }, icon = Icons.Filled.Share, primary = true)
                    ActionButton("Por correo", { share(created, createdMax, createdFor, true) }, icon = Icons.Filled.Email)
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
        list.forEach { o ->
            val code = o.optString("code")
            val revoked = o.optBoolean("revoked")
            val devs = o.optJSONArray("devices")
            val used = devs?.length() ?: 0
            Column(Modifier.fillMaxWidth().background(C.Panel2, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(code + "  ·  " + o.optString("note").ifBlank { "sin nombre" }, color = if (revoked) C.Low else C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    val names = devs?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.joinToString(", ") { d -> d.optString("name").ifBlank { "aparato" } + " (visto " + date(d.optString("seen")) + ")" } }.orEmpty()
                    Text(
                        (if (revoked) "DESACTIVADO · " else "") + "$used de ${o.optInt("max")} aparatos" + if (names.isNotBlank()) " · $names" else " · sin usar",
                        color = if (revoked) C.Warn else C.Low, fontSize = 11.sp,
                    )
                }
                Wrap(gap = 8.dp) {
                    if (!revoked) Pill("Enviar invitación", false, { share(code, o.optInt("max"), o.optString("note"), false) })
                    if (!revoked) Pill("Correo", false, { share(code, o.optInt("max"), o.optString("note"), true) })
                    Pill(if (revoked) "Activar" else "Desactivar", false, { scope.launch { Cloud.licSet(code, !revoked, false); reload() } })
                    if (used > 0) Pill("Liberar aparatos", false, { scope.launch { Cloud.licSet(code, revoked, true); reload() } })
                }
            }
        }
        if (list.isEmpty() && !loading && msg.isEmpty()) Text("Aún no has generado códigos.", color = C.Low, fontSize = 12.sp)
    }
}
