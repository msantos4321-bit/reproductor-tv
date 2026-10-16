package com.mauricio.cinecasa.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Progress(
    val key: String,
    val kind: String, // "movie" | "episode"
    val id: Int,
    val seriesId: Int,
    val title: String,
    val subtitle: String,
    val image: String,
    val pos: Long,
    val dur: Long,
    val updated: Long,
    val season: Int,
    val ep: Int,
) {
    val fraction: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

data class Saved(val kind: String, val id: Int, val title: String, val image: String, val added: Long)

/** Lo personal: continuar viendo, mi lista, episodios vistos, canales favoritos y recientes. */
object Library {
    private lateinit var sp: SharedPreferences

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> get() = _progress

    private val _myList = MutableStateFlow<List<Saved>>(emptyList())
    val myList: StateFlow<List<Saved>> get() = _myList

    private val _watched = MutableStateFlow<Set<Int>>(emptySet())
    val watched: StateFlow<Set<Int>> get() = _watched

    private val _favChannels = MutableStateFlow<List<Int>>(emptyList())
    val favChannels: StateFlow<List<Int>> get() = _favChannels

    private val _recentChannels = MutableStateFlow<List<Int>>(emptyList())
    val recentChannels: StateFlow<List<Int>> get() = _recentChannels

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("library", Context.MODE_PRIVATE)
        _progress.value = try {
            val o = JSONObject(sp.getString("progress", "{}") ?: "{}")
            o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { k to it.toProgress() } }.toMap()
        } catch (e: Exception) { emptyMap() }
        _myList.value = try {
            val a = JSONArray(sp.getString("mylist", "[]"))
            List(a.length()) { a.getJSONObject(it) }.map {
                Saved(it.optString("kind"), it.optInt("id"), it.optString("title"), it.optString("image"), it.optLong("added"))
            }
        } catch (e: Exception) { emptyList() }
        _watched.value = intList("watched").toSet()
        _favChannels.value = intList("favch")
        _recentChannels.value = intList("recentch")
    }

    private fun intList(k: String): List<Int> = try {
        val a = JSONArray(sp.getString(k, "[]"))
        List(a.length()) { a.getInt(it) }
    } catch (e: Exception) { emptyList() }

    private fun putInts(k: String, v: Collection<Int>) = sp.edit().putString(k, JSONArray(v.toList()).toString()).apply()

    // ---------- Progreso ----------
    fun saveProgress(item: PlayItem, pos: Long, dur: Long) {
        if (dur <= 0 || pos < 0) return
        val finished = pos >= dur * 0.92 || dur - pos < 90_000 && dur > 10 * 60_000
        val m = _progress.value.toMutableMap()
        if (finished) {
            m.remove(item.key)
            if (item.kind == "episode") markWatched(item.id, true)
        } else if (pos > 15_000) {
            // Para series solo guardamos el episodio en curso
            if (item.kind == "episode") m.entries.removeAll { it.value.kind == "episode" && it.value.seriesId == item.seriesId && it.key != item.key }
            m[item.key] = Progress(item.key, item.kind, item.id, item.seriesId, item.title, item.subtitle, item.image, pos, dur, System.currentTimeMillis(), item.season, item.ep)
        }
        _progress.value = m
        persistProgress()
    }

    fun clearProgress(key: String) {
        _progress.value = _progress.value - key
        persistProgress()
    }

    fun progressOf(key: String): Progress? = _progress.value[key]

    fun latestForSeries(seriesId: Int): Progress? =
        _progress.value.values.filter { it.kind == "episode" && it.seriesId == seriesId }.maxByOrNull { it.updated }

    fun continueWatching(): List<Progress> = _progress.value.values.sortedByDescending { it.updated }.take(40)

    private fun persistProgress() {
        val o = JSONObject()
        _progress.value.values.sortedByDescending { it.updated }.take(200).forEach { o.put(it.key, it.toJson()) }
        sp.edit().putString("progress", o.toString()).apply()
    }

    fun markWatched(epId: Int, w: Boolean) {
        _watched.value = if (w) _watched.value + epId else _watched.value - epId
        putInts("watched", _watched.value)
    }

    // ---------- Mi lista ----------
    fun inList(kind: String, id: Int) = _myList.value.any { it.kind == kind && it.id == id }

    fun toggleList(kind: String, id: Int, title: String, image: String) {
        _myList.value = if (inList(kind, id)) _myList.value.filterNot { it.kind == kind && it.id == id }
        else listOf(Saved(kind, id, title, image, System.currentTimeMillis())) + _myList.value
        val a = JSONArray()
        _myList.value.forEach {
            a.put(JSONObject().put("kind", it.kind).put("id", it.id).put("title", it.title).put("image", it.image).put("added", it.added))
        }
        sp.edit().putString("mylist", a.toString()).apply()
    }

    // ---------- Canales ----------
    fun toggleFavChannel(id: Int) {
        _favChannels.value = if (id in _favChannels.value) _favChannels.value - id else _favChannels.value + id
        putInts("favch", _favChannels.value)
    }

    fun touchChannel(id: Int) {
        _recentChannels.value = (listOf(id) + _recentChannels.value.filter { it != id }).take(30)
        putInts("recentch", _recentChannels.value)
    }

    var lastChannel: Int
        get() = sp.getInt("lastch", 0)
        set(v) = sp.edit().putInt("lastch", v).apply()

    private fun Progress.toJson() = JSONObject()
        .put("kind", kind).put("id", id).put("sid", seriesId).put("t", title).put("st", subtitle).put("img", image)
        .put("pos", pos).put("dur", dur).put("up", updated).put("s", season).put("e", ep)

    private fun JSONObject.toProgress() = Progress(
        key = "", kind = optString("kind"), id = optInt("id"), seriesId = optInt("sid"), title = optString("t"),
        subtitle = optString("st"), image = optString("img"), pos = optLong("pos"), dur = optLong("dur"),
        updated = optLong("up"), season = optInt("s"), ep = optInt("e"),
    ).let { it.copy(key = "${it.kind}:${it.id}") }
}
