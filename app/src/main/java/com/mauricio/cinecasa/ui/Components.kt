package com.mauricio.cinecasa.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Guarda qué elemento tenía el foco en cada pantalla para regresar ahí al volver. */
class FocusMemory {
    var key: String? = null
    val requester = FocusRequester()
}

fun Modifier.rememberFocus(mem: FocusMemory?, key: String): Modifier {
    if (mem == null) return this
    val base = this.onFocusChanged { if (it.isFocused) mem.key = key }
    return if (mem.key == key) base.focusRequester(mem.requester) else base
}

@Composable
fun RestoreFocus(mem: FocusMemory, fallback: FocusRequester? = null) {
    LaunchedEffect(Unit) {
        delay(60)
        val ok = mem.key != null && runCatching { mem.requester.requestFocus() }.isSuccess
        if (!ok && fallback != null) {
            repeat(5) {
                if (runCatching { fallback.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(100)
            }
        }
    }
}

@Composable
fun Img(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url.isNullOrBlank()) return
    AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = contentScale)
}

private val cardShape = RoundedCornerShape(8.dp)

@Composable
fun PosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    progress: Float? = null,
    badge: String? = null,
    watched: Boolean = false,
    showTitle: Boolean = true,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(cardShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = cardShape)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel2, focusedContainerColor = C.Panel2),
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    title, Modifier.align(Alignment.Center).padding(8.dp), color = C.Mid, fontSize = 12.sp,
                    textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                Img(image, Modifier.fillMaxSize())
                if (badge != null) {
                    Text(
                        badge, Modifier.align(Alignment.TopStart).padding(5.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        color = C.Hi, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (watched) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp), tint = C.Good)
                }
                if (progress != null && progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        }
        if (showTitle) {
            Text(
                title, Modifier.padding(top = 6.dp), color = if (focused) C.Hi else C.Low, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WideCard(
    title: String,
    subtitle: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 210.dp,
    progress: Float? = null,
    watched: Boolean = false,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(cardShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = cardShape)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel2, focusedContainerColor = C.Panel2),
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(title, Modifier.align(Alignment.Center).padding(10.dp), color = C.Mid, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 3)
                Img(image, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))))
                if (watched) Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp), tint = C.Good)
                if (progress != null && progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        }
        Text(title, Modifier.padding(top = 6.dp), color = if (focused) C.Hi else C.Mid, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, color = C.Low, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProgressBar(p: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).background(Color(0x66FFFFFF))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(height).background(C.Accent))
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onFocused: () -> Unit = {},
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.AccentSoft else C.Panel2,
            contentColor = if (selected) C.Accent else C.Mid,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) C.Accent else Color(0x33FFFFFF),
            contentColor = if (primary) Color.Black else C.Hi,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 8.dp), color = C.Hi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(modifier.size(size)) {
        drawArc(
            color = C.Accent, startAngle = a, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(4f, 4f), size = Size(this.size.width - 8f, this.size.height - 8f),
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun CenterMessage(title: String, sub: String = "", loading: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) Spinner()
            Text(title, color = C.Hi, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (sub.isNotBlank()) Text(sub, color = C.Mid, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(520.dp))
            action?.invoke()
        }
    }
}

/**
 * Campo de texto pensado para control remoto: se enfoca como un botón y al presionar OK
 * se abre el teclado. Arriba/abajo o Atrás salen del campo.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    leading: ImageVector? = null,
    boxRequester: FocusRequester = remember { FocusRequester() },
    startEditing: Boolean = false,
) {
    var editing by remember { mutableStateOf(startEditing) }
    var focused by remember { mutableStateOf(false) }
    val fieldFr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.dp)

    fun exit() {
        editing = false
        kb?.hide()
        runCatching { boxRequester.requestFocus() }
    }

    Surface(
        onClick = { editing = true },
        modifier = modifier.focusRequester(boxRequester).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (editing) C.Accent else C.Line), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, C.Accent), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Panel2, focusedContainerColor = C.Panel, contentColor = C.Hi, focusedContentColor = C.Hi),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, null, Modifier.size(20.dp), tint = if (focused || editing) C.Accent else C.Low)
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(color = C.Hi, fontSize = 17.sp),
                        cursorBrush = SolidColor(C.Accent),
                        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onAny = { exit(); onImeAction() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFr)
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionDown || e.key == Key.DirectionUp || e.key == Key.Back)) {
                                    exit(); true
                                } else false
                            },
                    )
                    if (value.isEmpty()) Text(placeholder, color = C.Low, fontSize = 17.sp)
                } else {
                    val shown = if (password && value.isNotEmpty()) "•".repeat(value.length) else value
                    Text(shown.ifEmpty { placeholder }, color = if (value.isEmpty()) C.Low else C.Hi, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            delay(60)
            runCatching { fieldFr.requestFocus() }
            kb?.show()
        }
    }
}

fun stars(r: Float): String = if (r > 0f) "★ " + (if (r > 10f) r / 10f else r).let { "%.1f".format(it) } else ""

@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier, rating: Float = 0f) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rating > 0f) Text(stars(rating), color = C.Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        parts.filter { it.isNotBlank() }.forEach { Text(it, color = C.Mid, fontSize = 14.sp, maxLines = 1) }
    }
}

val ScreenPadding = PaddingValues(start = 28.dp, end = 28.dp)

@Composable
fun Scrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(0f to C.Bg, 0.45f to C.Bg.copy(alpha = 0.85f), 1f to Color.Transparent)),
    )
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to C.Bg)))
}

fun Modifier.roundedClip(r: Dp = 8.dp) = this.clip(RoundedCornerShape(r))
