package com.mauricio.cinecasa.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// ======================================================================
// Modelos
// ======================================================================
data class SpTeam(
    val id: String, val n: String, val full: String, val ab: String, val logo: String, val col: String,
    val sc: String = "", val rec: String = "", val win: Boolean = false,
) {
    /** Color del equipo (o uno neutro). */
    val color: Long get() = col.takeIf { it.length == 6 }?.let { ("FF$it").toLongOrNull(16) } ?: 0xFF5A5A60
    val initials: String get() = ab.ifBlank { n.take(3) }.take(4).uppercase()
}

data class SpOdds(val fav: String, val mlh: String, val mla: String, val mld: String, val spread: String, val ou: Double?, val by: String)

data class SpSit(
    val outs: Int?, val b1: Boolean, val b2: Boolean, val b3: Boolean, val balls: Int?, val strikes: Int?,
    val pos: String, val down: String, val rz: Boolean, val last: String,
)

data class SpEvent(
    val id: String, val lg: String, val sp: String, val name: String, val start: Long, val st: String, val det: String,
    val h: SpTeam, val a: SpTeam, val tv: List<String>, val odds: SpOdds?, val note: String, val sit: SpSit?,
) {
    val key get() = "$lg:$id"
    val live get() = st == "in"
    val done get() = st == "post"
    val upcoming get() = st == "pre"
    val title get() = if (name.isNotBlank()) name else "${a.n} vs ${h.n}"
    fun has(teamKey: String) = teamKey == "$lg:${h.id}" || teamKey == "$lg:${a.id}"
    /** Cuánto dura más o menos (para grabar completo). */
    val durationMs: Long get() = when (sp) {
        "football" -> if (lg == "ncaaf") 4 * 3600_000L else 3 * 3600_000L + 45 * 60_000L
        "baseball" -> 3 * 3600_000L + 30 * 60_000L
        "basketball" -> 2 * 3600_000L + 45 * 60_000L
        "hockey" -> 3 * 3600_000L
        "soccer" -> 2 * 3600_000L + 15 * 60_000L
        "mma" -> 5 * 3600_000L
        else -> 3 * 3600_000L
    }
}

data class SpGame(
    val id: String, val start: Long, val st: String, val home: Boolean, val opp: SpTeam,
    val us: String, val them: String, val res: String, val tv: List<String>,
)

data class SpInjury(val n: String, val pos: String, val st: String, val d: String, val ret: String)
data class SpLeader(val cat: String, val n: String, val v: String)
data class SpNews(val t: String, val d: String, val img: String, val at: String)

data class SpTeamPage(
    val lg: String, val team: SpTeam, val rec: String, val home: String, val away: String, val standing: String,
    val past: List<SpGame>, val next: List<SpGame>, val streak: String,
    val inj: List<SpInjury>, val leaders: List<SpLeader>, val news: List<SpNews>,
)

data class SpTableRow(val id: String, val n: String, val ab: String, val logo: String, val v: List<String>)
data class SpTable(val lg: String, val cols: List<String>, val groups: List<Pair<String, List<SpTableRow>>>)

