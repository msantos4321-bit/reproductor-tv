package com.mauricio.cinecasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon

import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Account
import com.mauricio.cinecasa.data.Cloud
import com.mauricio.cinecasa.data.Devices
import com.mauricio.cinecasa.data.License
import com.mauricio.cinecasa.data.Net
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Profile
import com.mauricio.cinecasa.data.Profiles
import com.mauricio.cinecasa.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Abre "Administrar perfiles" desde cualquier lado (p. ej. Ajustes). */
val manageProfilesOpen = androidx.compose.runtime.mutableStateOf(false)

@Composable
fun Avatar(p: Profile, size: Dp = 96.dp) {
    Box(Modifier.size(size).background(Color(Profiles.colors[p.color.mod(Profiles.colors.size)]), CircleShape), contentAlignment = Alignment.Center) {
        when {
            p.guest -> Icon(Icons.Filled.PersonOutline, null, Modifier.size(size * 0.5f), tint = Color.White)
            p.private -> Icon(Icons.Filled.VisibilityOff, null, Modifier.size(size * 0.45f), tint = Color.White)
            else -> Text(p.initial, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfileTile(p: Profile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = CircleShape, inset = 4.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        ) { Box(Modifier.padding(6.dp)) { Avatar(p) } }
        Spacer(Modifier.height(10.dp))
        Text(p.name, color = if (focused) C.Hi else C.Mid, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.hasPin) Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = C.Low)
            if (p.kids) Text(" Infantil", color = C.Low, fontSize = 11.sp)
        }
    }
}

/** "¿Quién está viendo?" */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePicker(onManage: () -> Unit) {
    val list by Profiles.list.collectAsState()
    var keep by remember { mutableStateOf(Profiles.deviceDefault.isNotBlank()) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var privatePin by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val firstFr = remember { FocusRequester() }

    fun enter(p: Profile) {
        if (keep && !p.guest && !p.private) Profiles.deviceDefault = p.id
        if (!keep && Profiles.deviceDefault == p.id) Profiles.deviceDefault = ""
        Profiles.select(p)
    }

    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Text("¿Quién está viendo?", color = C.Hi, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.paneWidth(820.dp).padding(horizontal = 12.dp)) {
                list.forEachIndexed { i, p ->
                    ProfileTile(p, if (i == 0) Modifier.focusRequester(firstFr) else Modifier) {
                        if (p.hasPin) pinFor = p else enter(p)
                    }
                }
                ProfileTile(Profiles.GUEST) { Profiles.select(Profiles.GUEST) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (keep) "Recordar en esta tele: sí" else "Recordar en esta tele: no", keep, { keep = !keep })
                ActionButton("Entrar con PIN", { privatePin = true }, icon = Icons.Filled.Lock)
                ActionButton("Administrar perfiles", onManage, icon = Icons.Filled.Edit)
            }
            if (msg.isNotEmpty()) Text(msg, color = C.Warn, fontSize = 13.sp)
            Text("Invitado no guarda nada: ni lo que ves, ni tu lista.", color = C.Low, fontSize = 12.sp)
        }
    }

    pinFor?.let { p ->
        PinDialog("PIN de ${p.name}", onCancel = { pinFor = null }) { pin ->
            if (Profiles.checkPin(p, pin)) { pinFor = null; enter(p); true } else false
        }
    }
    if (privatePin) {
        PinDialog("Escribe tu PIN", sub = "Para entrar a un perfil privado", onCancel = { privatePin = false }) { pin ->
            scope.launch {
                val p = Profiles.openPrivate(pin)
                if (p != null) { privatePin = false; Profiles.select(p) } else { privatePin = false; msg = "No hay ningún perfil con ese PIN (o no hay internet)." }
            }
            true
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstFr.requestFocus() } }
}

/**
 * Teclado de PIN de 4 números. Funciona con los números del control o con el teclado en pantalla.
 * onDone devuelve true si el PIN fue aceptado (si no, avisa y limpia).
 */
