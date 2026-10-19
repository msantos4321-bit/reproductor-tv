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

private enum class RStep { Search, Pick, Enable, Pass, Working, Done }

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
    var searchRun by remember { mutableStateOf(0) }

    fun choose(t: RokuInstaller.Tv) {
        tv = t; msg = ""; pass = sp.getString("pw_${t.ip}", "") ?: ""
        step = RStep.Working
        scope.launch { step = if (RokuInstaller.devModeOn(t.ip)) RStep.Pass else RStep.Enable }
    }

    LaunchedEffect(searchRun) {
        step = RStep.Search; msg = ""
        tvs = RokuInstaller.discover(ctx)
        when (tvs.size) {
            0 -> { step = RStep.Pick; msg = "No encontré ninguna tele Roku. Revisa que la tele esté prendida y que este celular esté en el mismo WiFi que la tele." }
            1 -> choose(tvs[0])
            else -> step = RStep.Pick
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
                    Text(
                        "Solo la primera vez hay que activar el permiso para instalar:\n\n" +
                            "1. Toca «Abrir permiso en la tele». La tele cambia de pantalla sola.\n" +
                            "2. En la tele aparece «Developer Settings». Con el control elige «Enable installer and restart» y luego «I Agree».\n" +
                            "3. La tele te pide una contraseña: inventa una fácil (ej. 1234) y anótala.\n" +
                            "4. La tele se reinicia. Cuando vuelva a prender, toca «Ya lo hice».",
                        color = C.Mid, fontSize = 14.sp,
                    )
                    ActionButton("Abrir permiso en la tele", {
                        val t = tv ?: return@ActionButton
                        msg = "Mira tu tele…"
                        scope.launch { RokuInstaller.openDevScreen(t.ip); msg = "Si no apareció «Developer Settings», tócalo otra vez con la tele en la pantalla de inicio." }
                    }, Modifier.fillMaxWidth(), primary = true)
                    ActionButton("Ya lo hice", { msg = ""; step = RStep.Pass }, Modifier.fillMaxWidth())
                }
                RStep.Pass -> {
                    Text("Tele: ${tv?.name}", color = C.Hi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Escribe la contraseña que creaste en la tele y toca Instalar.", color = C.Mid, fontSize = 14.sp)
                    TvTextField(pass, { pass = it.trim() }, "Contraseña de la tele", Modifier.fillMaxWidth())
                    ActionButton("Instalar CineCasa", {
                        val t = tv ?: return@ActionButton
                        if (pass.isEmpty()) { msg = "Escribe la contraseña."; return@ActionButton }
                        step = RStep.Working; msg = "Instalando… tarda menos de un minuto."
                        scope.launch {
                            when (val r = RokuInstaller.install(t.ip, pass)) {
                                "ok" -> { sp.edit().putString("pw_${t.ip}", pass).apply(); msg = ""; step = RStep.Done }
                                "password" -> { msg = "Esa contraseña no es. Usa la que creaste en la tele."; step = RStep.Pass }
                                else -> { msg = r; step = RStep.Pass }
                            }
                        }
                    }, Modifier.fillMaxWidth(), primary = true)
                    Text("¿No te acuerdas? Vuelve a activar el permiso y crea otra.", color = C.Low, fontSize = 12.sp)
                    ActionButton("Activar el permiso otra vez", { msg = ""; step = RStep.Enable }, Modifier.fillMaxWidth())
                }
                RStep.Done -> {
                    Text("¡Listo! CineCasa ya se abrió en tu tele.", color = C.Good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("En la tele toca «Empezar prueba gratis» o escanea el QR con este celular para poner tus datos.\n\nPara abrirla después, búscala en la pantalla de inicio de la tele. Si algún día desaparece o hay versión nueva, repite esto desde aquí: tu contraseña ya quedó guardada.", color = C.Mid, fontSize = 14.sp)
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
            ActionButton(if (step == RStep.Done) "Terminar" else "Cerrar", onClose, Modifier.fillMaxWidth())
        }
    }
}
