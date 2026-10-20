package com.mauricio.cinecasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon

import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.LoadState
import com.mauricio.cinecasa.data.PlayItem
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Profiles
import com.mauricio.cinecasa.data.Cloud
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.Show
import com.mauricio.cinecasa.data.Vod
import kotlinx.coroutines.launch

sealed interface Route {
    data object Home : Route
    data class Search(val q: String = "") : Route
    data class Movies(val cat: String? = null) : Route
    data class Series(val cat: String? = null) : Route
    data object Live : Route
    data object MyList : Route
    data object Downloads : Route
    data object More : Route
    data object Settings : Route
    data class MovieDetail(val vod: Vod) : Route
    data class ShowDetail(val show: Show) : Route
    data class Play(val items: List<PlayItem>, val start: Int, val startPos: Long) : Route
    data class LivePlay(val channels: List<Channel>, val index: Int) : Route
    data class MultiView(val pool: List<Channel>, val first: Channel?) : Route
}

/** Acciones de navegación disponibles para todas las pantallas. */
class Nav(
    val go: (Route) -> Unit,
    val back: () -> Unit,
    val play: (List<PlayItem>, Int, Long) -> Unit,
    val playLive: (List<Channel>, Int) -> Unit,
    val memory: (String) -> FocusMemory,
    val state: MutableMap<String, Any>,
)

val LocalNav = staticCompositionLocalOf<Nav> { error("sin navegación") }

@Composable
fun App() {
    val account by Prefs.account.collectAsState()
    var pairing by remember { mutableStateOf(false) }
    val lic by com.mauricio.cinecasa.data.License.state.collectAsState()
    LaunchedEffect(Unit) { com.mauricio.cinecasa.data.License.refresh() }
    LaunchedEffect(C.dark) { DeviceUi.styleBars(C.dark) }
    Box(Modifier.fillMaxSize().background(C.Bg).then(if (DeviceUi.isTv) Modifier else Modifier.systemBarsPadding())) {
        when (val l = lic) {
            com.mauricio.cinecasa.data.License.State.Checking -> CenterMessage("CineCasa", "Revisando activación…", loading = true)
            is com.mauricio.cinecasa.data.License.State.Need ->
                if (pairing) PairScreen { pairing = false } else ActivationScreen(l.msg, onPair = { pairing = true })
            com.mauricio.cinecasa.data.License.State.Ok ->
                if (account == null) {
                    if (pairing) PairScreen { pairing = false } else LoginScreen(onPair = { pairing = true })
                } else ProfileGate()
        }
        AutoUpdateCheck()
        SleepTimerWatcher()
    }
    LaunchedEffect(Unit) {
        while (true) { C.apply(Prefs.themeMode); kotlinx.coroutines.delay(60_000) }
    }
}

/** Después de la cuenta: hogar en la nube, perfiles y "¿Quién está viendo?". */
@Composable
private fun ProfileGate() {
    val current by Profiles.current.collectAsState()
    val revoked by Cloud.revoked.collectAsState()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withTimeoutOrNull(8000) { Cloud.ensureHousehold(); Cloud.syncNow() }
        Profiles.ensureFirst()
        val d = Profiles.deviceDefault
        Profiles.list.value.firstOrNull { it.id == d && !it.hasPin }?.let { Profiles.select(it) }
        ready = true
        Cloud.startLoop()
    }
    LaunchedEffect(current?.id) { if (current != null && ready) runCatching { Cloud.syncNow() } }
    LaunchedEffect(revoked) {
        if (revoked) { Cloud.forgetHousehold(); Profiles.signOut(); Repo.logout() }
    }
    val cur = current
    when {
        !ready -> CenterMessage("Cargando perfiles…", loading = true)
        manageProfilesOpen.value -> ProfileManager { manageProfilesOpen.value = false }
        cur == null -> ProfilePicker(onManage = { manageProfilesOpen.value = true })
        else -> key(cur.id) { Shell() }
    }
    val pend = pendingPairCode.value
    if (cur != null && (pend != null || addDeviceOpen.value)) AddDeviceDialog({ pendingPairCode.value = null; addDeviceOpen.value = false }, pend ?: "")
    if (rokuOpen.value) RokuInstallDialog { rokuOpen.value = false }
}

