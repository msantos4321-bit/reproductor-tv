package com.mauricio.cinecasa

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mauricio.cinecasa.data.Recorder
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.Sports
import com.mauricio.cinecasa.data.Teams
import com.mauricio.cinecasa.ui.C
import com.mauricio.cinecasa.ui.CineTheme
import com.mauricio.cinecasa.ui.EventSheet
import com.mauricio.cinecasa.ui.FocusMemory
import com.mauricio.cinecasa.ui.LocalCompact
import com.mauricio.cinecasa.ui.LocalNav
import com.mauricio.cinecasa.ui.Nav
import com.mauricio.cinecasa.ui.RecordingsSection
import com.mauricio.cinecasa.ui.ScoreCard
import com.mauricio.cinecasa.ui.SportsScreen
import com.mauricio.cinecasa.ui.TeamScreen
import com.mauricio.cinecasa.ui.WinProbChart
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dibuja las pantallas nuevas con datos reales de muestra y guarda las imágenes en app/build/capturas
 * (el servidor de compilación las sube para revisarlas).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1000dp-h1500dp-mdpi")
class ShotsTest {
    private val out = File("build/capturas").apply { mkdirs() }

    @Before fun setUp() {
        Repo.init(ApplicationProvider.getApplicationContext<Application>())
        C.dark = true
        Repo.debugSetCatalog(Muestra.catalog())
        Sports.debugSet(Sports.Board(Muestra.events(), System.currentTimeMillis()))
        Teams.load(
            """[{"lg":"nfl","id":"12","n":"Chiefs","full":"Kansas City Chiefs","ab":"KC","logo":"","col":"e31837"},
                {"lg":"lmp","id":"144281","n":"Águilas","full":"Águilas de Mexicali","ab":"MXL","logo":"","col":"8a1538"}]""",
            "[]",
        )
        Sports.debugMemo("team:nfl:12", Sports.parseTeamPage("nfl", Muestra.json("team.json")))
    }

    private val nav = Nav({}, {}, { _, _, _ -> }, { _, _ -> }, { FocusMemory() }, HashMap())

    private fun idle(ms: Long) = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))

    /** Dibuja la vista en una imagen (sin esperar a que terminen las animaciones). */
    private fun draw(v: android.view.View, w: Int, h: Int): Bitmap {
        val full = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(full))
        return Bitmap.createBitmap(full, 0, 0, minOf(w, full.width), minOf(h, full.height))
    }

    private fun shoot(name: String, compact: Boolean = false, w: Int = 960, h: Int = 540, waitRounds: Int = 4, content: @Composable () -> Unit) {
        val act = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        act.setContent {
            CompositionLocalProvider(LocalNav provides nav, LocalCompact provides compact, LocalInspectionMode provides true) {
                CineTheme { Box(Modifier.size(w.dp, h.dp).background(C.Bg)) { content() } }
            }
        }
        idle(1500)
        // Deja terminar lo que llega de internet o de la memoria (fichas de equipos)
        repeat(waitRounds) { Thread.sleep(700); idle(400) }
        save(draw(act.window.decorView, w, h), name)
    }

    private fun save(b: Bitmap, name: String) {
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pantallaDeportesTele() = shoot("1_deportes_tele", h = 1100) { SportsScreen() }

    @Test fun pantallaDeportesCelular() = shoot("2_deportes_celular", compact = true, w = 412, h = 1400) { SportsScreen() }

    @Test fun tarjetas() = shoot("3_tarjetas", w = 960, h = 380) {
        val l = Muestra.events()
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ScoreCard(l.first { it.lg == "lmp" && it.live }) {}
                ScoreCard(l.first { it.id == "401873090" }) {}
                ScoreCard(l.first { it.id == "401873081" }) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ScoreCard(l.first { it.id == "401931982" }) {}
                ScoreCard(l.first { it.lg == "nba" }) {}
                ScoreCard(l.first { it.lg == "ufc" }) {}
            }
        }
    }

    @Test fun fichaCargaDatos() {
        // La ficha pide los datos igual que en la app (sin pantalla): deben llegar de la memoria
        val p = kotlinx.coroutines.runBlocking { Sports.team("nfl", "12") }
        org.junit.Assert.assertEquals("Kansas City Chiefs", p?.team?.full)
        val t = kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(20_000) { Sports.table("nfl") } }
        println("tabla nfl desde la nube: " + (t?.groups?.size ?: "sin internet"))
    }

    @Test fun fichaEquipo() {
        val page = Sports.parseTeamPage("nfl", Muestra.json("team.json"))
        val table = kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(20_000) { Sports.table("nfl") } }
        shoot("4_ficha_equipo", h = 1500) {
            com.mauricio.cinecasa.ui.TeamBody("nfl", com.mauricio.cinecasa.data.SpTeam("12", "Chiefs", "Kansas City Chiefs", "KC", "", "e31837"), page, table)
        }
    }

    @Test fun grabaciones() {
        val now = System.currentTimeMillis()
        Recorder.debugSet(listOf(
            Recorder.Rec(1, 3, "NFL 03: Chiefs vs Chargers", "Chargers vs Chiefs", now - 60 * 60_000, now + 3 * 3600_000, Recorder.RECORDING, "/x.ts", 2_300_000_000),
            Recorder.Rec(2, 2, "US: CBS HD", "Bears vs Lions", now + 5 * 3600_000, now + 9 * 3600_000),
            Recorder.Rec(3, 4, "LMP: Águilas vs Charros", "Charros vs Águilas", now - 30 * 3600_000, now - 26 * 3600_000, Recorder.DONE, "/y.ts", 9_100_000_000, pos = 1_000_000, dur = 4_000_000),
            Recorder.Rec(4, 1, "MX: TUDN HD", "América vs Monterrey", now - 50 * 3600_000, now - 48 * 3600_000, Recorder.FAILED, err = "La tele estaba apagada o sin la app a la hora de grabar."),
        ))
        shoot("5_grabaciones", h = 600) { Column(Modifier.padding(28.dp)) { RecordingsSection(false) } }
    }

    @Test fun probabilidad() = shoot("6_probabilidad", w = 600, h = 220) {
        val l = Muestra.events()
        val e = l.first { it.id == "401873090" }
        WinProbChart(listOf(50f, 55f, 61f, 58f, 47f, 40f, 44f, 52f, 63f, 70f, 66f, 72f), e.h, e.a, Modifier.padding(20.dp).width(540.dp))
    }

    @Test fun opcionesDelPartido() {
        val l = Muestra.events()
        val e = l.first { it.id == "401873081" }
        val act = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        act.setContent {
            CompositionLocalProvider(LocalNav provides nav, LocalCompact provides false, LocalInspectionMode provides true) {
                CineTheme { EventSheet(e, Muestra.catalog(), setOf("10", "11", "12")) {} }
            }
        }
        idle(1500)
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog() ?: return
        val v = d.window!!.decorView
        save(draw(v, v.width, v.height), "7_opciones_partido")
    }
}
