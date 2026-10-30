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
fun ActivationScreen(msg: String, onPair: () -> Unit, preview: (() -> Unit)? = null) {
    var code by remember { mutableStateOf(License.code) }
    var ref by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(msg) }
    var trialGone by remember { mutableStateOf(false) }
    var withCode by remember { mutableStateOf(License.code.isNotBlank()) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    fun go() {
        if (preview != null) { error = "Vista previa: aquí el cliente activaría su código."; return }
        if (code.isBlank()) { error = "Escribe tu código."; return }
        busy = true; error = ""
        scope.launch { error = License.activate(code) ?: ""; busy = false }
    }
    fun trial() {
        if (preview != null) { error = "Vista previa: aquí el cliente recibiría su prueba gratis de 21 días."; return }
        busy = true; error = ""
        scope.launch {
            val e = License.startTrial(ref)
            busy = false
            if (e != null) { error = e; if (e.contains("ya usó")) { trialGone = true; withCode = true } }
        }
    }
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        AdaptivePane(56.dp) {
            Column(Modifier.paneWidth(320.dp)) {
                Text("CINECASA", color = C.Hi, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
                Spacer(Modifier.height(12.dp))
                Text(if (withCode) "Activa este aparato con el código que te dieron." else "Pruébala gratis 21 días en hasta 5 aparatos. No necesitas código.", color = C.Mid, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("CineCasa es un reproductor: funciona con el servicio que tú ya tienes.", color = C.Low, fontSize = 13.sp)
            }
            Column(
                Modifier.paneWidth(420.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!withCode) {
                    TvTextField(ref, { ref = it.uppercase() }, "¿Quién te recomendó? Su código (opcional)", Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Go, leading = Icons.Filled.Share, boxRequester = fr, onImeAction = { trial() })
                    if (error.isNotEmpty()) Text(error, color = C.Warn, fontSize = 13.sp)
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) { Spinner(size = 24.dp); Text("  Activando tu prueba…", color = C.Mid, fontSize = 14.sp) }
                    else ActionButton("Empezar prueba gratis", { trial() }, Modifier.fillMaxWidth(), primary = true)
                    ActionButton("Ya tengo un código", { withCode = true; error = "" }, Modifier.fillMaxWidth())
                } else {
                    TvTextField(code, { code = it.uppercase() }, "Código (ej. CC-7F3K-92QD)", Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Go, leading = Icons.Filled.Key, boxRequester = fr, onImeAction = { go() })
                    if (error.isNotEmpty()) Text(error, color = C.Warn, fontSize = 13.sp)
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) { Spinner(size = 24.dp); Text("  Activando…", color = C.Mid, fontSize = 14.sp) }
                    else ActionButton("Activar", { go() }, Modifier.fillMaxWidth(), primary = true)
                    if (!trialGone) ActionButton("Quiero la prueba gratis", { withCode = false; error = "" }, Modifier.fillMaxWidth())
                }
                if (preview != null) ActionButton("Salir de la vista previa", preview, Modifier.fillMaxWidth(), primary = true)
                else ActionButton("Tengo CineCasa en otro aparato", onPair, Modifier.fillMaxWidth())
            }
        }
    }
    LaunchedEffect(withCode) { delay(80); runCatching { fr.requestFocus() } }
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
fun SubscriptionSection(sample: Boolean = false) {
    val info by License.info.collectAsState()
    // Vista previa del dueño: datos de ejemplo de un cliente en prueba
    val i = if (sample) remember { JSONObject().put("kind", "trial").put("max", 5).put("used", 2).put("price", 169)
        .put("expires", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(System.currentTimeMillis() + 21L * 86400000))).put("help_phone", "").put("pay_link", "https://mpago.la/ejemplo")
        .put("referral_days", 30).put("download_url", "https://msantos4321-bit.github.io/reproductor-tv/app/") } else info
    val fmt = remember { SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "MX")) }
    val iso = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") } }
    LaunchedEffect(Unit) { if (!sample && License.code.isNotBlank()) License.info.value = Cloud.licInfo(License.code) }
    if (i == null) { Text("Código ${License.code}", color = C.Mid, fontSize = 14.sp); return }
    if (sample) Text("Vista previa con datos de ejemplo", color = C.Warn, fontSize = 12.sp)
    val kind = when (i.optString("kind")) { "trial" -> "Prueba gratis"; "gift" -> "Cortesía"; else -> "Anual" }
    val exp = i.optString("expires").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { fmt.format(iso.parse(it.take(19))!!) }.getOrNull() }
    Text("$kind · código ${if (sample) "CC-EJEM-PL0S" else License.code}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Text(if (exp != null) "Vence el $exp" else "Sin vencimiento", color = C.Mid, fontSize = 13.sp)
    Text("${i.optInt("used")} de ${i.optInt("max")} aparatos en uso", color = C.Mid, fontSize = 13.sp)
    if (!sample && License.code.isNotBlank()) MyDevices(License.code)
    i.optString("seller").takeIf { it.isNotBlank() && it != "null" }?.let { seller ->
        val ph = i.optString("seller_phone").takeIf { it.isNotBlank() && it != "null" }
        Text("Te la vendió: $seller" + (ph?.let { " · $it" } ?: ""), color = C.Low, fontSize = 12.sp)
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val code = if (sample) "CC-EJEM-PL0S" else License.code
    val price = i.optDouble("price", 0.0).toInt()
    // En Google Play no se manda a pagar fuera de la tienda: solo contacto con el vendedor
    val pay = i.optString("pay_link").takeIf { it.startsWith("http") && !com.mauricio.cinecasa.BuildConfig.PLAY }
    val help = i.optString("help_phone").filter { it.isDigit() }.let { if (it.length == 10) "52$it" else it }
    fun open(u: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    var note by remember { mutableStateOf("") }
    val isTrial = i.optString("kind") == "trial"
    if (i.optString("kind") != "gift") {
        if (!com.mauricio.cinecasa.BuildConfig.PLAY) Text(if (isTrial) "Plan anual desde $$price al año (1 aparato)." else "Renovación por un año: $$price.", color = C.Hi, fontSize = 14.sp)
        else Text(if (isTrial) "Para seguir después de la prueba, habla con quien te compartió CineCasa." else "Para renovar, habla con quien te compartió CineCasa.", color = C.Hi, fontSize = 14.sp)
        Wrap {
            if (pay != null) ActionButton(if (isTrial) "Pagar plan anual" else "Pagar renovación", { if (!open(pay)) note = "Abre esta liga en tu celular: $pay" }, primary = true)
            if (help.length >= 10) ActionButton("Escribir por WhatsApp", {
                val t = if (com.mauricio.cinecasa.BuildConfig.PLAY) "Hola, te escribo por CineCasa. Mi código es $code."
                    else (if (isTrial) "Hola, quiero contratar el plan anual de CineCasa. Mi código es $code." else "Hola, quiero renovar CineCasa. Mi código es $code.") + (if (pay != null) " Ya hice el pago." else "")
                if (!open("https://wa.me/$help?text=" + Uri.encode(t))) note = "Escribe al WhatsApp $help"
            }, primary = pay == null)
        }
        Text(if (pay != null) "Después de pagar avisa por WhatsApp y tu código queda activo; no tienes que reinstalar nada." else "Tu código sigue siendo el mismo; no tienes que reinstalar nada.", color = C.Low, fontSize = 12.sp)
    }
    // Recomienda y gana
    val days = i.optInt("referral_days", 30)
    val prize = if (days % 30 == 0) "${days / 30} ${if (days == 30) "mes" else "meses"}" else "$days días"
    Spacer(Modifier.height(6.dp))
    Text("Recomienda y gana $prize", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Text("Comparte CineCasa. Cuando alguien contrate y escriba tu código $code en «¿Quién te recomendó?», te regalamos $prize.", color = C.Low, fontSize = 12.sp)
    Wrap {
        ActionButton("Compartir CineCasa", {
            val dl = i.optString("download_url")
            val t = if (com.mauricio.cinecasa.BuildConfig.PLAY) "Te recomiendo CineCasa, un reproductor muy fácil para la tele y el celular. Búscala en Google Play como «CineCasa» y pruébala gratis 21 días. Al abrirla, en «¿Quién te recomendó?» escribe: $code"
                else "Te recomiendo CineCasa, un reproductor muy fácil para la tele y el celular. Pruébalo gratis 21 días: $dl\nEn la tele usa la app Downloader con el número 8835880. Al abrirla, en «¿Quién te recomendó?» escribe: $code"
            runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Compartir CineCasa").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, icon = Icons.Filled.Share)
    }
    if (i.optString("ref_by").let { it.isBlank() || it == "null" }) {
        var ref by remember { mutableStateOf("") }
        Text("¿Alguien te recomendó? Escribe su código y le regalamos $prize cuando contrates.", color = C.Low, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTextField(ref, { ref = it.uppercase() }, "Código de quien te recomendó", Modifier.width(260.dp))
            ActionButton("Guardar", {
                if (ref.isBlank()) return@ActionButton
                if (sample) { note = "Vista previa: aquí se registraría quién lo recomendó."; return@ActionButton }
                scope.launch {
                    note = when (Cloud.setRef(code, ref)) {
                        "ok" -> { License.info.value = Cloud.licInfo(code); "Listo, quedó registrado. ¡Gracias!" }
                        "bad_ref" -> "Ese código no existe. Revísalo."
                        "self" -> "No puedes poner tu propio código."
                        "already" -> "Ya tenías registrado a quien te recomendó."
                        null -> "Sin conexión."
                        else -> "No se pudo guardar."
                    }
                }
            })
        }
    }
    if (note.isNotEmpty()) Text(note, color = C.Warn, fontSize = 13.sp)
}


/**
 * «Mis aparatos»: los aparatos activados con el mismo código. Se puede quitar uno solo
 * (p. ej. un celular perdido): deja de funcionar en unos minutos y su lugar queda libre.
 */
@Composable
private fun MyDevices(code: String) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<JSONObject?>(null) }
    var toRename by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(code) { data = Cloud.licDevices(code) }
    val d = data ?: return
    val me = d.optString("me")
    val devs = d.optJSONArray("devices")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
    val blocked = d.optJSONArray("blocked")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
    Spacer(Modifier.height(6.dp))
    Text("Mis aparatos", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Text("¿Perdiste un celular o ya no usas una tele? Quítalo aquí: deja de funcionar en unos minutos, se borran ahí los datos de tu cuenta y el lugar queda libre para otro aparato.", color = C.Low, fontSize = 12.sp)
    devs.forEach { x ->
        val key = x.optString("key"); val mine = key == me
        Row(Modifier.fillMaxWidth().background(C.Panel2, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(x.optString("name").ifBlank { "Aparato" } + if (mine) "  (este)" else "", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(listOf(devState(x), x.optString("model").takeIf { it.isNotBlank() && it != x.optString("name") }).filterNotNull().joinToString(" · "), color = C.Low, fontSize = 11.sp)
            }
            Wrap {
                Pill("Renombrar", false, { toRename = x })
                if (!mine) Pill("Quitar", false, { toRemove = x })
            }
        }
    }
    blocked.forEach { x ->
        Row(Modifier.fillMaxWidth().background(C.Panel2, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(x.optString("name").ifBlank { "Aparato" }, color = C.Mid, fontSize = 14.sp)
                Text("Quitado: ya no puede usar tu código", color = C.Low, fontSize = 11.sp)
            }
            Pill("Permitir otra vez", false, {
                scope.launch {
                    msg = if (Cloud.licDeviceAllow(code, x.optString("key")) == "ok") "Listo: ese aparato puede volver a entrar con tu código." else "Sin conexión. Intenta de nuevo."
                    data = Cloud.licDevices(code) ?: data
                }
            })
        }
    }
    if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
    toRemove?.let { x ->
        ConfirmDialog(
            title = "¿Quitar «${x.optString("name").ifBlank { "Aparato" }}»?",
            message = "Deja de funcionar en unos minutos y no podrá volver a usar tu código (a menos que lo permitas otra vez). Tus demás aparatos siguen igual.",
            confirm = "Sí, quitar",
            onConfirm = {
                toRemove = null
                scope.launch {
                    val key = x.optString("key")
                    msg = when (Cloud.licDeviceRemove(code, key)) {
                        "ok" -> {
                            // También sale del hogar (perfiles y lo que ibas viendo)
                            com.mauricio.cinecasa.data.Devices.list.value.keys.firstOrNull { Cloud.deviceKey(it) == key }?.let { Cloud.removeDevice(it) }
                            "Aparato quitado. Ya tienes un lugar libre."
                        }
                        "self" -> "Este aparato no se puede quitar desde aquí mismo; hazlo desde otro."
                        "gone" -> "Ese aparato ya no estaba."
                        null -> "Sin conexión. Intenta de nuevo."
                        else -> "No se pudo quitar."
                    }
                    data = Cloud.licDevices(code) ?: data
                }
            },
            onDismiss = { toRemove = null },
        )
    }
    toRename?.let { x ->
        var name by remember(x) { mutableStateOf(x.optString("name")) }
        val fr = remember { FocusRequester() }
        androidx.compose.ui.window.Dialog(onDismissRequest = { toRename = null }) {
            Column(Modifier.cardWidth(460.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nombre del aparato", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TvTextField(name, { name = it.take(40) }, "Sala, Recámara, Celular…", Modifier.fillMaxWidth(), boxRequester = fr)
                ActionButton("Guardar", {
                    toRename = null
                    scope.launch {
                        val key = x.optString("key")
                        if (Cloud.licDeviceRename(code, key, name) == "ok" && key == Cloud.deviceKey()) Cloud.deviceName = name
                        data = Cloud.licDevices(code) ?: data
                    }
                }, primary = true)
            }
        }
        LaunchedEffect(x) { delay(80); runCatching { fr.requestFocus() } }
    }
}

/** «Viendo ahora», «App abierta ahora» o «Visto hace 2 h». */
private fun devState(x: JSONObject): String {
    when (x.optString("state")) { "viendo" -> return "Viendo ahora"; "abierta" -> return "Abierta ahora" }
    val t = x.optString("seen").takeIf { it.length >= 19 } ?: return ""
    val ms = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(t.take(19))!!.time }.getOrNull() ?: return ""
    val min = (System.currentTimeMillis() - ms) / 60_000
    return "Visto " + when {
        min < 60 -> "hace ${min.coerceAtLeast(1)} min"
        min < 1440 -> "hace ${min / 60} h"
        min < 2880 -> "ayer"
        else -> "hace ${min / 1440} días"
    }
}
