package com.mauricio.cinecasa.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val msg: String) : LoadState
    data object Ready : LoadState
    data class Error(val msg: String) : LoadState
}

data class Catalog(
    val movies: List<Vod> = emptyList(),
    val shows: List<Show> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val vodCats: List<Category> = emptyList(),
    val seriesCats: List<Category> = emptyList(),
    val liveCats: List<Category> = emptyList(),
) {
    val movieById: Map<Int, Vod> by lazy { movies.associateBy { it.id } }
    val showById: Map<Int, Show> by lazy { shows.associateBy { it.id } }
    val channelById: Map<Int, Channel> by lazy { channels.associateBy { it.id } }
    val vodCatName: Map<String, String> by lazy { vodCats.associate { it.id to it.name } }
    val seriesCatName: Map<String, String> by lazy { seriesCats.associate { it.id to it.name } }
    val liveCatName: Map<String, String> by lazy { liveCats.associate { it.id to it.name } }
    private val adultIds: Set<String> by lazy {
        (vodCats + seriesCats + liveCats).filter { TextUtil.isAdult(it.name) }.map { it.id }.toSet()
    }

    private val kidsRe = Regex("(infantil|niñ|nin[oa]s|kids|anim|cartoon|caricatura|disney|pixar|familia|family|junior)", RegexOption.IGNORE_CASE)
    private val kidIds: Set<String> by lazy { (vodCats + seriesCats + liveCats).filter { kidsRe.containsMatchIn(it.name) }.map { it.id }.toSet() }
    private val vodIds by lazy { vodCats.map { it.id }.toSet() }
    private val seriesIds by lazy { seriesCats.map { it.id }.toSet() }
    private val kidsVod by lazy { vodIds.any { it in kidIds } }
    private val kidsSeries by lazy { seriesIds.any { it in kidIds } }
    private val kidsLive by lazy { liveCats.any { it.id in kidIds } }

    fun visibleCat(id: String): Boolean {
        if (Profiles.current.value?.kids == true) {
            if (id in adultIds) return false
            if (id in kidIds) return true
            // Si el proveedor no tiene categorías infantiles de ese tipo, se muestra todo lo que no es de adultos
            return when (id) { in vodIds -> !kidsVod; in seriesIds -> !kidsSeries; else -> !kidsLive }
        }
        return Prefs.showAdult || id !in adultIds
    }

    val visMovies: List<Vod> by lazy { movies.filter { visibleCat(it.cat) } }
    val visShows: List<Show> by lazy { shows.filter { visibleCat(it.cat) } }
    val visChannels: List<Channel> by lazy { channels.filter { visibleCat(it.cat) } }
    val visVodCats: List<Category> by lazy { vodCats.filter { visibleCat(it.id) } }
    val visSeriesCats: List<Category> by lazy { seriesCats.filter { visibleCat(it.id) } }
    val visLiveCats: List<Category> by lazy { liveCats.filter { visibleCat(it.id) } }

    /** Índice por id de TMDB y por título normalizado, para cruzar con TMDB. */
    val movieByTmdb: Map<String, List<Vod>> by lazy { visMovies.filter { it.tmdb.isNotBlank() && it.tmdb != "0" }.groupBy { it.tmdb } }
    val showByTmdb: Map<String, List<Show>> by lazy { visShows.filter { it.tmdb.isNotBlank() && it.tmdb != "0" }.groupBy { it.tmdb } }
    val movieByNorm: Map<String, List<Vod>> by lazy { visMovies.groupBy { it.norm } }
    val showByNorm: Map<String, List<Show>> by lazy { visShows.groupBy { it.norm } }

    // ---------- Versiones (mismo título en distintos idiomas o calidades) ----------
    init {
        val vm = vodCats.associate { it.id to it.name }
        val sm = seriesCats.associate { it.id to it.name }
        for (v in movies) if (v.lang == null) {
            val cn = vm[v.cat].orEmpty()
            v.lang = LangDetect.lang(v.name, cn); v.quality = LangDetect.quality(v.name, cn)
        }
        for (v in shows) if (v.lang == null) {
            val cn = sm[v.cat].orEmpty()
            v.lang = LangDetect.lang(v.name, cn); v.quality = LangDetect.quality(v.name, cn)
        }
    }

    /** Clave de grupo: mismo título normalizado; si dos tienen años distintos (remakes), se separan. */
    private fun <T> groupKeys(items: List<T>, norm: (T) -> String, year: (T) -> Int, id: (T) -> Int): Map<Int, String> {
        val out = HashMap<Int, String>(items.size * 2)
        for ((n, list) in items.groupBy(norm)) {
            val years = list.map(year).filter { it > 0 }.groupingBy { it }.eachCount()
            if (years.size <= 1) { list.forEach { out[id(it)] = n }; continue }
            val main = years.maxBy { it.value }.key
            for (it in list) {
                val y = year(it)
                val near = years.keys.firstOrNull { k -> y > 0 && kotlin.math.abs(k - y) <= 1 }
                out[id(it)] = "$n|${if (y == 0) main else (near ?: y)}"
            }
        }
        return out
    }

    private val movieKey: Map<Int, String> by lazy { groupKeys(visMovies, { it.norm }, { it.year }, { it.id }) }
    private val showKey: Map<Int, String> by lazy { groupKeys(visShows, { it.norm }, { it.year }, { it.id }) }
    private val movieGroups: Map<String, List<Vod>> by lazy { visMovies.groupBy { movieKey[it.id] ?: it.norm } }
    private val showGroups: Map<String, List<Show>> by lazy { visShows.groupBy { showKey[it.id] ?: it.norm } }

    private fun vodRank(v: Vod) = LangFilter.rank(v.lang ?: Lang.NONE) * 10 + LangFilter.rankQuality(v.quality)
    private fun showRank(v: Show) = LangFilter.rank(v.lang ?: Lang.NONE) * 10 + LangFilter.rankQuality(v.quality)

    /** Todas las versiones de esta película (idiomas y calidades), la preferida primero. */
    fun movieVariants(v: Vod): List<Vod> =
        movieGroups[movieKey[v.id] ?: v.norm].orEmpty().ifEmpty { listOf(v) }.sortedBy { vodRank(it) }

    fun showVariants(s: Show): List<Show> =
        showGroups[showKey[s.id] ?: s.norm].orEmpty().ifEmpty { listOf(s) }.sortedBy { showRank(it) }

    /** Idiomas disponibles de una película, para la etiqueta de la tarjeta ("LAT · SUB"). */
    fun movieLangs(v: Vod): String = movieVariants(v).mapNotNull { it.lang?.short?.ifBlank { null } }.distinct().joinToString(" · ")
    fun showLangs(s: Show): String = showVariants(s).mapNotNull { it.lang?.short?.ifBlank { null } }.distinct().joinToString(" · ")

    /** Una sola tarjeta por título, respetando el filtro de idioma y escogiendo la mejor versión. */
    fun dedupeMovies(list: List<Vod>): List<Vod> {
        val out = ArrayList<Vod>(list.size)
        val pos = HashMap<String, Int>()
        for (v in list) {
            if (!LangFilter.allows(v.lang ?: Lang.NONE)) continue
            val k = movieKey[v.id] ?: v.norm
            val i = pos[k]
            if (i == null) { pos[k] = out.size; out.add(v) }
            else if (vodRank(v) < vodRank(out[i])) out[i] = v
        }
        return out
    }

    fun dedupeShows(list: List<Show>): List<Show> {
        val out = ArrayList<Show>(list.size)
        val pos = HashMap<String, Int>()
        for (v in list) {
            if (!LangFilter.allows(v.lang ?: Lang.NONE)) continue
            val k = showKey[v.id] ?: v.norm
            val i = pos[k]
            if (i == null) { pos[k] = out.size; out.add(v) }
            else if (showRank(v) < showRank(out[i])) out[i] = v
        }
        return out
    }

    /** Cuántas películas trae cada idioma (para revisar en Ajustes que la detección funcione). */
    val langStats: Map<Lang, Int> by lazy { visMovies.groupingBy { it.lang ?: Lang.NONE }.eachCount() }
}

