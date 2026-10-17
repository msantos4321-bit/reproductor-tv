package com.mauricio.cinecasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mauricio.cinecasa.ui.App
import com.mauricio.cinecasa.ui.CineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Todas las teles reportan tamaños distintos; fijamos el lienzo a 960 de ancho
            // para que el diseño se vea igual en cualquier pantalla (720p, 1080p o 4K).
            val dm = resources.displayMetrics
            val w = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
            CompositionLocalProvider(LocalDensity provides Density(density = w / 960f, fontScale = 1f)) {
                CineTheme { App() }
            }
        }
    }
}
