package com.mauricio.cinecasa.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent as AKeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.Library
import com.mauricio.cinecasa.data.Net
import com.mauricio.cinecasa.data.PlayItem
import com.mauricio.cinecasa.data.Prefs
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.TextUtil
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
fun buildPlayer(ctx: Context, live: Boolean): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val extractors = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
    val msf = DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http), extractors)
    val renderers = DefaultRenderersFactory(ctx)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(if (live) 8_000 else 20_000, if (live) 30_000 else 60_000, 1_500, 3_000)
        .build()
    val p = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(msf)
        .setLoadControl(load)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
    // Preferir audio en español y sin subtítulos forzados salvo que se elijan
    p.trackSelectionParameters = TrackSelectionParameters.Builder(ctx)
        .setPreferredAudioLanguages("es", "spa", "es-419", "es-MX", "lat")
        .setPreferredTextLanguage("es")
        .setSelectUndeterminedTextLanguage(false)
        .build()
    return p
}

private fun errorText(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "El servidor rechazó la conexión (puede que tengas todas las pantallas en uso)."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "No hay conexión con el servidor."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Formato de video no compatible."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Esta tele no puede decodificar este video (códec no compatible)."
    else -> "No se pudo reproducir (${e.errorCodeName})."
}

private val PMid = Color(0xFFD1D1D6)
private val PLow = Color(0xFFA1A1A6)

