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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Account
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onPair: () -> Unit = {}) {
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val userFr = remember { FocusRequester() }
    val passFr = remember { FocusRequester() }

    fun submit() {
        if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "Llena los tres datos."; detail = ""; return }
        busy = true; error = ""; detail = ""
        scope.launch {
            val acc = Account(server.trim(), user.trim(), pass.trim())
            val result = withContext(Dispatchers.IO) { runCatching { Xtream.Login.login(acc) } }
            busy = false
            result.onSuccess { (okAcc, info) ->
                Prefs.serverFormats = info.formats
                Repo.clearAll()
                Prefs.setAccount(okAcc)
            }.onFailure { e ->
                if (e is Xtream.Login.LoginException) { error = e.title; detail = e.detail }
                else { error = "No se pudo conectar con el servidor."; detail = "${e.javaClass.simpleName}: ${e.message}" }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.Panel2, C.Bg), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        AdaptivePane(56.dp) {
            Column(Modifier.paneWidth(300.dp)) {
                Text("CineCasa", color = C.Hi, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Escribe los datos que te dio tu proveedor. Solo se guardan en esta tele.",
                    color = C.Mid, fontSize = 15.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("Presiona OK sobre cada campo para escribir. También puedes dictar con el micrófono del teclado.", color = C.Low, fontSize = 13.sp)
            }
            Column(
                Modifier.paneWidth(420.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TvTextField(server, { server = it }, "Servidor (ej. http://servidor.com:8080)", Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, leading = Icons.Filled.Dns, boxRequester = first,
                    onImeAction = { runCatching { userFr.requestFocus() } })
                TvTextField(user, { user = it }, "Usuario", Modifier.fillMaxWidth(), imeAction = ImeAction.Next,
                    leading = Icons.Filled.Person, boxRequester = userFr, onImeAction = { runCatching { passFr.requestFocus() } })
                TvTextField(pass, { pass = it }, "Contraseña", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go,
                    leading = Icons.Filled.Lock, boxRequester = passFr, onImeAction = { submit() })
                if (error.isNotEmpty()) Text(error, color = C.Accent, fontSize = 13.sp)
                if (detail.isNotEmpty()) Text("Detalle técnico (mándame foto de esto si sigue fallando):\n$detail", color = C.Low, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(4.dp))
                if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spinner(size = 28.dp); Text("Conectando…", color = C.Mid, fontSize = 14.sp)
                } else ActionButton("Entrar", { submit() }, Modifier.fillMaxWidth(), primary = true)
                ActionButton("Tengo CineCasa en otro aparato", onPair, Modifier.fillMaxWidth())
                ActionButton("Ver demostración", {
                    server = "https://wnwecaapchcfhcljsvli.supabase.co/functions/v1/demo"; user = "demo"; pass = "demo"; submit()
                }, Modifier.fillMaxWidth())
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
