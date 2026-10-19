package com.mauricio.cinecasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mauricio.cinecasa.ui.App
import com.mauricio.cinecasa.ui.CineTheme
import com.mauricio.cinecasa.ui.DeviceUi
import com.mauricio.cinecasa.ui.LocalCompact

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUi.init(this)
        if (!DeviceUi.isTv) enableEdgeToEdge()
        com.mauricio.cinecasa.ui.C.apply(com.mauricio.cinecasa.data.Prefs.themeMode)
        com.mauricio.cinecasa.data.LangFilter.load()
        readPair(intent)
        val compact = DeviceUi.isCompact(this)
        setContent {
            if (DeviceUi.isTv) {
                // Todas las teles reportan tamaños distintos; fijamos el lienzo a 960 de ancho
                // para que el diseño se vea igual en cualquier pantalla (720p, 1080p o 4K).
                val dm = resources.displayMetrics
                val w = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
                CompositionLocalProvider(LocalDensity provides Density(density = w / 960f, fontScale = 1f), LocalCompact provides false) {
                    CineTheme { App() }
                }
            } else {
                // Celular y tableta: tamaño natural del aparato
                CompositionLocalProvider(LocalCompact provides compact) { CineTheme { App() } }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readPair(intent)
    }

    /** cinecasa://pair?c=123456 → abre «Agregar aparato» con el código ya escrito. */
    private fun readPair(i: android.content.Intent?) {
        val d = i?.data ?: return
        if (d.scheme == "cinecasa" && d.host == "pair") d.getQueryParameter("c")?.filter { it.isDigit() }?.take(6)?.let {
            if (it.length == 6) com.mauricio.cinecasa.ui.pendingPairCode.value = it
        }
    }
}