// ======================================================================
// Películas y series
// ======================================================================
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PlayerScreen(items: List<PlayItem>, start: Int, startPos: Long) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val player = remember { buildPlayer(ctx, live = false) }
    var current by remember { mutableIntStateOf(start) }
    var ctrlVisible by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var viewRef by remember { mutableStateOf<PlayerView?>(null) }
    var seekHint by remember { mutableStateOf("") }
    var seekTick by remember { mutableIntStateOf(0) }

    fun save() {
        val it = items.getOrNull(player.currentMediaItemIndex) ?: return
        val d = player.duration
        if (d != MC.TIME_UNSET && d > 0) Library.saveProgress(it, player.currentPosition, d)
    }

    DisposableEffect(Unit) {
        val mediaItems = items.map { MediaItem.Builder().setUri(it.url).setMediaId(it.key).build() }
        player.setMediaItems(mediaItems, start, startPos.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // el episodio anterior terminó: marcarlo como visto
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    items.getOrNull(current)?.let { prev -> Library.saveProgress(prev, 1, 1) }
                }
                current = player.currentMediaItemIndex
                error = null
            }
            override fun onPlayerError(e: PlaybackException) { error = errorText(e) }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    items.getOrNull(player.currentMediaItemIndex)?.let { Library.saveProgress(it, 1, 1) }
                    nav.back()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    // Guardar avance cada 10 s y calcular cuánto falta (para avisar del siguiente episodio)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val d = player.duration
            remaining = if (d != MC.TIME_UNSET && d > 0) d - player.currentPosition else Long.MAX_VALUE
            if ((System.currentTimeMillis() / 1000) % 10 == 0L && player.isPlaying) save()
        }
    }

    BackHandler {
        val v = viewRef
        if (v != null && ctrlVisible && error == null) v.hideController() else nav.back()
    }

    LaunchedEffect(seekTick) { if (seekTick > 0) { delay(900); seekHint = "" } }

    val item = items.getOrNull(current)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                // Con los controles ocultos: ◀ ▶ regresan/adelantan 10 s (como en Netflix)
                if (e.type != KeyEventType.KeyDown || ctrlVisible || error != null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { player.seekBack(); seekHint = "−10 s  ·  " + TextUtil.clock(player.currentPosition); seekTick++; true }
                    Key.DirectionRight -> { player.seekForward(); seekHint = "+10 s  ·  " + TextUtil.clock(player.currentPosition); seekTick++; true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4500
                    controllerAutoShow = true
                    setShowSubtitleButton(true)
                    setShowNextButton(items.size > 1)
                    setShowPreviousButton(items.size > 1)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { vis -> ctrlVisible = vis == View.VISIBLE })
                    isFocusable = true
                    isFocusableInTouchMode = true
                    viewRef = this
                    post { requestFocus(); showController() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Título arriba cuando se ven los controles
        AnimatedVisibility(ctrlVisible && item != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 40.dp, end = 40.dp, top = 26.dp, bottom = 40.dp)) {
                Column {
                    Text(item?.title.orEmpty(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (!item?.subtitle.isNullOrBlank()) Text(item?.subtitle.orEmpty(), color = PMid, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        if (seekHint.isNotEmpty()) {
            Text(seekHint, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(horizontal = 20.dp, vertical = 10.dp))
        }
        // Aviso de siguiente episodio
        val next = items.getOrNull(current + 1)
        if (next != null && item?.kind == "episode" && remaining in 1L..30_000L) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 110.dp).background(Color(0xE6101010), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Column {
                    Text("Siguiente en ${remaining / 1000} s", color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(next.subtitle, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(300.dp))
                    Text("Usa ⏭ para pasar ya", color = PLow, fontSize = 11.sp)
                }
            }
        }
        error?.let { msg ->
            Box(Modifier.align(Alignment.Center).background(Color(0xE6101010), RoundedCornerShape(12.dp)).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(msg, color = Color.White, fontSize = 16.sp)
                    Text("Presiona OK para reintentar o Atrás para salir.", color = PMid, fontSize = 13.sp)
                }
            }
            LaunchedEffect(msg) {
                viewRef?.setOnKeyListener { _, code, ev ->
                    if (ev.action == AKeyEvent.ACTION_UP && (code == AKeyEvent.KEYCODE_DPAD_CENTER || code == AKeyEvent.KEYCODE_ENTER) && error != null) {
                        error = null; player.prepare(); player.play(); true
                    } else false
                }
            }
        }
    }
}

// ======================================================================
// TV en vivo
// ======================================================================
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun LivePlayerScreen(channels: List<Channel>, startIndex: Int) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val c by Repo.catalog.collectAsState()
    val favs by Library.favChannels.collectAsState()
    val player = remember { buildPlayer(ctx, live = true) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showList by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    val rootFr = remember { FocusRequester() }
    val listFr = remember { FocusRequester() }
    val ch = channels.getOrNull(index)

    fun formatsFor(): List<String> {
        val pref = Prefs.liveFormat
        val allowed = Prefs.serverFormats
        val base = when (pref) {
            "ts" -> listOf("ts", "m3u8")
            "m3u8" -> listOf("m3u8", "ts")
            else -> if (allowed.isNotEmpty() && "ts" !in allowed && "m3u8" in allowed) listOf("m3u8", "ts") else listOf("ts", "m3u8")
        }
        return base
    }

    fun load(i: Int, tryN: Int = 0) {
        val chan = channels.getOrNull(i) ?: return
        val x = Repo.xtream() ?: return
        val fmts = formatsFor()
        val fmt = fmts[(tryN) % fmts.size]
        val mi = MediaItem.Builder().setUri(x.liveUrl(chan.id, fmt)).apply { if (fmt == "m3u8") setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        player.setMediaItem(mi)
        player.prepare()
        player.playWhenReady = true
        error = null
        Library.touchChannel(chan.id)
        Library.lastChannel = chan.id
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) attempt = 0
            }
            override fun onPlayerError(e: PlaybackException) {
                if (attempt < 3) {
                    attempt++
                    load(index, attempt) // prueba el otro formato y reintenta
                } else error = errorText(e)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(index) {
        attempt = 0
        load(index)
        showInfo = true
        infoTick++
    }
    LaunchedEffect(infoTick, showInfo) {
        if (showInfo) { delay(5000); showInfo = false }
    }
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1600)
            val n = digits.toIntOrNull()
            val target = channels.indexOfFirst { it.num == n }.takeIf { it >= 0 }
                ?: c.visChannels.indexOfFirst { it.num == n }.takeIf { it >= 0 }?.let { gi -> channels.indexOf(c.visChannels[gi]).takeIf { it >= 0 } }
            if (target != null) index = target
            digits = ""
        }
    }
    LaunchedEffect(showList) {
        if (showList) { delay(80); runCatching { listFr.requestFocus() } } else { delay(30); runCatching { rootFr.requestFocus() } }
    }

    BackHandler {
        when {
            showList -> showList = false
            showInfo -> showInfo = false
            else -> nav.back()
        }
    }

    fun move(d: Int) {
        if (channels.isEmpty()) return
        index = (index + d + channels.size) % channels.size
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFr)
            // Celular/tableta: tocar abre la lista; deslizar arriba/abajo cambia de canal
            .pointerInput(Unit) { detectTapGestures(onTap = { if (!showList) showList = true }) }
            .pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(
                    onDragEnd = { if (acc < -90f) move(1) else if (acc > 90f) move(-1); acc = 0f },
                ) { _, d -> acc += d }
            }
            .onKeyEvent { e ->
                if (showList || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { move(if (e.key == Key.ChannelUp) 1 else -1); true }
                    Key.DirectionDown, Key.ChannelDown -> { move(if (e.key == Key.ChannelDown) -1 else 1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft -> { showList = true; true }
                    Key.DirectionRight, Key.Info, Key.Menu -> { showInfo = !showInfo; infoTick++; true }
                    else -> {
                        val code = e.key.nativeKeyCode
                        if (code in AKeyEvent.KEYCODE_0..AKeyEvent.KEYCODE_9) {
                            digits = (digits + (code - AKeyEvent.KEYCODE_0)).takeLast(4); true
                        } else false
                    }
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { cx ->
                PlayerView(cx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) Spinner(Modifier.align(Alignment.Center), 48.dp)

        if (digits.isNotEmpty()) {
            Text(digits, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp).background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 6.dp))
        }

        // Banner con el canal y la guía
        AnimatedVisibility(showInfo && ch != null && !showList, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            if (ch != null) LiveBanner(ch, c.liveCatName[ch.cat].orEmpty(), ch.id in favs)
        }

        // Lista de canales lateral
        AnimatedVisibility(showList, Modifier.align(Alignment.CenterStart), enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 3).coerceAtLeast(0))
            Column(Modifier.width(430.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xF2050608), Color(0xCC050608))))) {
                Text("Canales · Mantén OK para favorito", color = PLow, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(channels, key = { _, k -> k.id }) { i, k ->
                        ChannelRow(
                            k, k.id in favs,
                            if (i == index) Modifier.focusRequester(listFr) else Modifier,
                            selected = i == index,
                            onLongClick = { Library.toggleFavChannel(k.id) },
                        ) { index = i; showList = false }
                    }
                }
            }
        }

        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).background(Color(0xE6101010), RoundedCornerShape(12.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ch?.name.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(msg, color = PMid, fontSize = 14.sp)
                Text("Cambia de canal con ▲ ▼ o abre la lista con OK.", color = PLow, fontSize = 12.sp)
            }
        }
    }
    LaunchedEffect(Unit) { delay(50); runCatching { rootFr.requestFocus() } }
}