@Composable
fun PinDialog(title: String, sub: String = "", onCancel: () -> Unit, onDone: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    fun add(d: String) {
        if (pin.length >= 4) return
        pin += d; wrong = false
        if (pin.length == 4) { if (!onDone(pin)) { wrong = true; pin = "" } }
    }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.cardWidth(380.dp).background(C.Panel, RoundedCornerShape(20.dp)).padding(26.dp)
                .onPreviewKeyEvent { e ->
                    val c = e.key.nativeKeyCode
                    if (e.type == KeyEventType.KeyDown && c in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) {
                        add((c - android.view.KeyEvent.KEYCODE_0).toString()); true
                    } else false
                },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotBlank()) Text(sub, color = C.Low, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i -> Box(Modifier.size(16.dp).background(if (i < pin.length) C.Hi else C.Line, CircleShape)) }
            }
            Text(if (wrong) "PIN incorrecto" else " ", color = C.Warn, fontSize = 13.sp)
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.chunked(3).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { cIdx, k ->
                            val m = if (r == 0 && cIdx == 0) Modifier.focusRequester(fr) else Modifier
                            KeyButton(k, m) {
                                when (k) { "⌫" -> pin = pin.dropLast(1); "✕" -> onCancel(); else -> add(k) }
                            }
                        }
                    }
                }
            }
            Text("También puedes usar los números del control", color = C.Low, fontSize = 11.sp)
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

@Composable
private fun KeyButton(k: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.size(width = 84.dp, height = 52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel2, contentColor = C.Hi, focusedContainerColor = C.Accent, focusedContentColor = C.OnAccent),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(k, fontSize = 20.sp, fontWeight = FontWeight.Medium) } }
}

