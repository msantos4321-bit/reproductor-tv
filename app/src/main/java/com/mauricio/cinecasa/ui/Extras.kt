package com.mauricio.cinecasa.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Calendar

/** Temporizador para apagar: al llegar la hora pregunta "¿Sigues viendo?" y, si nadie responde, cierra la app. */
object SleepTimer {
    var endAt by mutableLongStateOf(0L)
    fun set(minutes: Int) { endAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L }
    fun minutesLeft(): Int = if (endAt <= 0) 0 else ((endAt - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0) + 1
}

@Composable
fun SleepTimerWatcher() {
    val ctx = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    val end = SleepTimer.endAt
    LaunchedEffect(end) {
        if (end <= 0) return@LaunchedEffect
        delay((end - System.currentTimeMillis()).coerceAtLeast(0))
        asking = true
    }
    if (asking) {
        var left by remember { mutableIntStateOf(60) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            runCatching { fr.requestFocus() }
            while (left > 0) { delay(1000); left-- }
            SleepTimer.endAt = 0
            asking = false
            (ctx as? Activity)?.finishAffinity()
        }
        Dialog(onDismissRequest = {}) {
            Column(Modifier.width(440.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Sigues viendo?", color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("El temporizador terminó. La app se cerrará en $left s.", color = C.Mid, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Sigo viendo", { SleepTimer.endAt = 0; asking = false }, Modifier.focusRequester(fr), primary = true)
                    ActionButton("30 min más", { SleepTimer.set(30); asking = false })
                    ActionButton("Cerrar ya", { SleepTimer.endAt = 0; asking = false; (ctx as? Activity)?.finishAffinity() })
                }
            }
        }
    }
}

/** Reloj discreto arriba a la derecha (como en las apps de TV). */
@Composable
fun ClockLabel(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR); val m = cal.get(Calendar.MINUTE)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "a. m." else "p. m."
    val sleep = SleepTimer.minutesLeft()
    Text(
        "%d:%02d %s".format(if (h == 0) 12 else h, m, ampm) + if (sleep > 0) "   ·   se apaga en $sleep min" else "",
        modifier, color = C.Low, fontSize = 13.sp, fontWeight = FontWeight.Medium,
    )
}