@Composable
private fun Shell() {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val memories = remember { HashMap<String, FocusMemory>() }
    val state = remember { HashMap<String, Any>() }
    val scope = rememberCoroutineScope()
    var askPlay by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var serverDlg by remember { mutableStateOf(false) }
    val loadState by Repo.state.collectAsState()

    LaunchedEffect(Unit) { Repo.start() }
    // Abrir directo en el último canal (si se eligió en Ajustes)
    val catalogNow by Repo.catalog.collectAsState()
    var autoStarted by remember { mutableStateOf(false) }
    LaunchedEffect(catalogNow.channels.isNotEmpty()) {
        if (autoStarted || catalogNow.channels.isEmpty()) return@LaunchedEffect
        autoStarted = true
        if (!Prefs.startOnLive) return@LaunchedEffect
        val last = catalogNow.channelById[com.mauricio.cinecasa.data.Library.lastChannel] ?: return@LaunchedEffect
        val list = com.mauricio.cinecasa.data.Library.recentChannels.value.mapNotNull { catalogNow.channelById[it] }.ifEmpty { listOf(last) }
        stack.add(Route.LivePlay(list, list.indexOf(last).coerceAtLeast(0)))
    }

    fun withConnectionCheck(action: () -> Unit) {
        // El conteo de "pantallas en uso" del proveedor no es confiable (cuenta sesiones viejas
        // que ya se cerraron), así que ya no se pregunta antes de reproducir. Si de verdad
        // se rebasa el límite, el reproductor muestra el aviso del servidor.
        action()
    }

    val nav = remember {
        Nav(
            go = { r ->
                val top = stack.last()
                if (isTopLevel(r) && isTopLevel(top)) {
                    stack.clear(); stack.add(r)
                } else stack.add(r)
            },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
            play = { items, start, pos ->
                // Lo descargado no usa pantallas de la cuenta: se reproduce directo
                if (items.all { com.mauricio.cinecasa.data.Downloads.localUri(it.key) != null }) stack.add(Route.Play(items, start, pos))
                else withConnectionCheck { stack.add(Route.Play(items, start, pos)) }
            },
            playLive = { ch, i -> withConnectionCheck { stack.add(Route.LivePlay(ch, i)) } },
            memory = { k -> memories.getOrPut(k) { FocusMemory() } },
            state = state,
        )
    }

    val top = stack.last()
    BackHandler(enabled = stack.size > 1 || top != Route.Home) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else { stack.clear(); stack.add(Route.Home) }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        val ls = loadState
        val catalog by Repo.catalog.collectAsState()
        val emptyCatalog = catalog.movies.isEmpty() && catalog.shows.isEmpty() && catalog.channels.isEmpty()
        when {
            emptyCatalog && (ls is LoadState.Loading || ls is LoadState.Idle) ->
                CenterMessage("Preparando tu catálogo", (ls as? LoadState.Loading)?.msg ?: "Un momento…", loading = true)
            emptyCatalog && ls is LoadState.Error ->
                CenterMessage("No se pudo cargar", ls.msg) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("Reintentar", { Repo.start(force = true) }, primary = true)
                        ActionButton("Ver mis descargas", { stack.clear(); stack.add(Route.Downloads); Repo.markOffline() })
                        ActionButton("Cambiar servidor", { serverDlg = true })
                        ActionButton("Cambiar cuenta", { Repo.logout() })
                    }
                }
            top is Route.Play -> PlayerScreen(top.items, top.start, top.startPos)
            top is Route.LivePlay -> key(top) { LivePlayerScreen(top.channels, top.index) }
            top is Route.MultiView -> MultiViewScreen(top.pool, top.first)
            top is Route.MovieDetail -> key(top) { MovieDetailScreen(top.vod) }
            top is Route.ShowDetail -> key(top) { ShowDetailScreen(top.show) }
            LocalCompact.current -> PhoneShell(top, canBack = stack.size > 1)
            else -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(start = 72.dp)) {
                if (stack.size > 1) BackBar { nav.back() }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (top) {
                        Route.Home -> HomeScreen()
                        is Route.Search -> key(top) { SearchScreen(top.q) }
                        is Route.Movies -> key(top) { BrowseScreen(isSeries = false, initialCat = top.cat) }
                        is Route.Series -> key(top) { BrowseScreen(isSeries = true, initialCat = top.cat) }
                        Route.Live -> LiveScreen()
                        Route.MyList -> MyListScreen()
                        Route.Downloads -> DownloadsScreen()
                        Route.Settings -> SettingsScreen()
                        else -> {}
                    }
                }
                }
                Sidebar(top) { nav.go(it) }
                ClockLabel(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 24.dp))
            }
        }
    }

    if (serverDlg) ServerDialog { serverDlg = false }
    ExpiryReminder()
    askPlay?.let { (msg, action) ->
        ConfirmDialog(
            title = "Pantallas ocupadas",
            message = msg,
            confirm = "Reproducir de todos modos",
            onConfirm = { askPlay = null; action() },
            onDismiss = { askPlay = null },
        )
    }
}

