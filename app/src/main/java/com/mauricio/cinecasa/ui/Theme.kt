package com.mauricio.cinecasa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object C {
    val Bg = Color(0xFF0A0C10)
    val Panel = Color(0xFF14171E)
    val Panel2 = Color(0xFF1D212B)
    val Line = Color(0xFF2A2F3B)
    val Accent = Color(0xFFFF6A3D)
    val AccentSoft = Color(0x33FF6A3D)
    val Hi = Color(0xFFF2F3F5)
    val Mid = Color(0xFFB4BAC6)
    val Low = Color(0xFF737B8C)
    val Good = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFC145)
    val Gold = Color(0xFFFFC94D)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = Color.Black,
            background = C.Bg,
            onBackground = C.Hi,
            surface = C.Panel,
            onSurface = C.Hi,
            surfaceVariant = C.Panel2,
            onSurfaceVariant = C.Mid,
            border = C.Line,
        ),
        content = content,
    )
}
