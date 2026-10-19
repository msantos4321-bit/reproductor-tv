package com.mauricio.cinecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.mauricio.cinecasa.BuildConfig
import com.mauricio.cinecasa.data.AccountInfo
import com.mauricio.cinecasa.data.LangFilter
import com.mauricio.cinecasa.data.LoadState
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    val account by Prefs.account.collectAsState()
    val c by Repo.catalog.collectAsState()
    val st by Repo.state.collectAsState()
    var info by remember { mutableStateOf<AccountInfo?>(null) }
    var infoFailed by remember { mutableStateOf(false) }
    var liveFmt by remember { mutableStateOf(Prefs.liveFormat) }
    var adult by remember { mutableStateOf(Prefs.showAdult) }
    var theme by remember { mutableStateOf(Prefs.themeMode) }
    var startLive by remember { mutableStateOf(Prefs.startOnLive) }
    var tmdb by remember { mutableStateOf(Prefs.tmdbKey) }
    var tmdbMsg by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    val me by com.mauricio.cinecasa.data.Profiles.current.collectAsState()
    val isAdmin = me?.admin == true
    var serverDlg by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        info = Repo.accountInfo()
        infoFailed = info == null
        runCatching { firstFr.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = if (LocalCompact.current) 12.dp else 32.dp, end = if (LocalCompact.current) 12.dp else 32.dp, top = 28.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Ajustes", color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Section("Agregar otra tele o celular") {
            Text("Pon CineCasa en otro aparato de tu casa con tu misma cuenta, sin escribir contraseñas: abre la app en el aparato nuevo y escribe aquí el código que te muestra.", color = C.Mid, fontSize = 13.sp)
            ActionButton("Agregar aparato", { addDeviceOpen.value = true }, primary = true)
        }

        if (isAdmin) Section("Tu cuenta") {
            val a = account
            Line("Servidor", a?.base.orEmpty())
            Line("Usuario", a?.user.orEmpty())
            val i = info
            when {
                i != null -> {
                    Line("Estado", (if (i.status.equals("Active", true)) "Activa" else i.status) + if (i.trial) " (prueba)" else "")
                    Line("Vence", i.expires?.let { SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(Date(it)) } ?: "Sin fecha")
                    Line("Pantallas al mismo tiempo", "${i.maxCons} permitidas")
                }
                infoFailed -> Line("Estado", "No se pudo consultar ahora")
                else -> Line("Estado", "Consultando…")
            }
            Line("Catálogo", "${"%,d".format(c.movies.size)} películas · ${"%,d".format(c.shows.size)} series · ${"%,d".format(c.channels.size)} canales")
            if (Prefs.lastRefresh > 0) Line("Actualizado", SimpleDateFormat("d MMM, HH:mm", Locale("es", "MX")).format(Date(Prefs.lastRefresh)))
            Wrap(Modifier.padding(top = 8.dp), 12.dp) {
                ActionButton("Actualizar catálogo", { Repo.start(force = true) }, Modifier.focusRequester(firstFr), icon = Icons.Filled.Refresh)
                ActionButton("Cambiar servidor", { serverDlg = true })
                ActionButton("Cambiar de cuenta", { confirmLogout = true })
                if (st is LoadState.Loading) Text((st as LoadState.Loading).msg, color = C.Mid, fontSize = 13.sp)
            }
            Text("El catálogo se actualiza solo cada 8 horas al abrir la app.", color = C.Low, fontSize = 12.sp)
        }

        if (isAdmin) Section("Perfiles y aparatos") {
            Text("Agrega, renombra o protege con PIN los perfiles de tu casa. Nadie ve qué perfil se está usando ni en qué aparato.", color = C.Mid, fontSize = 13.sp)
            ActionButton("Administrar perfiles", { manageProfilesOpen.value = true }, icon = Icons.Filled.Refresh)
            DevicesSection()
        }
        if (!isAdmin) Text("La cuenta, los perfiles y los aparatos solo los cambia el administrador.", color = C.Low, fontSize = 12.sp)

        val owner by com.mauricio.cinecasa.data.License.owner.collectAsState()
        var asClient by remember { mutableStateOf(false) }
        var previewAct by remember { mutableStateOf(false) }
        if (isAdmin && owner && !asClient) Section("Centro de control") { ControlCenterSection() }
        if ((!owner && com.mauricio.cinecasa.data.License.code.isNotBlank()) || (owner && asClient)) Section("Tu suscripción") { SubscriptionSection(sample = owner) }
        if (isAdmin && owner) Section("Ver como cliente") {
            Text("Revisa lo que ven tus clientes: su sección «Tu suscripción» (pagar, compartir, recomendado) y la pantalla de prueba gratis. Es solo una vista previa: no activa ni cambia nada.", color = C.Mid, fontSize = 13.sp)
            Wrap {
                ActionButton(if (asClient) "Volver a vista de dueño" else "Ver Ajustes como cliente", { asClient = !asClient }, primary = !asClient)
                ActionButton("Ver pantalla de prueba gratis", { previewAct = true })
            }
        }
        if (previewAct) androidx.compose.ui.window.Dialog(onDismissRequest = { previewAct = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { ActivationScreen("", onPair = {}, preview = { previewAct = false }) }
        }

        if (isAdmin && !DeviceUi.realTv) Section("Presentaciones") {
            Text("Muestra en este celular el diseño de la tele (pantalla acostada, sin barras) para grabar videos de demostración con la grabadora de pantalla.", color = C.Mid, fontSize = 13.sp)
            Wrap {
                ActionButton(if (DeviceUi.tvLayout) "Volver al diseño de celular" else "Ver diseño de tele", { DeviceUi.setTvLayout(!DeviceUi.tvLayout) }, primary = !DeviceUi.tvLayout)
            }
        }
        if (isAdmin) Section("Demostración") {
            Text(
                if (Prefs.inDemo) "Estás usando el catálogo de demostración. Tu cuenta real está guardada; regresa a ella cuando termines."
                else "Cambia a un catálogo de demostración con películas libres para grabar videos o enseñar la app. Tu cuenta real se guarda y regresas con un toque.",
                color = C.Mid, fontSize = 13.sp,
            )
            Wrap {
                if (Prefs.inDemo) ActionButton("Volver a mi cuenta", { Repo.clearAll(); Prefs.exitDemo(); Repo.start(force = true) }, primary = true)
                else ActionButton("Usar la demostración", { Repo.clearAll(); Prefs.enterDemo(); Repo.start(force = true) })
            }
        }
        Section("Apariencia") {
            Text("Claro como Apple TV, o grafito suave para la noche. «Automático» cambia a grafito de 8 pm a 7 am.", color = C.Mid, fontSize = 13.sp)
            Wrap {
                for ((k, label) in listOf("light" to "Claro", "dark" to "Grafito", "auto" to "Automático")) {
                    Pill(label, theme == k, { theme = k; Prefs.themeMode = k; C.apply(k) })
                }
            }
        }

        Section("Idioma de películas y series") {
            Text(
                "Cuando una película está varias veces (latino, subtitulada, inglés, 4K…), solo verás una tarjeta; " +
                    "las versiones se eligen dentro de la ficha. Este filtro decide cuáles se muestran y cuál va primero.",
                color = C.Mid, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth(0.85f),
            )
            Wrap {
                LangFilter.presets.forEach { p -> Pill(p.label, LangFilter.current.id == p.id, {
                    LangFilter.set(p.id)
                    com.mauricio.cinecasa.data.Profiles.current.value?.let { cur -> if (!cur.guest) com.mauricio.cinecasa.data.Profiles.save(cur.copy(lang = p.id)) }
                }) }
            }
            val stats = c.langStats.entries.sortedByDescending { it.value }
                .joinToString("  ·  ") { "${it.key.label.ifBlank { "Sin etiqueta" }}: ${"%,d".format(it.value)}" }
            if (stats.isNotBlank()) Text("Detectado en tu catálogo: $stats", color = C.Low, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(0.9f))
            Text("Las que no traen etiqueta de idioma se muestran siempre.", color = C.Low, fontSize = 12.sp)
        }

        Section("Al abrir y al dormir") {
            Text("Qué mostrar al abrir la app:", color = C.Mid, fontSize = 13.sp)
            Wrap {
                Pill("Inicio", !startLive, { startLive = false; Prefs.startOnLive = false })
                Pill("Último canal en vivo", startLive, { startLive = true; Prefs.startOnLive = true })
            }
            Text("Temporizador para apagar (te pregunta antes de cerrar):", color = C.Mid, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Wrap {
                val left = SleepTimer.minutesLeft()
                Pill("Apagado", left == 0, { SleepTimer.set(0) })
                for (m in listOf(30, 60, 90, 120)) Pill("$m min", false, { SleepTimer.set(m) })
            }
            val left = SleepTimer.minutesLeft()
            if (left > 0) Text("Se apagará en $left min.", color = C.Accent, fontSize = 13.sp)
        }

        Section("TV en vivo") {
            Text("Formato de los canales. Si un canal no abre o se corta, prueba el otro.", color = C.Mid, fontSize = 13.sp)
            Wrap {
                for ((k, label) in listOf("auto" to "Automático", "ts" to "MPEG-TS", "m3u8" to "HLS")) {
                    Pill(label, liveFmt == k, { liveFmt = k; Prefs.liveFormat = k })
                }
            }
        }

        if (isAdmin) Section("Contenido para adultos") {
            Text("Las categorías para adultos están ocultas en toda la app, incluida la búsqueda.", color = C.Mid, fontSize = 13.sp)
            Wrap {
                Pill("Ocultar", !adult, { adult = false; Prefs.showAdult = false; Repo.refilter() })
                Pill("Mostrar", adult, { adult = true; Prefs.showAdult = true; Repo.refilter() })
            }
        }

        if (isAdmin) Section("Búsqueda por actor (opcional)") {
            Text(
                "Tu proveedor casi nunca etiqueta el reparto de cada película. Con una clave gratuita de TMDB (themoviedb.org) " +
                    "puedes buscar un actor o director y ver todas sus películas y series que estén en tu catálogo. " +
                    "También sirve para encontrar tráilers que el proveedor no trae.",
                color = C.Mid, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth(0.85f),
            )
            Text("Cómo obtenerla: crea una cuenta en themoviedb.org → Ajustes → API → solicita una clave (uso personal). Copia la «API Key».", color = C.Low, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(0.85f))
            Wrap(gap = 12.dp) {
                TvTextField(tmdb, { tmdb = it }, "Clave de API de TMDB (v3)", Modifier.paneWidth(420.dp), leading = Icons.Filled.Key)
                ActionButton("Guardar", {
                    scope.launch {
                        if (tmdb.isBlank()) { Prefs.tmdbKey = ""; tmdbMsg = "Clave borrada."; return@launch }
                        tmdbMsg = "Probando clave…"
                        val ok = withContext(Dispatchers.IO) { Tmdb.test(tmdb.trim()) }
                        if (ok) { Prefs.tmdbKey = tmdb.trim(); tmdbMsg = "Clave guardada. Ya puedes buscar por actor." }
                        else tmdbMsg = "La clave no funcionó. Revisa que sea la «API Key» (v3)."
                    }
                })
            }
            if (tmdbMsg.isNotEmpty()) Text(tmdbMsg, color = if (tmdbMsg.startsWith("Clave guardada")) C.Good else C.Warn, fontSize = 13.sp)
        }

        Section("Actualizaciones") {
            Text("La app revisa sola si hay versión nueva cada vez que la abres. También puedes revisar aquí.", color = C.Mid, fontSize = 13.sp)
            Wrap(gap = 12.dp) {
                ActionButton("Buscar actualización", {
                    updateDialogOpen.value = true
                    scope.launch { com.mauricio.cinecasa.data.Updater.check() }
                }, icon = Icons.Filled.Refresh)
                Text("Tienes la versión ${BuildConfig.VERSION_NAME}", color = C.Low, fontSize = 13.sp)
            }
        }

        Section("Acerca de") {
            Line("App", "CineCasa ${BuildConfig.VERSION_NAME}")
            Text("Funciona con cuentas tipo Xtream Codes (servidor, usuario y contraseña).", color = C.Low, fontSize = 12.sp)
        }
    }

    if (serverDlg) ServerDialog { serverDlg = false }
    if (confirmLogout) {
        ConfirmDialog(
            title = "¿Cambiar de cuenta?",
            message = "Se borrarán el servidor, usuario y contraseña de esta tele. Tu lista y lo que ibas viendo se conservan.",
            confirm = "Sí, cambiar",
            onConfirm = { confirmLogout = false; Repo.logout() },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    val compact = LocalCompact.current
    Row {
        Text(label, Modifier.width(if (compact) 112.dp else 170.dp), color = C.Low, fontSize = 14.sp)
        Text(value, Modifier.weight(1f), color = C.Hi, fontSize = 14.sp)
    }
}

/**
 * Cambiar solo la dirección del servidor (los proveedores la cambian seguido).
 * Conserva usuario y contraseña, prueba la nueva dirección y recarga el catálogo.
 */
@Composable
fun ServerDialog(onClose: () -> Unit) {
    val acc = Prefs.account.value ?: return
    var server by remember { mutableStateOf(acc.server) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.cardWidth(520.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cambiar servidor", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Escribe la nueva dirección que te dio tu proveedor. Tu usuario y contraseña se conservan.", color = C.Mid, fontSize = 14.sp)
            TvTextField(server, { server = it }, "http://servidor.com:8080", Modifier.fillMaxWidth(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, boxRequester = fr)
            if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
            Wrap(gap = 12.dp) {
                if (busy) { Spinner(size = 26.dp); Text("Probando…", color = C.Mid, fontSize = 14.sp) }
                else {
                    ActionButton("Guardar y probar", {
                        busy = true; msg = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching { com.mauricio.cinecasa.data.Xtream.Login.login(acc.copy(server = server.trim())) }
                            }
                            busy = false
                            r.onSuccess { (ok, info) ->
                                Prefs.serverFormats = info.formats
                                Repo.clearAll()
                                Prefs.setAccount(ok)
                                Repo.start(force = true)
                                onClose()
                            }.onFailure { e ->
                                msg = (e as? com.mauricio.cinecasa.data.Xtream.Login.LoginException)?.let { it.title + "\n" + it.detail } ?: (e.message ?: "No funcionó")
                            }
                        }
                    }, primary = true)
                    ActionButton("Cancelar", onClose)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}