private fun isTopLevel(r: Route) = r == Route.Home || r is Route.Search || r is Route.Movies || r is Route.Series ||
    r == Route.Live || r == Route.MyList || r == Route.Settings || r == Route.Downloads || r == Route.More

private data class NavItem(val label: String, val icon: ImageVector, val route: Route, val match: (Route) -> Boolean)

private val navItems = listOf(
    NavItem("Buscar", Icons.Filled.Search, Route.Search()) { it is Route.Search },
    NavItem("Inicio", Icons.Filled.Home, Route.Home) { it == Route.Home },
    NavItem("Películas", Icons.Filled.Movie, Route.Movies()) { it is Route.Movies },
    NavItem("Series", Icons.Filled.Tv, Route.Series()) { it is Route.Series },
    NavItem("TV en vivo", Icons.Filled.LiveTv, Route.Live) { it == Route.Live },
    NavItem("Mi lista", Icons.Filled.Bookmarks, Route.MyList) { it == Route.MyList },
    NavItem("Descargas", Icons.Filled.Download, Route.Downloads) { it == Route.Downloads },
    NavItem("Ajustes", Icons.Filled.Settings, Route.Settings) { it == Route.Settings },
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sidebar(current: Route, onSelect: (Route) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val requesters = remember { List(navItems.size) { FocusRequester() } }
    val sel = navItems.indexOfFirst { it.match(current) }.coerceAtLeast(0)
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (expanded) 220.dp else 72.dp)
            .background(if (expanded) C.Glass else C.Panel.copy(alpha = 0.9f))
            .onFocusChanged { expanded = it.hasFocus }
            .focusProperties { enter = { requesters[sel] } }
            .focusGroup()
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val me by Profiles.current.collectAsState()
        me?.let { p ->
            Surface(
                onClick = { Profiles.signOut() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = C.Mid, focusedContainerColor = C.Accent, focusedContentColor = C.OnAccent),
            ) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 32.dp)
                    if (expanded) {
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("Cambiar perfil", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        navItems.forEachIndexed { i, item ->
            val shape = RoundedCornerShape(10.dp)
            Surface(
                onClick = { onSelect(item.route) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).focusRequester(requesters[i]),
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = if (i == sel) C.Accent else C.Mid,
                    focusedContainerColor = C.Accent,
                    focusedContentColor = C.OnAccent,
                ),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, item.label, Modifier.size(22.dp))
                    if (expanded) {
                        Spacer(Modifier.width(14.dp))
                        Text(item.label, fontSize = 15.sp, fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, cancel: String = "Cancelar") {
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.cardWidth(460.dp).background(C.Panel, RoundedCornerShape(16.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = C.Hi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = C.Mid, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(cancel, onDismiss, Modifier.focusRequester(fr))
                ActionButton(confirm, onConfirm, primary = true)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}


// ======================================================================
// Celular: pestañas abajo como en iPhone
// ======================================================================
private data class Tab(val label: String, val icon: ImageVector, val route: Route, val match: (Route) -> Boolean)

private val phoneTabs = listOf(
    Tab("Inicio", Icons.Filled.Home, Route.Home) { it == Route.Home || it is Route.Movies || it is Route.Series },
    Tab("Buscar", Icons.Filled.Search, Route.Search()) { it is Route.Search },
    Tab("En vivo", Icons.Filled.LiveTv, Route.Live) { it == Route.Live },
    Tab("Descargas", Icons.Filled.Download, Route.Downloads) { it == Route.Downloads },
    Tab("Más", Icons.Filled.Menu, Route.More) { it == Route.More || it == Route.MyList || it == Route.Settings },
)

@Composable
private fun PhoneShell(top: Route, canBack: Boolean) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        if (canBack) BackBar { nav.back() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (top) {
                Route.Home -> HomeScreen()
                is Route.Search -> key(top) { SearchScreen(top.q) }
                is Route.Movies -> key(top) { BrowseScreen(isSeries = false, initialCat = top.cat) }
                is Route.Series -> key(top) { BrowseScreen(isSeries = true, initialCat = top.cat) }
                Route.Live -> LiveScreen()
                Route.MyList -> MyListScreen()
                Route.Downloads -> DownloadsScreen()
                Route.Settings -> SettingsScreen()
                Route.More -> MoreScreen()
                else -> {}
            }
        }
        // Barra de pestañas
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().background(C.Glass).padding(top = 6.dp, bottom = 6.dp)) {
            phoneTabs.forEach { t ->
                val sel = t.match(top)
                Column(
                    Modifier.weight(1f).clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                    ) { nav.go(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, Modifier.size(24.dp), tint = if (sel) C.Accent else C.Low)
                    Text(t.label, fontSize = 10.sp, color = if (sel) C.Accent else C.Low, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** "Más": como la pestaña de Ajustes de iPhone, en grupos. */
@Composable
private fun MoreScreen() {
    val nav = LocalNav.current
    val me by Profiles.current.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PhoneTopBar("Más")
        me?.let { p ->
            Group {
                Row(Modifier.fillMaxWidth().clickable { Profiles.signOut() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = C.Hi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Cambiar de perfil", color = C.Low, fontSize = 13.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = C.Low)
                }
            }
        }
        Group {
            MoreRow(Icons.Filled.Bookmarks, "Mi lista") { nav.go(Route.MyList) }
            MoreRow(Icons.Filled.Movie, "Películas") { nav.go(Route.Movies()) }
            MoreRow(Icons.Filled.Tv, "Series") { nav.go(Route.Series()) }
            MoreRow(Icons.Filled.Download, "Descargas", last = true) { nav.go(Route.Downloads) }
        }
        Group {
            MoreRow(
                if (DeviceUi.rotationLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                if (DeviceUi.rotationLocked) "Rotación bloqueada" else "Rotación automática",
                value = if (DeviceUi.rotationLocked) "Toca para desbloquear" else "Toca para bloquear",
            ) { DeviceUi.toggleRotationLock() }
            MoreRow(Icons.Filled.Tv, "Agregar otra tele o celular") { addDeviceOpen.value = true }
            if (!DeviceUi.isTv) MoreRow(Icons.Filled.LiveTv, "Instalar CineCasa en una tele Roku") { rokuOpen.value = true }
            MoreRow(Icons.Filled.Settings, "Ajustes", last = true) { nav.go(Route.Settings) }
        }
        Text("Los videos siempre se ven a lo ancho. El candado deja fija la orientación del resto de la app.", color = C.Low, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel)) { content() }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, value: String = "", last: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(C.Accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = C.Hi, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (value.isNotBlank()) Text(value, color = C.Low, fontSize = 13.sp)
            Icon(Icons.Filled.ChevronRight, null, tint = C.Low)
        }
        if (!last) Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(0.5.dp).background(C.Line))
    }
}


/** Aviso (una vez al día) cuando la suscripción del cliente está por vencer. */
@Composable
private fun ExpiryReminder() {
    val info by com.mauricio.cinecasa.data.License.info.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(info) {
        val i = info ?: return@LaunchedEffect
        val exp = i.optString("expires").takeIf { it.length >= 19 && it != "null" } ?: return@LaunchedEffect
        val t = runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(exp.take(19))!!.time }.getOrNull() ?: return@LaunchedEffect
        val days = ((t - System.currentTimeMillis()) / 86_400_000L).toInt()
        if (days > 7 || days < 0) return@LaunchedEffect
        val sp = ctx.getSharedPreferences("aviso", android.content.Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / 86_400_000L
        if (sp.getLong("dia", 0) == today) return@LaunchedEffect
        sp.edit().putLong("dia", today).apply()
        val trial = i.optString("kind") == "trial"
        val seller = i.optString("seller").takeIf { it.isNotBlank() && it != "null" }
        show = (if (trial) "Tu prueba gratis" else "Tu CineCasa") + (if (days == 0) " vence hoy." else " vence en $days ${if (days == 1) "día" else "días"}.") +
            (if (seller != null) " Pídele a $seller tu ${if (trial) "plan anual" else "renovación"} para no quedarte sin ver." else " Pide tu ${if (trial) "plan anual" else "renovación"} a quien te la vendió.") +
            " Tu código sigue siendo el mismo."
    }
    show?.let { msg ->
        val phone = info?.optString("seller_phone")?.filter { it.isDigit() }.orEmpty()
        ConfirmDialog(
            title = "Tu suscripción está por vencer", message = msg,
            confirm = if (phone.length >= 10) "Escribirle por WhatsApp" else "Entendido",
            onConfirm = {
                show = null
                if (phone.length >= 10) runCatching {
                    val p = if (phone.length == 10) "52$phone" else phone
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$p?text=" + android.net.Uri.encode("Hola, quiero renovar mi CineCasa (código ${com.mauricio.cinecasa.data.License.code}).")))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onDismiss = { show = null }, cancel = "Después",
        )
    }
}