/** Administrar perfiles (si un administrador tiene PIN, se pide antes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileManager(onClose: () -> Unit) {
    val list by Profiles.list.collectAsState()
    val admins = list.filter { it.admin }
    var unlocked by remember { mutableStateOf(admins.none { it.hasPin } || Profiles.current.value?.admin == true) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var newPrivate by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }
    BackHandler { onClose() }

    if (!unlocked) {
        PinDialog("PIN del administrador", sub = "Para administrar perfiles", onCancel = onClose) { pin ->
            if (admins.any { it.hasPin && Profiles.checkPin(it, pin) }) { unlocked = true; true } else false
        }
        return
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Administrar perfiles", color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Elige un perfil para cambiar nombre, color, PIN, si es infantil o administrador.", color = C.Mid, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                list.forEachIndexed { i, p -> ProfileTile(p, if (i == 0) Modifier.focusRequester(firstFr) else Modifier) { editing = p } }
                Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { editing = Profiles.create("Nuevo perfil", (list.size + 1) % Profiles.colors.size) },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel2, contentColor = C.Mid, focusedContainerColor = C.Accent, focusedContentColor = C.OnAccent),
                    ) { Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, Modifier.size(40.dp)) } }
                    Spacer(Modifier.height(10.dp))
                    Text("Agregar", color = C.Mid, fontSize = 15.sp)
                }
            }
            Column(Modifier.background(C.Panel, RoundedCornerShape(14.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perfil privado", color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "No aparece en ninguna lista ni en ningún aparato. Para entrar se elige «Entrar con PIN» y se escribe su PIN. " +
                        "Lo que veas ahí no deja rastro en la tele: se guarda solo en la nube, ligado a ese PIN.",
                    color = C.Mid, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth(0.85f),
                )
                ActionButton("Crear perfil privado", { newPrivate = true }, icon = Icons.Filled.VisibilityOff)
            }
            ActionButton("Listo", onClose, primary = true)
        }
    }

    editing?.let { p -> ProfileEditor(p, canRemoveAdmin = admins.size > 1 || !p.admin) { editing = null } }
    if (newPrivate) PrivateCreator { newPrivate = false }
    LaunchedEffect(Unit) { delay(80); runCatching { firstFr.requestFocus() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(p0: Profile, canRemoveAdmin: Boolean, onClose: () -> Unit) {
    var p by remember { mutableStateOf(p0) }
    var name by remember { mutableStateOf(p0.name) }
    var askPin by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    fun commit() { Profiles.save(p.copy(name = name.trim().ifBlank { p.name })) }

    Dialog(onDismissRequest = { commit(); onClose() }) {
        Column(Modifier.cardWidth(560.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.copy(name = name), 56.dp)
                Spacer(Modifier.width(14.dp))
                Text("Editar perfil", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            TvTextField(name, { name = it }, "Nombre", Modifier.fillMaxWidth(), boxRequester = fr)
            Text("Color", color = C.Low, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profiles.colors.forEachIndexed { i, c ->
                    Surface(
                        onClick = { p = p.copy(color = i) },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                        border = ClickableSurfaceDefaults.border(
                            border = if (p.color == i) Border(BorderStroke(2.dp, C.Hi), shape = CircleShape) else Border.None,
                            focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = CircleShape),
                        ),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(c), focusedContainerColor = Color(c)),
                    ) { Box(Modifier.size(34.dp)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(if (p.kids) "Infantil: sí" else "Infantil: no", p.kids, { p = p.copy(kids = !p.kids) })
                Pill(if (p.admin) "Administrador: sí" else "Administrador: no", p.admin, { if (!p.admin || canRemoveAdmin) p = p.copy(admin = !p.admin) })
                Pill(if (p.hasPin) "PIN: activado" else "PIN: sin PIN", p.hasPin, { if (p.hasPin) p = Profiles.withPin(p, null) else askPin = true }, icon = Icons.Filled.Lock)
            }
            Text(
                "Infantil: oculta lo de adultos y muestra solo categorías infantiles si tu proveedor las tiene. " +
                    "Administrador: puede cambiar la cuenta, los perfiles y los aparatos.",
                color = C.Low, fontSize = 12.sp, lineHeight = 17.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Guardar", { commit(); onClose() }, primary = true)
                ActionButton("Eliminar perfil", { confirmDel = true })
            }
        }
    }
    if (askPin) PinDialog("Nuevo PIN para ${name.ifBlank { p.name }}", sub = "4 números", onCancel = { askPin = false }) { pin ->
        p = Profiles.withPin(p, pin); askPin = false; true
    }
    if (confirmDel) ConfirmDialog(
        "¿Eliminar ${p.name}?", "Se borran su lista, lo que iba viendo y sus favoritos en todos los aparatos.", "Eliminar",
        onConfirm = { confirmDel = false; scope.launch { Profiles.delete(p) }; onClose() }, onDismiss = { confirmDel = false },
    )
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

@Composable
private fun PrivateCreator(onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var askPin by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.cardWidth(520.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nuevo perfil privado", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Elige un nombre (solo lo verás tú) y un PIN de 4 números que no uses en otro perfil.", color = C.Mid, fontSize = 13.sp)
            TvTextField(name, { name = it }, "Nombre", Modifier.fillMaxWidth(), boxRequester = fr)
            if (done.isNotEmpty()) Text(done, color = C.Good, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Elegir PIN y crear", { askPin = true }, primary = true)
                ActionButton("Cerrar", onClose)
            }
        }
    }
    if (askPin) PinDialog("PIN del perfil privado", onCancel = { askPin = false }) { pin ->
        askPin = false
        scope.launch {
            Profiles.createPrivate(name, pin, 5)
            done = "Listo. Para entrar: «¿Quién está viendo?» → Entrar con PIN."
        }
        true
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

/** Tele o celular nuevo: muestra un código para que otro aparato lo apruebe. */
@Composable
fun PairScreen(onCancel: () -> Unit) {
    var offer by remember { mutableStateOf<Cloud.PairOffer?>(null) }
    var status by remember { mutableStateOf("Pidiendo código…") }
    val fr = remember { FocusRequester() }
    BackHandler { onCancel() }
    LaunchedEffect(Unit) {
        while (true) {
            val o = Cloud.pairRequest()
            if (o == null) { status = "No hay conexión con la nube. Reintentando…"; delay(5000); continue }
            offer = o; status = "Esperando aprobación…"
            val until = System.currentTimeMillis() + 14 * 60_000
            while (System.currentTimeMillis() < until) {
                delay(3000)
                val p = Cloud.pairPoll(o) ?: continue
                // Se termina fuera de esta pantalla: al cambiar el estado, la pantalla desaparece
                // y cancelaría el trabajo a medias (eso dejaba la app en "Revisando activación…").
                status = "¡Aprobado! Entrando…"
                License.scope.launch {
                    Cloud.setHousehold(p.optString("hh"), p.optString("hs"))
                    if (p.optString("ua").isNotBlank()) Net.UA = p.optString("ua")
                    Repo.clearAll()
                    Prefs.setAccount(Account(p.optString("server"), p.optString("user"), p.optString("pass")))
                    // Si el hogar que lo aprobó usa código, este aparato se activa con el mismo
                    p.optString("lic").takeIf { it.isNotBlank() }?.let { License.activate(it) }
                    License.refresh()
                    onCancel()
                }
                return@LaunchedEffect
            }
        }
    }
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Tv, null, Modifier.size(44.dp), tint = C.Accent)
            Text("Agregar este aparato", color = C.Hi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("En una tele o celular donde ya uses CineCasa, entra a Ajustes → Agregar aparato y escribe:", color = C.Mid, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(offer?.code?.chunked(3)?.joinToString(" ") ?: "··· ···", color = C.Hi, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
            Text(status, color = C.Low, fontSize = 13.sp)
            ActionButton("Volver", onCancel, Modifier.focusRequester(fr))
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

/** En un aparato ya configurado: aprobar el código de la tele nueva. */
@Composable
fun AddDeviceDialog(onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.cardWidth(500.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Agregar aparato", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("En el aparato nuevo abre CineCasa y elige «Tengo CineCasa en otro aparato». Escribe aquí el código que muestra.", color = C.Mid, fontSize = 13.sp)
            TvTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, "Código de 6 números", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number, boxRequester = fr)
            if (msg.isNotEmpty()) Text(msg, color = if (msg.startsWith("Listo")) C.Good else C.Warn, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (busy) { Spinner(size = 24.dp); Text("Enviando…", color = C.Mid, fontSize = 14.sp) }
                else {
                    ActionButton("Aprobar", {
                        if (code.length != 6) { msg = "Son 6 números." } else {
                            busy = true
                            scope.launch {
                                val ok = Cloud.pairApprove(code)
                                busy = false
                                msg = if (ok) "Listo. El aparato nuevo entrará solo en unos segundos." else "No funcionó: revisa el código (dura 15 minutos) o la conexión."
                            }
                        }
                    }, primary = true)
                    ActionButton("Cerrar", onClose)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

/** Aparatos del hogar (solo administrador). No muestra quién ni qué está viendo cada uno. */
@Composable
fun DevicesSection() {
    val devices by Devices.list.collectAsState()
    var addOpen by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(Cloud.deviceName) }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("es", "MX")) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aparatos con CineCasa en tu hogar. Para agregar uno nuevo no hace falta escribir la contraseña: solo un código.", color = C.Mid, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Agregar aparato", { addOpen = true }, icon = Icons.Filled.Add, primary = true)
            ActionButton("Nombre de este aparato: ${Cloud.deviceName}", { rename = true }, icon = Icons.Filled.Edit)
        }
        devices.values.sortedByDescending { it.seen }.forEach { d ->
            Row(Modifier.fillMaxWidth().background(C.Panel2, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name + if (d.id == Cloud.deviceId) "  (este)" else "", color = C.Hi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("${d.model} · versión ${d.version} · conectado ${fmt.format(Date(d.seen))}", color = C.Low, fontSize = 11.sp)
                }
                if (d.id != Cloud.deviceId) Pill("Quitar", false, { Cloud.removeDevice(d.id) })
            }
        }
    }
    if (addOpen) AddDeviceDialog { addOpen = false }
    if (rename) {
        val fr = remember { FocusRequester() }
        Dialog(onDismissRequest = { rename = false }) {
            Column(Modifier.cardWidth(460.dp).background(C.Panel, RoundedCornerShape(18.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nombre de este aparato", color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TvTextField(name, { name = it }, "Sala, Recámara, Celular…", Modifier.fillMaxWidth(), boxRequester = fr)
                ActionButton("Guardar", { Cloud.deviceName = name; rename = false }, primary = true)
            }
        }
        LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    }
}