@Composable
private fun LiveBanner(ch: Channel, catName: String, fav: Boolean) {
    val epg = rememberEpg(ch, 0)
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))).padding(start = 40.dp, end = 40.dp, top = 60.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 120.dp, height = 68.dp).background(Color(0x22FFFFFF), RoundedCornerShape(8.dp)).padding(6.dp), contentAlignment = Alignment.Center) {
                Img(ch.icon, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch.num > 0) Text("${ch.num}  ", color = C.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(ch.name + if (fav) "  ★" else "", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (catName.isNotBlank()) Text("   $catName", color = PLow, fontSize = 13.sp, maxLines = 1)
                }
                val now = epg.firstOrNull()
                val nxt = epg.getOrNull(1)
                if (now != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ahora: ${now.title}", color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        ProgressBar(now.progress(), Modifier.width(260.dp), 3.dp)
                        Text("  ${TextUtil.hhmm(now.start)}–${TextUtil.hhmm(now.end)}", color = PLow, fontSize = 12.sp)
                    }
                    if (nxt != null) Text("Después (${TextUtil.hhmm(nxt.start)}): ${nxt.title}", color = PMid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Sin guía de programación", color = PLow, fontSize = 13.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("▲▼ cambiar · OK lista · ▶ info", color = PLow, fontSize = 11.sp)
                Text("Números: ir a canal", color = PLow, fontSize = 11.sp)
            }
        }
    }
}

/** Formatos a probar para canales en vivo, en orden. */
fun liveFormats(): List<String> = when (Prefs.liveFormat) {
    "ts" -> listOf("ts", "m3u8")
    "m3u8" -> listOf("m3u8", "ts")
    else -> {
        val allowed = Prefs.serverFormats
        if (allowed.isNotEmpty() && "ts" !in allowed && "m3u8" in allowed) listOf("m3u8", "ts") else listOf("ts", "m3u8")
    }
}

fun liveMediaItem(ch: Channel, tryN: Int = 0): MediaItem? {
    val x = Repo.xtream() ?: return null
    val fmts = liveFormats()
    val fmt = fmts[tryN % fmts.size]
    return MediaItem.Builder().setUri(x.liveUrl(ch.id, fmt)).apply { if (fmt == "m3u8") setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
}

/** Video pequeño (vista previa o cuadro de pantalla múltiple). */
@Composable
fun MiniVideo(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { cx ->
            (android.view.LayoutInflater.from(cx).inflate(com.mauricio.cinecasa.R.layout.texture_player, null) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