data class SpPlay(val t: String, val clk: String, val scoring: Boolean, val per: Int = 0, val team: String = "", val score: String = "", val kind: String = "")
data class SpLine(val id: String, val ab: String, val home: Boolean, val ls: List<String>, val sc: String)
data class GStat(val l: String, val h: String, val a: String, val hn: Double, val an: Double, val low: Boolean)
data class GPlayerRow(val n: String, val pos: String, val num: String, val starter: Boolean, val v: List<String>)
data class GPlayerGroup(val g: String, val cols: List<String>, val rows: List<GPlayerRow>)
data class GLeader(val cat: String, val n: String, val v: String, val head: String, val pos: String)
data class GOddsSide(val open: String, val now: String, val oddsNow: String = "")
data class GOdds(
    val by: String, val details: String, val favH: Boolean, val favA: Boolean, val favOpenH: Boolean, val favOpenA: Boolean,
    val mlH: GOddsSide, val mlA: GOddsSide, val mlD: String, val spH: GOddsSide, val spA: GOddsSide,
    val ouOpen: String, val ouNow: String, val over: String, val under: String,
)
data class GForm(val r: String, val s: String, val o: String, val logo: String, val at: String, val d: Long)
data class GH2H(val d: Long, val h: String, val a: String, val hs: String, val `as`: String, val w: String)
data class GStarter(val n: String, val num: String, val pos: String, val ev: List<String>)
data class GLineup(val formation: String, val starters: List<GStarter>)
data class SpGameDetail(
    val id: String, val st: String, val det: String, val line: List<SpLine>, val wp: List<Float>,
    val plays: List<SpPlay>, val drive: String, val predHome: Float?, val inj: Map<String, List<SpInjury>>,
    val tv: List<String> = emptyList(), val venue: String = "", val city: String = "", val temp: Int? = null, val rain: Int? = null,
    val stats: List<GStat> = emptyList(), val players: Map<String, List<GPlayerGroup>> = emptyMap(),
    val leaders: Map<String, List<GLeader>> = emptyMap(), val keyPlays: List<SpPlay> = emptyList(), val odds: GOdds? = null,
    val ats: Map<String, List<Pair<String, String>>> = emptyMap(), val last5: Map<String, List<GForm>> = emptyMap(),
    val h2h: List<GH2H> = emptyList(), val lineups: Map<String, GLineup> = emptyMap(),
)

/** Equipo que sigue un perfil. */
data class FavTeam(val lg: String, val id: String, val n: String, val full: String, val ab: String, val logo: String, val col: String) {
    val key get() = "$lg:$id"
    fun asTeam() = SpTeam(id, n, full, ab, logo, col)
}

data class League(val k: String, val short: String, val name: String, val sp: String)

// ======================================================================
// Datos de deportes (vienen de la función «deportes» en la nube)
// ======================================================================
object Sports {
    private const val FN = "https://wnwecaapchcfhcljsvli.supabase.co/functions/v1/deportes"
    private const val KEY = "sb_publishable_vnkSLd5EzN53_LBv76YxUA_QRo2u8ok"

    val leagues = listOf(
        League("lmp", "LMP", "Liga Mexicana del Pacífico", "baseball"),
        League("ligamx", "Liga MX", "Liga MX", "soccer"),
        League("nfl", "NFL", "NFL", "football"),
        League("nba", "NBA", "NBA", "basketball"),
        League("mlb", "MLB", "MLB", "baseball"),
        League("nhl", "NHL", "NHL", "hockey"),
        League("ucl", "Champions", "Champions League", "soccer"),
        League("epl", "Premier", "Premier League", "soccer"),
        League("laliga", "LaLiga", "LaLiga", "soccer"),
        League("mls", "MLS", "MLS", "soccer"),
        League("ncaaf", "NCAA", "Futbol americano colegial", "football"),
        League("ufc", "UFC", "UFC", "mma"),
    )
    val leagueByKey = leagues.associateBy { it.k }

