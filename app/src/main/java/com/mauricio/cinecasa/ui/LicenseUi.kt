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
import androidx.compose.runtime.collectAsState
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

const val CONTROL_URL = "https://msantos4321-bit.github.io/reproductor-tv/control/"

/** Para el dueño: abre el Centro de control (panel web) ya con la sesión iniciada. */
@Composable
fun ControlCenterSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    Text(
        "Distribuidores, códigos, pruebas gratis, créditos, renovaciones y reglas de precios, todo en un solo lugar. " +
            "Se abre en el navegador; guárdalo en tu pantalla de inicio para tenerlo como app.",
        color = C.Mid, fontSize = 13.sp, lineHeight = 19.sp,
    )
    Wrap {
        ActionButton(if (busy) "Abriendo…" else "Abrir Centro de control", {
            if (busy) return@ActionButton
            busy = true; msg = ""
            scope.launch {
                val t = Cloud.mgrLink()
                busy = false
                if (t == null) { msg = "No se pudo conectar. Revisa tu internet."; return@launch }
                val ok = try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$CONTROL_URL#e=$t")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
                } catch (_: Exception) { false }
                if (!ok) msg = "Este aparato no tiene navegador. Ábrelo desde tu celular."
            }
        }, icon = Icons.Filled.Key, primary = true)
    }
    if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
}

/** Para clientes: qué tipo de activación tienen y cuándo vence. */
@Composable
fun SubscriptionSection() {
    val info by License.info.collectAsState()
    val i = info
    val fmt = remember { SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "MX")) }
    val iso = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") } }
    LaunchedEffect(Unit) { if (License.code.isNotBlank()) License.info.value = Cloud.licInfo(License.code) }
    if (i == null) { Text("Código ${License.code}", color = C.Mid, fontSize = 14.sp); return }
    val kind = when (i.optString("kind")) { "trial" -> "Prueba gratis"; "gift" -> "Cortesía"; else -> "Anual" }
    val exp = i.optString("expires").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { fmt.format(iso.parse(it.take(19))!!) }.getOrNull() }
    Text("$kind · código ${License.code}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Text(if (exp != null) "Vence el $exp" else "Sin vencimiento", color = C.Mid, fontSize = 13.sp)
    Text("${i.optInt("used")} de ${i.optInt("max")} aparatos en uso", color = C.Mid, fontSize = 13.sp)
    i.optString("seller").takeIf { it.isNotBlank() && it != "null" }?.let { seller ->
        val ph = i.optString("seller_phone").takeIf { it.isNotBlank() && it != "null" }
        Text("Te la vendió: $seller" + (ph?.let { " · $it" } ?: ""), color = C.Low, fontSize = 12.sp)
    }
    if (i.optString("kind") == "trial") Text("Para seguir usándola después de la prueba, pide tu plan anual a quien te la compartió.", color = C.Low, fontSize = 12.sp)
}
