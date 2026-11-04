package com.mauricio.cinecasa.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.mauricio.cinecasa.BuildConfig
import com.mauricio.cinecasa.data.Diagnose
import kotlinx.coroutines.delay
import org.json.JSONObject

// ======================================================================
// Contacto con el vendedor: pagar y WhatsApp. En la tele, con QR (la tele no abre WhatsApp).
// ======================================================================

fun qrBitmap(text: String, size: Int): android.graphics.Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 0))
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888)
}

@Composable
fun QrImage(text: String, size: Dp = 170.dp, label: String = "") {
    val bmp = remember(text) { runCatching { qrBitmap(text, 420) }.getOrNull() } ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
            Image(bmp.asImageBitmap(), label.ifBlank { "Código QR" }, Modifier.fillMaxSize())
        }
        if (label.isNotBlank()) Text(label, color = C.Mid, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).width(size))
    }
}

/** Teléfono en formato de WhatsApp (52 + 10 dígitos en México). "" si no sirve. */
fun waPhone(raw: String?): String {
    val d = raw.orEmpty().filter { it.isDigit() }
    return when {
        d.length == 10 -> "52$d"
        d.length in 11..15 -> d
        else -> ""
    }
}

fun waUrl(phone: String, text: String) = "https://wa.me/$phone?text=" + Uri.encode(text)

/** Abre una liga; false si el aparato no tiene con qué abrirla. */
fun openUrl(ctx: Context, url: String): Boolean =
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

private fun JSONObject.s(k: String) = optString(k).takeIf { it.isNotBlank() && it != "null" }

/**
 * Botones para pagar y escribir al vendedor (o al gerente si no hay vendedor).
 * [h] trae help_phone, pay_link, price y seller (de cc_lic_help, cc_lic_info o cc_sales_contact).
 * En una tele de verdad muestra códigos QR para el celular.
 */
@Composable
fun ContactActions(h: JSONObject?, waText: String, payLabel: String = "Pagar", showPrice: Boolean = true, firstFocus: FocusRequester? = null, showSeller: Boolean = true) {
    val ctx = LocalContext.current
    if (h == null) return
    val phone = waPhone(h.s("help_phone"))
    // En Google Play no se manda a pagar fuera de la tienda
    val pay = h.s("pay_link")?.takeIf { it.startsWith("http") && !BuildConfig.PLAY }
    val price = h.optDouble("price", 0.0).toInt()
    var note by remember { mutableStateOf("") }
    if (showPrice && price > 0 && !BuildConfig.PLAY)
        Text(if (payLabel.contains("renov", true)) "Renovación por un año: $$price." else "Plan anual desde $$price al año.", color = C.Hi, fontSize = 14.sp)
    if (showSeller) h.s("seller")?.let { Text("Te atiende: $it", color = C.Low, fontSize = 12.sp) }
    if (phone.isEmpty() && pay == null) {
        Text("Escríbele a quien te compartió CineCasa.", color = C.Low, fontSize = 13.sp)
        return
    }
    if (DeviceUi.realTv) {
        Text("Escanea con la cámara de tu celular:", color = C.Mid, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (pay != null) QrImage(pay, 150.dp, payLabel)
            if (phone.isNotEmpty()) QrImage(waUrl(phone, waText), 150.dp, "WhatsApp")
        }
        if (phone.isNotEmpty()) Text("O escribe al WhatsApp ${phone.removePrefix("52")}", color = C.Low, fontSize = 12.sp)
    } else {
        Wrap {
            if (pay != null) ActionButton(payLabel, { if (!openUrl(ctx, pay)) note = "Abre esta liga: $pay" },
                if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier, primary = true)
            if (phone.isNotEmpty()) ActionButton("Escribir por WhatsApp", { if (!openUrl(ctx, waUrl(phone, waText))) note = "Escribe al WhatsApp ${phone.removePrefix("52")}" },
                if (firstFocus != null && pay == null) Modifier.focusRequester(firstFocus) else Modifier, primary = pay == null)
        }
    }
    if (note.isNotEmpty()) Text(note, color = C.Warn, fontSize = 13.sp)
}

// ======================================================================
// «Revisar mi servicio»
// ======================================================================

@Composable
fun ServiceCheckDialog(onClose: () -> Unit) {
    var res by remember { mutableStateOf<Diagnose.Result?>(null) }
    var run by remember { mutableStateOf(0) }
    var help by remember { mutableStateOf<JSONObject?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(run) {
        res = null
        res = Diagnose.run()
        if (help == null) help = com.mauricio.cinecasa.data.License.helpInfo()
    }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.cardWidth(560.dp).background(C.Panel, RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Revisar mi servicio", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val r = res
            if (r == null) {
                Row(verticalAlignment = Alignment.CenterVertically) { Spinner(size = 24.dp); Text("  Revisando internet, tu activación y tu proveedor…", color = C.Mid, fontSize = 14.sp) }
                ActionButton("Cerrar", onClose, Modifier.focusRequester(fr))
            } else {
                r.steps.forEach { st ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(if (st.ok == true) "✔" else if (st.ok == false) "✘" else "·", color = if (st.ok == true) C.Good else if (st.ok == false) C.Warn else C.Low,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                        Column {
                            Text(st.label, color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(st.detail, color = C.Mid, fontSize = 13.sp)
                        }
                    }
                }
                Text(r.verdict, color = if (r.allOk) C.Good else C.Warn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Wrap {
                    ActionButton("Revisar otra vez", { run++ }, Modifier.focusRequester(fr), primary = true)
                    ActionButton("Cerrar", onClose)
                }
                if (!r.allOk && help != null) {
                    Text("¿Sigue sin funcionar? Manda este resultado a quien te vendió CineCasa:", color = C.Low, fontSize = 12.sp)
                    ContactActions(help, r.summary().take(600), showPrice = false)
                }
            }
        }
    }
    LaunchedEffect(res == null) { delay(80); runCatching { fr.requestFocus() } }
}