    data class Board(val events: List<SpEvent> = emptyList(), val at: Long = 0, val error: String? = null, val loading: Boolean = false)

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> get() = _board

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.w("CineCasa", "deportes", e) })
    private val users = AtomicInteger(0)
    private var loop: Job? = null

    /** Las pantallas que muestran marcadores se «suscriben»; mientras haya alguien, se actualiza solo. */
    fun subscribe() {
        if (users.incrementAndGet() == 1 || loop?.isActive != true) startLoop()
    }
    fun unsubscribe() {
        if (users.decrementAndGet() <= 0) { users.set(0); loop?.cancel(); loop = null }
    }

    private fun startLoop() {
        loop?.cancel()
        loop = io.launch {
            while (true) {
                refresh()
                val live = _board.value.events.any { it.live }
                delay(if (live) 30_000 else 90_000)
            }
        }
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - _board.value.at < 15_000 && _board.value.events.isNotEmpty()) return@withContext
        _board.value = _board.value.copy(loading = true)
        try {
            val list = parseBoard(get("$FN?q=board"))
            _board.value = Board(list, System.currentTimeMillis(), null, false)
        } catch (e: Exception) {
            _board.value = _board.value.copy(loading = false, error = "No se pudieron cargar los marcadores. Revisa tu internet.")
        }
    }

    /** Lee la respuesta de «?q=board». */
    fun parseBoard(json: String): List<SpEvent> {
        val a = JSONObject(json).optJSONArray("events") ?: JSONArray()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::event) }
    }

    /** Solo para pruebas. */
    internal fun debugSet(b: Board) { _board.value = b }
    internal fun debugMemo(k: String, v: Any) { memo[k] = System.currentTimeMillis() to v }
    fun parseTeamPage(lg: String, json: String): SpTeamPage = teamPage(lg, JSONObject(json))

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("apikey", KEY).header("User-Agent", "CineCasa").build()
        Net.quick.newBuilder().readTimeout(25, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw java.io.IOException("deportes ${r.code}")
            return t
        }
    }

    // ---------- lecturas con caché corto ----------
    private val memo = ConcurrentHashMap<String, Pair<Long, Any>>()
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> memoGet(k: String, ttl: Long, make: () -> T): T? = withContext(Dispatchers.IO) {
        memo[k]?.let { (t, v) -> if (System.currentTimeMillis() - t < ttl) return@withContext v as T }
        try { make().also { memo[k] = System.currentTimeMillis() to it } } catch (_: Exception) { memo[k]?.second as T? }
    }

    suspend fun team(lg: String, id: String): SpTeamPage? = memoGet("team:$lg:$id", 5 * 60_000L) {
        teamPage(lg, JSONObject(get("$FN?q=team&lg=$lg&id=$id")))
    }

    private fun teamPage(lg: String, o: JSONObject): SpTeamPage {
        val t = o.getJSONObject("team")
        return SpTeamPage(
            lg, team(t), t.optString("rec"), t.optString("home"), t.optString("away"), t.optString("standing"),
            o.optJSONArray("past").objs().map(::game), o.optJSONArray("next").objs().map(::game), o.optString("streak"),
            o.optJSONArray("inj").objs().map { SpInjury(it.optString("n"), it.optString("pos"), it.optString("st"), it.optString("d"), it.optString("ret")) },
            o.optJSONArray("leaders").objs().map { SpLeader(it.optString("cat"), it.optString("n"), it.optString("v")) },
            o.optJSONArray("news").objs().map { SpNews(it.optString("t"), it.optString("d"), it.optString("img"), it.optString("at")) },
        )
    }

    suspend fun table(lg: String): SpTable? = memoGet("table:$lg", 15 * 60_000L) {
        val o = JSONObject(get("$FN?q=table&lg=$lg"))
        SpTable(
            lg, o.optJSONArray("cols").strs(),
            o.optJSONArray("groups").objs().map { g ->
                g.optString("n") to g.optJSONArray("rows").objs().map { r ->
                    SpTableRow(r.optString("id"), r.optString("n"), r.optString("ab"), r.optString("logo"), r.optJSONArray("v").strs())
                }
            },
        )
    }

    suspend fun game(lg: String, id: String, live: Boolean): SpGameDetail? = memoGet("game:$lg:$id", if (live) 15_000L else 5 * 60_000L) {
        parseGame(get("$FN?q=game&lg=$lg&id=$id"))
    }

    /** Lee la respuesta de «?q=game». */
    fun parseGame(json: String): SpGameDetail {
        val o = JSONObject(json)
        val wp = o.optJSONArray("wp") ?: JSONArray()
        fun byTeam(a: JSONArray?) = a.objs()
        fun play(it: JSONObject) = SpPlay(it.optString("t"), it.optString("clk"), it.optBoolean("sc"), it.optInt("per"), it.optString("team"), it.optString("score"), it.optString("kind"))
        fun side(x: JSONObject?) = GOddsSide(x?.optString("open").orEmpty(), x?.optString("now").orEmpty(), x?.optString("oddsNow").orEmpty())
        val od = o.optJSONObject("odds")
        return SpGameDetail(
            id = o.optString("id"), st = o.optString("st"), det = o.optString("det"),
            line = o.optJSONArray("teams").objs().map { SpLine(it.optString("id"), it.optString("ab"), it.optBoolean("home"), it.optJSONArray("ls").strs(), it.optString("sc")) },
            wp = (0 until wp.length()).map { wp.optDouble(it, 50.0).toFloat() },
            plays = o.optJSONArray("plays").objs().map(::play),
            drive = o.optString("drive"),
            predHome = o.optJSONObject("pred")?.optDouble("h", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat(),
            inj = byTeam(o.optJSONArray("inj")).associate { t ->
                t.optString("team") to t.optJSONArray("list").objs().map { SpInjury(it.optString("n"), it.optString("pos"), it.optString("st"), it.optString("d"), "") }
            },
            tv = o.optJSONArray("tv").strs(), venue = o.optString("venue"), city = o.optString("city"),
            temp = if (o.isNull("temp") || !o.has("temp")) null else o.optInt("temp"),
            rain = if (o.isNull("rain") || !o.has("rain")) null else o.optInt("rain"),
            stats = o.optJSONArray("stats").objs().map { GStat(it.optString("l"), it.optString("h"), it.optString("a"), it.optDouble("hn", Double.NaN), it.optDouble("an", Double.NaN), it.optBoolean("low")) },
            players = byTeam(o.optJSONArray("players")).associate { t ->
                t.optString("team") to t.optJSONArray("groups").objs().map { g ->
                    GPlayerGroup(g.optString("g"), g.optJSONArray("cols").strs(), g.optJSONArray("rows").objs().map { r ->
                        GPlayerRow(r.optString("n"), r.optString("pos"), r.optString("num"), r.optBoolean("st"), r.optJSONArray("v").strs())
                    })
                }
            },
            leaders = byTeam(o.optJSONArray("leaders")).associate { t ->
                t.optString("team") to t.optJSONArray("items").objs().map { GLeader(it.optString("cat"), it.optString("n"), it.optString("v"), it.optString("head"), it.optString("pos")) }
            },
            keyPlays = o.optJSONArray("keyPlays").objs().map(::play),
            odds = od?.let {
                val ou = it.optJSONObject("ou")
                GOdds(it.optString("by"), it.optString("details"), it.optBoolean("favH"), it.optBoolean("favA"), it.optBoolean("favOpenH"), it.optBoolean("favOpenA"),
                    side(it.optJSONObject("mlH")), side(it.optJSONObject("mlA")), it.optString("mlD"), side(it.optJSONObject("spH")), side(it.optJSONObject("spA")),
                    ou?.optString("open").orEmpty(), ou?.optString("now").orEmpty(), ou?.optString("over").orEmpty(), ou?.optString("under").orEmpty())
            },
            ats = byTeam(o.optJSONArray("ats")).associate { t -> t.optString("team") to t.optJSONArray("recs").objs().map { it.optString("t") to it.optString("s") } },
            last5 = byTeam(o.optJSONArray("last5")).associate { t ->
                t.optString("team") to t.optJSONArray("ev").objs().map { GForm(it.optString("r"), it.optString("s"), it.optString("o"), it.optString("logo"), it.optString("at"), parseTime(it.optString("d"))) }
            },
            h2h = o.optJSONArray("h2h").objs().map { GH2H(parseTime(it.optString("d")), it.optString("h"), it.optString("a"), it.optString("hs"), it.optString("as"), it.optString("w")) },
            lineups = byTeam(o.optJSONArray("lineups")).associate { t ->
                t.optString("team") to GLineup(t.optString("formation"), t.optJSONArray("starters").objs().map { GStarter(it.optString("n"), it.optString("num"), it.optString("pos"), it.optJSONArray("ev").strs()) })
            },
        )
    }

    /** Partido del tablero que pasa en este canal (por los nombres de los dos equipos en el nombre del canal). */
    fun eventForChannel(ch: Channel, events: List<SpEvent>): SpEvent? {
        val n = ch.norm
        if (n.length < 6) return null
        return events.filter { e -> !e.done || e.start > System.currentTimeMillis() - 6 * 3600_000 }
            .firstOrNull { e -> words(e.h).any { n.contains(it) } && words(e.a).any { n.contains(it) } }
    }

    suspend fun teams(lg: String): List<SpTeam> = memoGet("teams:$lg", 6 * 3600_000L) {
        JSONObject(get("$FN?q=teams&lg=$lg")).optJSONArray("teams").objs().map(::team)
    } ?: emptyList()

    // ---------- lectura del JSON ----------
    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun team(o: JSONObject) = SpTeam(
        o.optString("id"), o.optString("n"), o.optString("full"), o.optString("ab"), o.optString("logo"), o.optString("col"),
        o.optString("sc"), o.optString("rec"), o.optBoolean("win"),
    )

    fun parseTime(s: String): Long = try {
        val t = s.trim().removeSuffix("Z")
        val fmt = if (t.length <= 16) "yyyy-MM-dd'T'HH:mm" else "yyyy-MM-dd'T'HH:mm:ss"
        java.text.SimpleDateFormat(fmt, java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(t.take(if (t.length <= 16) 16 else 19))!!.time
    } catch (_: Exception) { 0L }

    private fun event(o: JSONObject): SpEvent? {
        val start = parseTime(o.optString("start"))
        if (start == 0L) return null
        val od = o.optJSONObject("odds")
        val si = o.optJSONObject("sit")
        return SpEvent(
            o.optString("id"), o.optString("lg"), o.optString("sp"), o.optString("name"), start, o.optString("st"), o.optString("det"),
            team(o.optJSONObject("h") ?: JSONObject()), team(o.optJSONObject("a") ?: JSONObject()),
            o.optJSONArray("tv").strs().filter { it.isNotBlank() },
            od?.let { SpOdds(it.optString("fav"), it.optString("mlh"), it.optString("mla"), it.optString("mld"), it.optString("spread"), if (it.isNull("ou")) null else it.optDouble("ou"), it.optString("by")) },
            o.optString("note"),
            si?.let {
                SpSit(
                    if (it.isNull("outs")) null else it.optInt("outs"), it.optBoolean("b1"), it.optBoolean("b2"), it.optBoolean("b3"),
                    if (it.isNull("balls")) null else it.optInt("balls"), if (it.isNull("strikes")) null else it.optInt("strikes"),
                    it.optString("pos"), it.optString("down"), it.optBoolean("rz"), it.optString("last"),
                )
            },
        )
    }

    private fun game(o: JSONObject) = SpGame(
        o.optString("id"), parseTime(o.optString("start")), o.optString("st"), o.optBoolean("home"),
        team(o.optJSONObject("opp") ?: JSONObject()), o.optString("us"), o.optString("them"), o.optString("res"), o.optJSONArray("tv").strs(),
    )

    // ======================================================================
    // ¿En qué canal pasa? (por nombre del canal y por la guía de programación)
    // ======================================================================
    private val epgHits = ConcurrentHashMap<String, List<Int>>()

    private fun words(t: SpTeam): List<String> {
        val out = mutableListOf<String>()
        val n = TextUtil.norm(t.n); val f = TextUtil.norm(t.full)
        if (n.length >= 4) out += n
        if (f.length >= 4 && f != n) out += f
        // «Chicago Bears» -> «bears»; «Águilas de Mexicali» -> «mexicali»
        f.split(" de ").lastOrNull()?.takeIf { it.length >= 5 && it != f }?.let { out += it }
        f.split(' ').lastOrNull()?.takeIf { it.length >= 5 }?.let { out += it }
        return out.distinct()
    }

    private val tvAlias = mapOf(
        "espn" to listOf("espn"), "espn2" to listOf("espn 2", "espn2"), "abc" to listOf("abc"), "cbs" to listOf("cbs"),
        "nbc" to listOf("nbc"), "fox" to listOf("fox"), "fs1" to listOf("fs1", "fox sports 1"), "fs2" to listOf("fs2", "fox sports 2"),
        "tnt" to listOf("tnt"), "tbs" to listOf("tbs"), "nfl net" to listOf("nfl network", "nfl net"), "nba tv" to listOf("nba tv"),
        "mlb net" to listOf("mlb network", "mlb net"), "nhl net" to listOf("nhl network"), "peacock" to listOf("peacock"),
        "prime video" to listOf("prime video", "amazon prime"), "netflix" to listOf("netflix"), "espn+" to listOf("espn+", "espn plus"),
        "tudn" to listOf("tudn"), "univision" to listOf("univision"), "telemundo" to listOf("telemundo"), "unimas" to listOf("unimas"),
    )

    /** Canales que probablemente pasan el partido, del más probable al menos. */
    fun channelsFor(ev: SpEvent, c: Catalog, sportsCats: Set<String>): List<Channel> {
        val hw = words(ev.h); val aw = words(ev.a)
        val scored = mutableMapOf<Int, Int>()
        for (ch in c.visChannels) {
            val n = ch.norm
            val sh = hw.any { n.contains(it) }; val sa = aw.any { n.contains(it) }
            var s = 0
            if (sh && sa) s = 100 else if (sh || sa) s = if (ch.cat in sportsCats) 60 else 0
            if (s > 0) scored[ch.id] = maxOf(scored[ch.id] ?: 0, s)
        }
        epgHits[ev.key]?.forEach { id -> scored[id] = maxOf(scored[id] ?: 0, 90) }
        // Cadenas de TV (CBS, FOX, ESPN…) solo dentro de deportes o si el nombre es claro
        val nets = ev.tv.map { TextUtil.norm(it) }.flatMap { k -> tvAlias.entries.firstOrNull { (a, _) -> k == a || k.startsWith(a) }?.value ?: listOf(k) }.filter { it.length >= 3 }
        if (nets.isNotEmpty()) for (ch in c.visChannels) {
            if (ch.id in scored) continue
            val n = " " + ch.norm + " "
            if (nets.any { Regex("(^|[^a-z0-9])" + Regex.escape(it) + "([^a-z0-9+]|$)").containsMatchIn(n) }) scored[ch.id] = if (ch.cat in sportsCats) 40 else 25
        }
        return scored.entries.sortedByDescending { it.value }.mapNotNull { c.channelById[it.key] }.take(12)
    }

    /** Busca el partido en la guía de los canales de deportes (tarda unos segundos; se guarda). */
    suspend fun scanEpg(ev: SpEvent, c: Catalog, sportsCats: Set<String>): List<Channel> = withContext(Dispatchers.IO) {
        epgHits[ev.key]?.let { ids -> return@withContext ids.mapNotNull { c.channelById[it] } }
        val hw = words(ev.h); val aw = words(ev.a)
        val pool = c.visChannels.filter { it.cat in sportsCats && it.epgId.isNotBlank() }.take(120)
        val gate = Semaphore(6)
        val hits = pool.map { ch ->
            async {
                gate.withPermit {
                    val epg = Repo.epg(ch.id)
                    val hit = epg.any { e ->
                        val t = TextUtil.norm(e.title + " " + e.desc)
                        val near = e.end > ev.start - 30 * 60_000 && e.start < ev.start + ev.durationMs
                        near && (hw.any { t.contains(it) } && aw.any { t.contains(it) })
                    }
                    if (hit) ch.id else null
                }
            }
        }.awaitAll().filterNotNull()
        epgHits[ev.key] = hits
        hits.mapNotNull { c.channelById[it] }
    }
}

// ======================================================================
// Qué categorías del proveedor son de deportes
// ======================================================================
object SportsCats {
    private val sportRe = Regex(
        "(deport|sport|ppv|pay ?per ?view|evento|event|nfl|nba|mlb|nhl|ufc|mma|box|fut(bol|ball)|f[uú]tbol|soccer|liga|league|espn|tudn|dazn|bein|golf|tenis|tennis|f1|formula|motor|nascar|wwe|lucha|b[eé]isbol|baseball|basket|hockey|ncaa|beisbol|afizzionados|claro sports|fox ?sports|sky sports|tsn|willow|cricket|rugby|olymp)",
        RegexOption.IGNORE_CASE,
    )
    private val ppvRe = Regex("(ppv|pay ?per ?view|evento|event|replay|nfl ?sunday|game ?pass|league ?pass|mlb ?tv|nba ?tv|nhl ?tv|espn ?\\+|dazn|ufc ?fight|fight ?pass|peacock|flo ?sports)", RegexOption.IGNORE_CASE)

    fun auto(c: Catalog): Set<String> = c.liveCats.filter { sportRe.containsMatchIn(it.name) && !TextUtil.isAdult(it.name) }.map { it.id }.toSet()

    /** Categorías de deportes en uso (automáticas o las que eligió el dueño). */
    fun ids(c: Catalog): Set<String> {
        val custom = Prefs.sportsCats
        if (custom != null) return custom.filter { id -> c.liveCats.any { it.id == id } }.toSet()
        return auto(c)
    }

    fun isPpv(name: String) = ppvRe.containsMatchIn(name)
}

// ======================================================================
// Equipos que sigue cada perfil y «sin marcadores» (se guardan con el perfil)
// ======================================================================
object Teams {
    private val _list = MutableStateFlow<List<FavTeam>>(emptyList())
    val list: StateFlow<List<FavTeam>> get() = _list
    private val _noSpoil = MutableStateFlow<Set<String>>(emptySet())
    val noSpoil: StateFlow<Set<String>> get() = _noSpoil
    /** Marcadores que el usuario pidió ver aunque tenga «sin marcadores» (solo en esta sesión). */
    private val _revealed = MutableStateFlow<Set<String>>(emptySet())
    val revealed: StateFlow<Set<String>> get() = _revealed

    internal fun load(raw: String?, rawNo: String?) {
        _list.value = try {
            val a = JSONArray(raw ?: "[]"); (0 until a.length()).mapNotNull { a.optJSONObject(it)?.toFav() }
        } catch (_: Exception) { emptyList() }
        _noSpoil.value = try { val a = JSONArray(rawNo ?: "[]"); (0 until a.length()).map { a.getString(it) }.toSet() } catch (_: Exception) { emptySet() }
        _revealed.value = emptySet()
    }
    internal fun dump(): String = JSONArray().apply { _list.value.forEach { put(it.toJson()) } }.toString()
    internal fun dumpNo(): String = JSONArray(_noSpoil.value.toList()).toString()

    fun follows(key: String) = _list.value.any { it.key == key }

    fun toggle(lg: String, t: SpTeam) {
        val k = "$lg:${t.id}"
        if (follows(k)) {
            _list.value = _list.value.filterNot { it.key == k }
            Library.teamChanged(k, null)
        } else {
            val f = FavTeam(lg, t.id, t.n, t.full, t.ab, t.logo, t.col)
            _list.value = _list.value + f
            Library.teamChanged(k, f.toJson())
        }
    }

    fun setNoSpoil(key: String, on: Boolean) {
        _noSpoil.value = if (on) _noSpoil.value + key else _noSpoil.value - key
        Library.noSpoilChanged(key, on)
    }

    fun reveal(evKey: String) { _revealed.value = _revealed.value + evKey }

    /** ¿Hay que ocultar el marcador de este partido? */
    fun hidden(ev: SpEvent): Boolean = ev.key !in _revealed.value && !ev.upcoming &&
        (_noSpoil.value.contains("${ev.lg}:${ev.h.id}") || _noSpoil.value.contains("${ev.lg}:${ev.a.id}"))

    internal fun applyRemote(kind: String, key: String, data: JSONObject?, deleted: Boolean) {
        when (kind) {
            "team" -> {
                val rest = _list.value.filterNot { it.key == key }
                _list.value = if (deleted || data == null) rest else rest + data.toFav()
            }
            "nospoil" -> _noSpoil.value = if (deleted) _noSpoil.value - key else _noSpoil.value + key
        }
    }

    internal fun FavTeam.toJson() = JSONObject().put("lg", lg).put("id", id).put("n", n).put("full", full).put("ab", ab).put("logo", logo).put("col", col)
    private fun JSONObject.toFav() = FavTeam(optString("lg"), optString("id"), optString("n"), optString("full"), optString("ab"), optString("logo"), optString("col"))
}

// ======================================================================
// Quiniela de la casa: cada perfil elige quién gana; sin dinero, solo por el gusto
// ======================================================================
object Quiniela {
    data class Pick(
        val ev: String, val profile: String, val pick: String, // "h" | "a" | "d"
        val title: String, val start: Long, val result: String = "", // "" sin resultado
    ) {
        val key get() = "$ev|$profile"
        val correct get() = result.isNotBlank() && result == pick
    }

    private lateinit var sp: SharedPreferences
    private val _picks = MutableStateFlow<Map<String, Pick>>(emptyMap())
    val picks: StateFlow<Map<String, Pick>> get() = _picks

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("quiniela", Context.MODE_PRIVATE)
        _picks.value = try {
            val o = JSONObject(sp.getString("p", "{}") ?: "{}")
            o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.toPick() }.associateBy { it.key }
        } catch (_: Exception) { emptyMap() }
    }

    fun mine(ev: SpEvent, profile: String): Pick? = _picks.value["${ev.key}|$profile"]

    fun choose(ev: SpEvent, profile: String, pick: String) {
        if (!ev.upcoming) return
        val p = Pick(ev.key, profile, pick, ev.title, ev.start)
        put(p)
    }

    /** Cuando un partido termina, anota el resultado en las elecciones. */
    fun settle(events: List<SpEvent>) {
        val byKey = events.filter { it.done }.associateBy { it.key }
        for (p in _picks.value.values) {
            if (p.result.isNotBlank()) continue
            val e = byKey[p.ev] ?: continue
            val r = when { e.h.win -> "h"; e.a.win -> "a"; e.h.sc.isNotBlank() && e.h.sc == e.a.sc -> "d"; else -> null } ?: continue
            put(p.copy(result = r))
        }
    }

    private fun put(p: Pick) {
        _picks.value = _picks.value + (p.key to p)
        persist()
        if (::sp.isInitialized) Cloud.enqueue("_", "pick", p.key, p.toJson())
    }

    /** Tabla: perfil -> (aciertos, jugados) de los últimos 30 días. */
    fun standings(): List<Triple<String, Int, Int>> {
        val since = System.currentTimeMillis() - 30L * 86400_000
        return _picks.value.values.filter { it.start > since && it.result.isNotBlank() }
            .groupBy { it.profile }
            .map { (pid, l) -> Triple(pid, l.count { it.correct }, l.size) }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
    }

    fun applyRemote(key: String, data: JSONObject?, deleted: Boolean) {
        _picks.value = if (deleted || data == null) _picks.value - key else {
            val p = data.toPick()
            val cur = _picks.value[key]
            // El resultado nunca se borra con una copia vieja
            _picks.value + (key to if (cur != null && cur.result.isNotBlank() && p.result.isBlank()) p.copy(result = cur.result) else p)
        }
        persist()
    }

    private fun persist() {
        if (!::sp.isInitialized) return
        val cutoff = System.currentTimeMillis() - 60L * 86400_000
        val o = JSONObject()
        _picks.value.values.filter { it.start > cutoff }.forEach { o.put(it.key, it.toJson()) }
        sp.edit().putString("p", o.toString()).apply()
    }

    private fun Pick.toJson() = JSONObject().put("ev", ev).put("pr", profile).put("p", pick).put("t", title).put("s", start).put("r", result)
    private fun JSONObject.toPick() = Pick(optString("ev"), optString("pr"), optString("p"), optString("t"), optLong("s"), optString("r"))
}
