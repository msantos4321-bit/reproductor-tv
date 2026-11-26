package com.mauricio.cinecasa

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mauricio.cinecasa.data.Catalog
import com.mauricio.cinecasa.data.Category
import com.mauricio.cinecasa.data.Channel
import com.mauricio.cinecasa.data.GrowingFileDataSource
import com.mauricio.cinecasa.data.Quiniela
import com.mauricio.cinecasa.data.Recorder
import com.mauricio.cinecasa.data.Repo
import com.mauricio.cinecasa.data.SpEvent
import com.mauricio.cinecasa.data.Sports
import com.mauricio.cinecasa.data.SportsCats
import com.mauricio.cinecasa.data.Teams
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Datos de muestra: los mismos que manda la función «deportes», con horas relativas a ahora. */
object Muestra {
    fun json(name: String): String {
        val raw = Muestra::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()
        val now = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return Regex("\"NOW([+-]\\d+)\"").replace(raw) { m -> "\"" + fmt.format(java.util.Date(now + m.groupValues[1].toLong() * 60_000)) + "\"" }
    }

    fun events(): List<SpEvent> = Sports.parseBoard(json("board.json"))

    fun catalog() = Catalog(
        channels = listOf(
            Channel(1, 101, "MX: TUDN HD", "", "tudn", "10", 0),
            Channel(2, 102, "US: CBS HD", "", "cbs", "11", 0),
            Channel(3, 103, "NFL 03: Chiefs vs Chargers", "", "", "12", 0),
            Channel(4, 104, "LMP: Águilas de Mexicali vs Charros de Jalisco", "", "", "12", 0),
            Channel(5, 105, "ESPN 2 MX", "", "espn2", "10", 0),
            Channel(6, 201, "Canal 5", "", "c5", "20", 0),
            Channel(7, 202, "Las Estrellas", "", "las", "20", 0),
        ),
        liveCats = listOf(Category("10", "DEPORTES MX"), Category("11", "USA | SPORTS"), Category("12", "PPV EVENTOS"), Category("20", "MÉXICO NACIONALES")),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SportsTest {
    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Repo.init(ctx)
    }

    @Test fun leeLosMarcadores() {
        val l = Muestra.events()
        assertEquals(9, l.size)
        val lmp = l.first { it.lg == "lmp" && it.live }
        assertEquals("Charros", lmp.a.n)
        assertEquals("Baja 7ª", lmp.det)
        assertEquals(2, lmp.sit?.outs)
        assertTrue(lmp.sit!!.b1 && lmp.sit!!.b3)
        val nfl = l.first { it.id == "401873081" }
        assertTrue(nfl.upcoming)
        assertEquals("a", nfl.odds?.fav)
        assertEquals(55.5, nfl.odds?.ou ?: 0.0, 0.01)
        assertTrue(l.first { it.lg == "nba" }.h.win)
        assertTrue(l.first { it.lg == "ufc" }.title.startsWith("UFC 323"))
    }

    @Test fun detectaCategoriasDeDeportes() {
        val ids = SportsCats.auto(Muestra.catalog())
        assertEquals(setOf("10", "11", "12"), ids)
        assertTrue(SportsCats.isPpv("PPV EVENTOS"))
        assertFalse(SportsCats.isPpv("DEPORTES MX"))
    }

    @Test fun encuentraElCanalDelPartido() {
        val c = Muestra.catalog()
        val ids = SportsCats.auto(c)
        val l = Muestra.events()
        val kc = l.first { it.id == "401873090" }
        val chs = Sports.channelsFor(kc, c, ids)
        assertEquals("NFL 03: Chiefs vs Chargers", chs.first().name)
        assertTrue("también sugiere CBS", chs.any { it.name == "US: CBS HD" })
        val lmp = l.first { it.lg == "lmp" && it.live }
        assertEquals(4, Sports.channelsFor(lmp, c, ids).first().id)
        // Un partido sin canal no inventa canales de otras categorías
        val nba = l.first { it.lg == "nba" }
        assertTrue(Sports.channelsFor(nba, c, ids).none { it.cat == "20" })
    }

    @Test fun quinielaCuentaAciertos() {
        val l = Muestra.events()
        val up = l.first { it.id == "401873081" }
        Quiniela.choose(up, "p1", "a")
        assertEquals("a", Quiniela.mine(up, "p1")?.pick)
        // El partido termina: gana el visitante (Bears)
        val fin = up.copy(st = "post", a = up.a.copy(sc = "24", win = true), h = up.h.copy(sc = "20"))
        Quiniela.settle(listOf(fin))
        assertTrue(Quiniela.mine(up, "p1")!!.correct)
        assertEquals(1, Quiniela.standings().first { it.first == "p1" }.second)
    }

    @Test fun sinMarcadoresOcultaYSePuedeMostrar() {
        val l = Muestra.events()
        val kc = l.first { it.id == "401873090" }
        Teams.load("[]", "[\"nfl:12\"]")
        assertTrue(Teams.hidden(kc))
        Teams.reveal(kc.key)
        assertFalse(Teams.hidden(kc))
        val lions = l.first { it.id == "401873081" }
        assertFalse("antes del partido no hay nada que ocultar", Teams.hidden(lions))
    }

    @Test fun fichaDeEquipo() {
        val p = Sports.parseTeamPage("nfl", Muestra.json("team.json"))
        assertEquals("Kansas City Chiefs", p.team.full)
        assertEquals(3, p.past.size)
        assertEquals("P", p.past.first().res)
        assertEquals(2, p.inj.size)
    }

    @Test fun grabacionSeVeMientrasCrece() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val f = File(ctx.cacheDir, "prueba.ts").apply { writeBytes(ByteArray(1000) { 1 }) }
        val r = Recorder.Rec(1, 1, "Canal", "Prueba", 0, Long.MAX_VALUE, status = Recorder.RECORDING, path = f.path)
        Recorder.debugSet(listOf(r))
        val uri = Uri.fromFile(f)
        assertTrue(Recorder.isGrowing(uri))
        val ds = GrowingFileDataSource()
        ds.open(DataSpec(uri))
        val buf = ByteArray(4096)
        var got = 0
        while (got < 1000) got += ds.read(buf, 0, buf.size)
        // Llega más video mientras se está viendo
        Thread { Thread.sleep(400); f.appendBytes(ByteArray(500) { 2 }) }.start()
        val n = ds.read(buf, 0, buf.size)
        assertEquals(500, n)
        assertEquals(2.toByte(), buf[0])
        // Al terminar la grabación, el lector llega al final
        Recorder.debugSet(listOf(r.copy(status = Recorder.DONE)))
        assertEquals(androidx.media3.common.C.RESULT_END_OF_INPUT, ds.read(buf, 0, buf.size))
        ds.close()
    }

    @Test fun estimaEspacio() {
        assertEquals(Recorder.BYTES_PER_HOUR * 3, Recorder.estimate(3 * 3600_000L))
    }
}
