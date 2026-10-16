package com.mauricio.cinecasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.LoadState
import com.mauricio.cinecasa.data.PlayItem
import com.mauricio.cinecasa.data.Prefs
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
    data object Settings : Route
    data class MovieDetail(val vod: Vod) : Route
    data class ShowDetail(val show: Show) : Route
    data class Play(val items: List<PlayItem>, val start: Int, val startPos: Long) : Route
    data class LivePlay(val channels: List<Channel>, val index: Int) : Route
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
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (account == null) LoginScreen() else Shell()
    }
}

@Composable
private fun Shell() {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val memories = remember { HashMap<String, FocusMemory>() }
    val state = remember { HashMap<String, Any>() }
    val scope = rememberCoroutineScope()
    var askPlay by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val loadState by Repo.state.collectAsState()

    LaunchedEffect(Unit) { Repo.start() }

    fun withConnectionCheck(action: () -> Unit) {
        scope.launch {
            val info = Repo.accountInfo()
            if (info != null && info.maxCons > 0 && info.activeCons >= info.maxCons) {
                askPlay = "Ya hay ${info.activeCons} de ${info.maxCons} pantallas en uso. Si continúas, el servidor podría cortar otra reproducción." to action
            } else action()
        }
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
            play = { items, start, pos -> withConnectionCheck { stack.add(Route.Play(items, start, pos)) } },
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
                        ActionButton("Cambiar cuenta", { Repo.logout() })
                    }
                }
            top is Route.Play -> PlayerScreen(top.items, top.start, top.startPos)
            top is Route.LivePlay -> LivePlayerScreen(top.channels, top.index)
            top is Route.MovieDetail -> key(top) { MovieDetailScreen(top.vod) }
            top is Route.ShowDetail -> key(top) { ShowDetailScreen(top.show) }
            else -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(start = 72.dp)) {
                    when (top) {
                        Route.Home -> HomeScreen()
                        is Route.Search -> key(top) { SearchScreen(top.q) }
                        is Route.Movies -> key(top) { BrowseScreen(isSeries = false, initialCat = top.cat) }
                        is Route.Series -> key(top) { BrowseScreen(isSeries = true, initialCat = top.cat) }
                        Route.Live -> LiveScreen()
                        Route.MyList -> MyListScreen()
                        Route.Settings -> SettingsScreen()
                        else -> {}
                    }
                }
                Sidebar(top) { nav.go(it) }
            }
        }
    }

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
    r == Route.Live || r == Route.MyList || r == Route.Settings

private data class NavItem(val label: String, val icon: ImageVector, val route: Route, val match: (Route) -> Boolean)

private val navItems = listOf(
    NavItem("Buscar", Icons.Filled.Search, Route.Search()) { it is Route.Search },
    NavItem("Inicio", Icons.Filled.Home, Route.Home) { it == Route.Home },
    NavItem("Películas", Icons.Filled.Movie, Route.Movies()) { it is Route.Movies },
    NavItem("Series", Icons.Filled.Tv, Route.Series()) { it is Route.Series },
    NavItem("TV en vivo", Icons.Filled.LiveTv, Route.Live) { it == Route.Live },
    NavItem("Mi lista", Icons.Filled.Bookmarks, Route.MyList) { it == Route.MyList },
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
            .background(
                if (expanded) Brush.horizontalGradient(listOf(Color(0xFA07080B), Color(0xF207080B)))
                else Brush.horizontalGradient(listOf(Color(0xCC07080B), Color(0x0007080B))),
            )
            .onFocusChanged { expanded = it.hasFocus }
            .focusProperties { enter = { requesters[sel] } }
            .focusGroup()
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
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
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
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
            Modifier.width(460.dp).background(C.Panel, RoundedCornerShape(16.dp)).padding(28.dp),
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
