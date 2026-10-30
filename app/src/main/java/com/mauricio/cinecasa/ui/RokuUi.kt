package com.mauricio.cinecasa.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.RokuInstaller
import kotlinx.coroutines.launch

/** Abre el asistente "Instalar CineCasa en una tele Roku". */
val rokuOpen = mutableStateOf(false)

private enum class RStep { Search, Pick, Enable, Wait, Pass, Working, Done }

/** Datos que el celular le pasa a la tele para que entre sola (código, proveedor y cuenta de la casa). */
private fun launchParams(): Map<String, String> {
    val a = com.mauricio.cinecasa.data.Prefs.account.value
    val real = a != null && !com.mauricio.cinecasa.data.Prefs.inDemo
    val cl = com.mauricio.cinecasa.data.Cloud
    return buildMap {
        put("lic", com.mauricio.cinecasa.data.License.code)
        if (real) {
            put("srv", a!!.base); put("usr", a.user); put("pwd", a.pass)
            if (com.mauricio.cinecasa.data.Prefs.vendorServer) put("vs", "1")
        }
        if (cl.hasHousehold) { put("hh", cl.householdId); put("hs", cl.householdSecret) }
    }
}

@Composable
fun RokuInstallDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("roku", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(RStep.Search) }
    var tvs by remember { mutableStateOf(listOf<RokuInstaller.Tv>()) }
    var tv by remember { mutableStateOf<RokuInstaller.Tv?>(null) }
    var pass by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var blocked by remember { mutableStateOf(false) }
    var searchRun by remember { mutableStateOf(0) }
    val info by com.mauricio.cinecasa.data.License.info.collectAsState()
    val help = info?.optString("help_phone").orEmpty().filter { it.isDigit() }.let { if (it.length == 10) "52$it" else it }

    fun doInstall(t: RokuInstaller.Tv) {
        step = RStep.Working; msg = "Instalando… tarda menos de un minuto."
        scope.launch {
            when (val r = RokuInstaller.install(t.ip, pass)) {
                "ok" -> {
                    sp.edit().putString("pw_${t.ip}", pass).apply()
                    msg = "Abriendo CineCasa en la tele con tus datos…"
                    RokuInstaller.launchWith(t.ip, launchParams())
                    msg = ""; step = RStep.Done
                }
                "password" -> { msg = "La tele no aceptó la contraseña. Escribe la que pusiste en la tele."; step = RStep.Pass }
                else -> { msg = r; step = RStep.Pass }
            }
        }
    }

    fun choose(t: RokuInstaller.Tv) {
        tv = t; msg = ""; blocked = false
        pass = sp.getString("pw_${t.ip}", "")?.ifBlank { null } ?: sp.getString("pw_last", "")?.ifBlank { null }
            ?: (1000 + java.util.Random().nextInt(9000)).toString().also { sp.edit().putString("pw_last", it).apply() }
        step = RStep.Working
        scope.launch {
            if (RokuInstaller.devModeOn(t.ip)) {
                // Ya tiene el permiso: si recordamos la contraseña, instala directo
                if (sp.contains("pw_${t.ip}")) doInstall(t) else step = RStep.Pass
            } else step = RStep.Enable
        }
    }

    LaunchedEffect(searchRun) {
        step = RStep.Search; msg = ""
        tvs = RokuInstaller.discover(ctx)
        when (tvs.size) {
            0 -> { step = RStep.Pick; msg = "No encontré ninguna tele Roku. Revisa que la tele esté prendida y que este celular esté en el mismo WiFi que la tele (no en datos móviles)." }
            1 -> choose(tvs[0])
            else -> step = RStep.Pick
        }
    }
    // Mientras la tele se reinicia, el celular la espera y en cuanto vuelve instala solo
    LaunchedEffect(step) {
        val t = tv
        if (step == RStep.Wait && t != null) {
            if (RokuInstaller.waitDevMode(t.ip)) { if (step == RStep.Wait) doInstall(t) }
            else if (step == RStep.Wait) { msg = "La tele no regresó con el permiso activado. Repite el paso 1."; step = RStep.Enable }
        }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.cardWidth(520.dp).background(C.Panel, RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Instalar CineCasa en una tele Roku", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            when (step) {
                RStep.Search, RStep.Working -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 24.dp)
                    Text(if (step == RStep.Search) "Buscando teles Roku en tu WiFi…" else "Un momento…", color = C.Mid, fontSize = 14.sp)
                }
                RStep.Pick -> {
                    if (tvs.isNotEmpty()) Text("Elige tu tele:", color = C.Mid, fontSize = 14.sp)
                    tvs.forEach { t -> ActionButton("📺  ${t.name}", { choose(t) }, Modifier.fillMaxWidth(), primary = true) }
                    ActionButton("Buscar otra vez", { searchRun++ }, Modifier.fillMaxWidth())
                }
                RStep.Enable -> {
                    Text("Tele: ${tv?.name}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Solo la primera vez. Ten a la mano el control de la tele.", color = C.Mid, fontSize = 14.sp)
                    Text("1. Toca el botón azul. La tele cambia de pantalla sola a «Developer Settings».", color = C.Hi, fontSize = 14.sp)
                    ActionButton("Abrir permiso en la tele", {
                        val t = tv ?: return@ActionButton
                        msg = "Mira tu tele…"
                        scope.launch {
                            msg = when (RokuInstaller.openDevScreen(t.ip)) {
                                "blocked" -> { blocked = true; "" }
                                "offline" -> "No hay conexión con la tele. Revisa que esté prendida y en el mismo WiFi."
                                else -> "Si no apareció «Developer Settings», regresa la tele a la pantalla de inicio y toca otra vez el botón."
                            }
                        }
                    }, Modifier.fillMaxWidth(), primary = true)
                    if (blocked) Text(
                        "Tu tele no deja que el celular la controle. En la tele ve a Configuración → Sistema → Configuración avanzada del sistema → Control por aplicaciones móviles → Acceso a la red → «Predeterminado» (o «Permisivo»). Luego toca otra vez el botón azul.",
                        color = C.Warn, fontSize = 13.sp,
                    )
                    Text("2. En la tele, con el control, elige «Enable installer and restart» y luego «I Agree».", color = C.Hi, fontSize = 14.sp)
                    Text("3. Cuando la tele muestre el teclado para la contraseña, toca este botón y la escribe sola:", color = C.Hi, fontSize = 14.sp)
                    ActionButton("Escribir la contraseña en la tele ($pass)", {
                        val t = tv ?: return@ActionButton
                        scope.launch { RokuInstaller.typeText(t.ip, pass); msg = "Listo. Ahora en la tele elige «Set password and reboot»." }
                    }, Modifier.fillMaxWidth())
                    Text("4. En la tele elige «Set password and reboot». Se reinicia sola.", color = C.Hi, fontSize = 14.sp)
                    ActionButton("Ya se está reiniciando", { msg = ""; sp.edit().putString("pw_${tv?.ip}", pass).apply(); step = RStep.Wait }, Modifier.fillMaxWidth(), primary = true)
                    Text("Si la tele ya tenía una contraseña de antes, toca «Ya tengo contraseña».", color = C.Low, fontSize = 12.sp)
                    ActionButton("Ya tengo contraseña", { msg = ""; pass = ""; step = RStep.Pass }, Modifier.fillMaxWidth())
                }
                RStep.Wait -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spinner(size = 24.dp)
                        Text("Esperando a que la tele vuelva a prender… En cuanto regrese, CineCasa se instala sola. No cierres esta ventana.", color = C.Mid, fontSize = 14.sp)
                    }
                }
                RStep.Pass -> {
                    Text("Tele: ${tv?.name}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Escribe la contraseña que pusiste en la tele y toca Instalar.", color = C.Mid, fontSize = 14.sp)
                    TvTextField(pass, { pass = it.trim() }, "Contraseña de la tele", Modifier.fillMaxWidth())
                    ActionButton("Instalar CineCasa", {
                        val t = tv ?: return@ActionButton
                        if (pass.isEmpty()) { msg = "Escribe la contraseña."; return@ActionButton }
                        doInstall(t)
                    }, Modifier.fillMaxWidth(), primary = true)
                    Text("¿No te acuerdas? Vuelve a activar el permiso y pon una nueva.", color = C.Low, fontSize = 12.sp)
                    ActionButton("Activar el permiso otra vez", { msg = ""; pass = (1000 + java.util.Random().nextInt(9000)).toString(); step = RStep.Enable }, Modifier.fillMaxWidth())
                }
                RStep.Done -> {
                    Text("¡Listo! CineCasa ya se abrió en tu tele.", color = C.Good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("La tele entra sola con los datos de este celular. Si te pide algo, escanea su QR con este celular.\n\nPara abrirla después, búscala en la pantalla de inicio de la tele. Si algún día desaparece o hay versión nueva, repite esto desde aquí: ya no tendrás que hacer los pasos de la primera vez.", color = C.Mid, fontSize = 14.sp)
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
            if (step != RStep.Done && help.length >= 10) ActionButton("¿Atorado? Pedir ayuda por WhatsApp", {
                val t = "Hola, estoy instalando CineCasa en mi tele Roku (${tv?.name ?: "sin encontrar"}) y me atoré" +
                    (if (msg.isNotBlank()) ": $msg" else ".") + (com.mauricio.cinecasa.data.License.code.takeIf { it.isNotBlank() }?.let { " Mi código es $it." } ?: "")
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$help?text=" + android.net.Uri.encode(t))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Modifier.fillMaxWidth())
            ActionButton(if (step == RStep.Done) "Terminar" else "Cerrar", onClose, Modifier.fillMaxWidth())
        }
    }
}