object Repo {
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> get() = _catalog

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> get() = _state

    private val vodInfos = ConcurrentHashMap<Int, VodInfo>()
    private val seriesInfos = object : LinkedHashMap<Int, SeriesInfo>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, SeriesInfo>?) = size > 40
    }
    private val epgCache = ConcurrentHashMap<Int, Pair<Long, List<EpgItem>>>()
    private var saveJob: Job? = null
    private var refreshJob: Job? = null

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "catalog").apply { mkdirs() }
        Prefs.init(ctx)
        Library.init(ctx)
        Cloud.init(ctx)
        Profiles.init(ctx)
        Downloads.init(ctx)
        Translate.init(ctx)
    }

    fun xtream(): Xtream? = Prefs.account.value?.let { Xtream(it) }

    // ---------------- Catálogo ----------------
    fun start(force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val hadCache = _catalog.value.movies.isNotEmpty() || loadCache()
            if (hadCache) {
                _state.value = LoadState.Ready
                loadVodInfoCache()
            }
            val stale = System.currentTimeMillis() - Prefs.lastRefresh > 8 * 3600_000L
            if (force || !hadCache || stale) refresh(showProgress = force || !hadCache)
        }
    }

    private suspend fun refresh(showProgress: Boolean) = withContext<Unit>(Dispatchers.IO) {
        val x = xtream() ?: return@withContext
        try {
            if (showProgress) _state.value = LoadState.Loading("Conectando con tu servidor…")
            try { Prefs.serverFormats = x.accountInfo().formats } catch (_: Exception) {}
            val cats = async { Triple(x.categories("get_vod_categories"), x.categories("get_series_categories"), x.categories("get_live_categories")) }
            if (showProgress) _state.value = LoadState.Loading("Descargando películas…")
            val movies = x.vods()
            if (showProgress) _state.value = LoadState.Loading("Descargando series… (${"%,d".format(movies.size)} películas listas)")
            val shows = x.shows()
            if (showProgress) _state.value = LoadState.Loading("Descargando canales…")
            val channels = x.channels()
            val (vc, sc, lc) = cats.await()
            val c = Catalog(movies, shows, channels, vc, sc, lc)
            _catalog.value = c
            Prefs.lastRefresh = System.currentTimeMillis()
            _state.value = LoadState.Ready
            saveCache(c)
        } catch (e: Exception) {
            if (_catalog.value.movies.isEmpty() && _catalog.value.channels.isEmpty()) {
                _state.value = LoadState.Error(
                    if (e is java.net.UnknownHostException) "No se encuentra el servidor. Es probable que tu proveedor haya cambiado la dirección: pídele la nueva y elige «Cambiar servidor»."
                    else "No se pudo descargar el catálogo: ${e.message ?: e.javaClass.simpleName}",
                )
            } else {
                _state.value = LoadState.Ready
            }
        }
    }

    /** Re-aplica filtros (p. ej. al cambiar "mostrar adultos"). */
    fun refilter() {
        val c = _catalog.value
        _catalog.value = c.copy()
    }

    /** Sin internet: deja pasar a la app (p. ej. para ver descargas) aunque no haya catálogo. */
    fun markOffline() { if (_state.value is LoadState.Error) _state.value = LoadState.Ready }

    fun logout() {
        clearAll()
        Prefs.setAccount(null)
    }

    fun clearAll() {
        refreshJob?.cancel()
        _catalog.value = Catalog()
        _state.value = LoadState.Idle
        vodInfos.clear()
        synchronized(seriesInfos) { seriesInfos.clear() }
        epgCache.clear()
        dir.listFiles()?.forEach { it.delete() }
        Prefs.lastRefresh = 0
    }

    // ---------------- Caché en disco ----------------
    private fun saveCache(c: Catalog) {
        try {
            writeList(File(dir, "movies.json"), c.movies) { w, v ->
                w.name("stream_id").value(v.id.toLong()); w.name("name").value(v.name); w.name("stream_icon").value(v.icon)
                w.name("rating").value(v.rating.toString()); w.name("added").value(v.added.toString()); w.name("category_id").value(v.cat)
                w.name("container_extension").value(v.ext); w.name("tmdb").value(v.tmdb); w.name("year").value(v.year.toString())
            }
            writeList(File(dir, "shows.json"), c.shows) { w, s ->
                w.name("series_id").value(s.id.toLong()); w.name("name").value(s.name); w.name("cover").value(s.cover)
                w.name("backdrop_path").value(s.backdrop); w.name("plot").value(s.plot); w.name("cast").value(s.cast)
                w.name("director").value(s.director); w.name("genre").value(s.genre); w.name("releaseDate").value(s.release)
                w.name("rating").value(s.rating.toString()); w.name("last_modified").value(s.lastMod.toString())
                w.name("category_id").value(s.cat); w.name("youtube_trailer").value(s.trailer); w.name("tmdb").value(s.tmdb)
            }
            writeList(File(dir, "channels.json"), c.channels) { w, ch ->
                w.name("stream_id").value(ch.id.toLong()); w.name("num").value(ch.num.toLong()); w.name("name").value(ch.name)
                w.name("stream_icon").value(ch.icon); w.name("epg_channel_id").value(ch.epgId); w.name("category_id").value(ch.cat)
                w.name("tv_archive").value(if (ch.archiveDays > 0) "1" else "0"); w.name("tv_archive_duration").value(ch.archiveDays.toString())
            }
            for ((name, list) in listOf("vodcats" to c.vodCats, "seriescats" to c.seriesCats, "livecats" to c.liveCats)) {
                writeList(File(dir, "$name.json"), list) { w, cat -> w.name("category_id").value(cat.id); w.name("category_name").value(cat.name) }
            }
        } catch (_: Exception) {}
    }

    private fun <T> writeList(f: File, items: List<T>, write: (JsonWriter, T) -> Unit) {
        val tmp = File(f.path + ".tmp")
        JsonWriter(tmp.bufferedWriter()).use { w ->
            w.beginArray()
            for (it in items) { w.beginObject(); write(w, it); w.endObject() }
            w.endArray()
        }
        tmp.renameTo(f)
    }

    private fun <T> readList(f: File, map: (Map<String, String>) -> T?): List<T> {
        if (!f.exists()) return emptyList()
        val out = ArrayList<T>()
        JsonReader(f.bufferedReader()).use { r ->
            r.beginArray()
            while (r.hasNext()) map(r.readFlat())?.let(out::add)
            r.endArray()
        }
        return out
    }

    private fun loadCache(): Boolean = try {
        val movies = readList(File(dir, "movies.json")) { Xtream.vodFrom(it) }
        val shows = readList(File(dir, "shows.json")) { Xtream.showFrom(it) }
        val channels = readList(File(dir, "channels.json")) { Xtream.channelFrom(it) }
        fun cats(n: String) = readList(File(dir, "$n.json")) { m -> m["category_id"]?.let { Category(it, m["category_name"].orEmpty()) } }
        if (movies.isEmpty() && shows.isEmpty() && channels.isEmpty()) false
        else {
            _catalog.value = Catalog(movies, shows, channels, cats("vodcats"), cats("seriescats"), cats("livecats"))
            true
        }
    } catch (e: Exception) { false }

    // ---------------- Detalles con caché ----------------
    fun cachedVodInfo(id: Int): VodInfo? = vodInfos[id]

    suspend fun vodInfo(id: Int): VodInfo? = vodInfos[id] ?: withContext(Dispatchers.IO) {
        try {
            xtream()?.vodInfo(id)?.also { vodInfos[id] = it; scheduleSaveInfos() }
        } catch (e: Exception) { null }
    }

    suspend fun seriesInfo(id: Int): SeriesInfo? {
        synchronized(seriesInfos) { seriesInfos[id] }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                xtream()?.seriesInfo(id)?.also { synchronized(seriesInfos) { seriesInfos[id] = it } }
            } catch (e: Exception) { null }
        }
    }

    suspend fun epg(ch: Int): List<EpgItem> {
        val now = System.currentTimeMillis()
        epgCache[ch]?.let { (t, l) -> if (now - t < 10 * 60_000 && (l.isEmpty() || l.first().end > now)) return l.filter { it.end > now } }
        return withContext(Dispatchers.IO) {
            try {
                val l = xtream()?.shortEpg(ch) ?: emptyList()
                epgCache[ch] = now to l
                l
            } catch (e: Exception) {
                epgCache[ch] = now to emptyList()
                emptyList()
            }
        }
    }

    suspend fun accountInfo(): AccountInfo? = withContext(Dispatchers.IO) {
        try { xtream()?.accountInfo(quick = true) } catch (e: Exception) { null }
    }

    /** Guarda reparto/dirección de las películas ya consultadas: así la búsqueda por actor mejora con el uso. */
    private fun scheduleSaveInfos() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(4000)
            try {
                val f = File(dir, "vodinfo.json")
                val tmp = File(dir, "vodinfo.json.tmp")
                JsonWriter(tmp.bufferedWriter()).use { w ->
                    w.beginArray()
                    vodInfos.forEach { (id, i) ->
                        w.beginObject()
                        w.name("id").value(id.toLong()); w.name("plot").value(i.plot.take(600)); w.name("cast").value(i.cast)
                        w.name("director").value(i.director); w.name("genre").value(i.genre); w.name("release").value(i.release)
                        w.name("dur").value(i.durationSecs.toLong()); w.name("rating").value(i.rating.toString())
                        w.name("backdrop").value(i.backdrop); w.name("poster").value(i.poster); w.name("trailer").value(i.trailer)
                        w.name("tmdb").value(i.tmdb)
                        w.endObject()
                    }
                    w.endArray()
                }
                tmp.renameTo(f)
            } catch (_: Exception) {}
        }
    }

    private fun loadVodInfoCache() {
        if (vodInfos.isNotEmpty()) return
        try {
            readList(File(dir, "vodinfo.json")) { m ->
                val id = m["id"]?.toIntOrNull() ?: return@readList null
                id to VodInfo(
                    plot = m["plot"].orEmpty(), cast = m["cast"].orEmpty(), director = m["director"].orEmpty(),
                    genre = m["genre"].orEmpty(), release = m["release"].orEmpty(), durationSecs = m["dur"]?.toIntOrNull() ?: 0,
                    rating = m["rating"]?.toFloatOrNull() ?: 0f, backdrop = m["backdrop"].orEmpty(), poster = m["poster"].orEmpty(),
                    trailer = m["trailer"].orEmpty(), tmdb = m["tmdb"].orEmpty(),
                )
            }.forEach { vodInfos[it.first] = it.second }
        } catch (_: Exception) {}
    }

    // ---------------- Búsqueda ----------------
    fun searchLocal(raw: String): SearchResults {
        val q = SearchEngine.Query(raw)
        if (!q.valid) return SearchResults(raw)
        val c = _catalog.value
        val movies = c.visMovies.asSequence()
            .map { it to SearchEngine.scoreTitle(q, it.norm, it.year) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Vod, Int>> { it.second }.thenByDescending { it.first.added })
            .map { it.first }.toList().let { c.dedupeMovies(it) }.take(60)
        val shows = c.visShows.asSequence()
            .map { it to SearchEngine.scoreTitle(q, it.norm, it.year) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Show, Int>> { it.second }.thenByDescending { it.first.lastMod })
            .map { it.first }.toList().let { c.dedupeShows(it) }.take(60)
        val channels = if (q.year > 0) emptyList() else c.visChannels.asSequence()
            .map { it to SearchEngine.scoreTitle(q, it.norm, 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }.take(40).toList()
        val peopleShows = c.dedupeShows(c.visShows.filter { SearchEngine.matchPeople(q, it.normPeople) }).take(40)
        val peopleMovies = vodInfos.entries.asSequence()
            .filter { SearchEngine.matchPeople(q, it.value.normPeople) }
            .mapNotNull { c.movieById[it.key] }.filter { c.visibleCat(it.cat) }.toList().let { c.dedupeMovies(it) }.take(40)
        return SearchResults(raw, movies, shows, channels, peopleMovies, peopleShows)
    }

    /** Búsqueda de actor/director con TMDB y cruce contra tu catálogo. */
    fun searchPerson(raw: String): Triple<Tmdb.Person, List<Vod>, List<Show>>? {
        if (!Tmdb.enabled || raw.trim().length < 4) return null
        return try {
            val q = SearchEngine.Query(raw)
            val person = Tmdb.searchPeople(raw.trim()).firstOrNull { SearchEngine.matchPeople(q, TextUtil.norm(it.name)) } ?: return null
            val credits = Tmdb.credits(person.id)
            val c = _catalog.value
            val movies = LinkedHashSet<Vod>()
            val shows = LinkedHashSet<Show>()
            for (cr in credits) {
                if (cr.mediaType == "tv") {
                    val hit = c.showByTmdb[cr.tmdbId] ?: listOf(cr.title, cr.original).flatMap { t ->
                        c.showByNorm[TextUtil.norm(t)].orEmpty().filter { cr.year == 0 || it.year == 0 || kotlin.math.abs(it.year - cr.year) <= 1 }
                    }
                    hit.firstOrNull()?.let(shows::add)
                } else {
                    val hit = c.movieByTmdb[cr.tmdbId] ?: listOf(cr.title, cr.original).flatMap { t ->
                        c.movieByNorm[TextUtil.norm(t)].orEmpty().filter { cr.year == 0 || it.year == 0 || kotlin.math.abs(it.year - cr.year) <= 1 }
                    }
                    hit.firstOrNull()?.let(movies::add)
                }
            }
            Triple(person, c.dedupeMovies(movies.toList()), c.dedupeShows(shows.toList()))
        } catch (e: Exception) { null }
    }
}
