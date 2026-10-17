package com.mauricio.cinecasa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import java.util.Calendar

/**
 * Paleta inspirada en Apple: gris perla, tarjetas blancas y azul de sistema.
 * De noche (o si se elige) cambia a grafito suave, nunca negro puro.
 * Todos los colores se leen en vivo, así que el cambio de tema es instantáneo.
 */
object C {
    var dark by mutableStateOf(false)

    val Bg get() = if (dark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)
    val Panel get() = if (dark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val Panel2 get() = if (dark) Color(0xFF3A3A3C) else Color(0xFFE8E8ED)
    val Line get() = if (dark) Color(0xFF48484A) else Color(0xFFD2D2D7)
    val Accent get() = if (dark) Color(0xFF0A84FF) else Color(0xFF0071E3)
    val AccentSoft get() = if (dark) Color(0x330A84FF) else Color(0x1A0071E3)
    val OnAccent = Color.White
    /** Botón principal: negro elegante de día, blanco de noche. */
    val Strong get() = if (dark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F)
    val OnStrong get() = if (dark) Color(0xFF1D1D1F) else Color(0xFFFFFFFF)
    val Hi get() = if (dark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F)
    val Mid get() = if (dark) Color(0xFFC7C7CC) else Color(0xFF515154)
    val Low get() = if (dark) Color(0xFF8E8E93) else Color(0xFF86868B)
    val Good get() = if (dark) Color(0xFF30D158) else Color(0xFF1E9E5A)
    val Warn get() = if (dark) Color(0xFFFFD60A) else Color(0xFFB26A00)
    val Gold get() = if (dark) Color(0xFFFFD60A) else Color(0xFFB07800)
    val Glass get() = if (dark) Color(0xF22C2C2E) else Color(0xF2FFFFFF)

    /** "light" | "dark" | "auto" (oscuro de 8 pm a 7 am). */
    fun apply(mode: String) {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> h >= 20 || h < 7
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CineTheme(content: @Composable () -> Unit) {
    val scheme = if (C.dark) darkColorScheme(
        primary = C.Accent, onPrimary = C.OnAccent, background = C.Bg, onBackground = C.Hi,
        surface = C.Panel, onSurface = C.Hi, surfaceVariant = C.Panel2, onSurfaceVariant = C.Mid, border = C.Line,
    ) else lightColorScheme(
        primary = C.Accent, onPrimary = C.OnAccent, background = C.Bg, onBackground = C.Hi,
        surface = C.Panel, onSurface = C.Hi, surfaceVariant = C.Panel2, onSurfaceVariant = C.Mid, border = C.Line,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
