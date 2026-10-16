package com.mauricio.cinecasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mauricio.cinecasa.ui.App
import com.mauricio.cinecasa.ui.CineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CineTheme { App() }
        }
    }
}
