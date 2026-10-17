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

/**
 * Lo personal de cada perfil: continuar viendo, mi lista, episodios vistos, canales favoritos y recientes.
 * - Se guarda en la tele por perfil y se sincroniza en la nube con los demás aparatos.
 * - Invitado: no guarda nada (al salir se olvida).
 * - Perfil privado: solo en memoria en este aparato y en la nube; no deja rastro en la tele.
 */
object Library {
    private lateinit var appCtx: Context
    private var sp: SharedPreferences? = null
    /** Perfil activo para sincronizar (null = no sincronizar). */
    private var scope: String? = null

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
        appCtx = ctx.applicationContext
    }

    /**
     * Cambia al perfil indicado. persist=false (invitado/privado) => nada se guarda en este aparato.
     * sync=false (invitado) => nada sube a la nube.
     */
    fun switchTo(profileId: String, persist: Boolean, sync: Boolean) {
        sp = if (persist) appCtx.getSharedPreferences("library_$profileId", Context.MODE_PRIVATE) else null
        scope = if (sync) profileId else null
        val p = sp
        if (p == null) {
            _progress.value = emptyMap(); _myList.value = emptyList(); _watched.value = emptySet()
            _favChannels.value = emptyList(); _recentChannels.value = emptyList()
            return
        }
        _progress.value = try {
            val o = JSONObject(p.getString("progress", "{}") ?: "{}")
            o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { k to it.toProgress() } }.toMap()
        } catch (e: Exception) { emptyMap() }
        _myList.value = try {
            val a = JSONArray(p.getString("mylist", "[]"))
            List(a.length()) { a.getJSONObject(it) }.map { it.toSaved() }
        } catch (e: Exception) { emptyList() }
        _watched.value = intList("watched").toSet()
        _favChannels.value = intList("favch")
        _recentChannels.value = intList("recentch")
    }

    /** La primera vez, lo que ya tenías guardado (antes de los perfiles) pasa a tu perfil principal. */
    fun migrateLegacy(toProfile: String) {
        val old = appCtx.getSharedPreferences("library", Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return
        val dst = appCtx.getSharedPreferences("library_$toProfile", Context.MODE_PRIVATE).edit()
        for ((k, v) in old.all) when (v) {
            is String -> dst.putString(k, v)
            is Int -> dst.putInt(k, v)
            is Long -> dst.putLong(k, v)
            is Boolean -> dst.putBoolean(k, v)
        }
        dst.apply()
        old.edit().clear().apply()
    }

    /** Sube todo lo del perfil (útil la primera vez que se activa la nube). */
    fun pushAll() {
        _myList.value.forEach { emit("mylist", "${it.kind}:${it.id}", it.toJson()) }
        _progress.value.values.forEach { emit("progress", it.key, it.toJson()) }
        _watched.value.forEach { emit("watched", it.toString(), JSONObject().put("w", true)) }
        _favChannels.value.forEach { emit("favch", it.toString(), JSONObject().put("f", true)) }
        emit("recentch", "list", JSONObject().put("ids", JSONArray(_recentChannels.value)))
    }

    private fun emit(kind: String, key: String, data: JSONObject?, deleted: Boolean = false) {
        val s = scope ?: return
        Cloud.enqueue(s, kind, key, data, deleted)
    }

    private fun intList(k: String): List<Int> = try {
        val a = JSONArray(sp?.getString(k, "[]") ?: "[]")
        List(a.length()) { a.getInt(it) }
    } catch (e: Exception) { emptyList() }

    private fun putInts(k: String, v: Collection<Int>) { sp?.edit()?.putString(k, JSONArray(v.toList()).toString())?.apply() }

    // ---------- Progreso ----------
    fun saveProgress(item: PlayItem, pos: Long, dur: Long) {
        if (dur <= 0 || pos < 0) return
        val finished = pos >= dur * 0.92 || dur - pos < 90_000 && dur > 10 * 60_000
        val m = _progress.value.toMutableMap()
        if (finished) {
            if (m.remove(item.key) != null) emit("progress", item.key, null, true)
            if (item.kind == "episode") markWatched(item.id, true)
        } else if (pos > 15_000) {
            // Para series solo guardamos el episodio en curso
            if (item.kind == "episode") {
                val old = m.entries.filter { it.value.kind == "episode" && it.value.seriesId == item.seriesId && it.key != item.key }.map { it.key }
                old.forEach { m.remove(it); emit("progress", it, null, true) }
            }
            val p = Progress(item.key, item.kind, item.id, item.seriesId, item.title, item.subtitle, item.image, pos, dur, System.currentTimeMillis(), item.season, item.ep)
            m[item.key] = p
            emit("progress", p.key, p.toJson())
        }
        _progress.value = m
        persistProgress()
    }

    fun clearProgress(key: String) {
        _progress.value = _progress.value - key
        emit("progress", key, null, true)
        persistProgress()
    }

    fun progressOf(key: String): Progress? = _progress.value[key]

    fun latestForSeries(seriesId: Int): Progress? =
        _progress.value.values.filter { it.kind == "episode" && it.seriesId == seriesId }.maxByOrNull { it.updated }

    fun continueWatching(): List<Progress> = _progress.value.values.sortedByDescending { it.updated }.take(40)

    private fun persistProgress() {
        val p = sp ?: return
        val o = JSONObject()
        _progress.value.values.sortedByDescending { it.updated }.take(200).forEach { o.put(it.key, it.toJson()) }
        p.edit().putString("progress", o.toString()).apply()
    }

    fun markWatched(epId: Int, w: Boolean) {
        _watched.value = if (w) _watched.value + epId else _watched.value - epId
        putInts("watched", _watched.value)
        emit("watched", epId.toString(), if (w) JSONObject().put("w", true) else null, !w)
    }

    // ---------- Mi lista ----------
    fun inList(kind: String, id: Int) = _myList.value.any { it.kind == kind && it.id == id }

    fun toggleList(kind: String, id: Int, title: String, image: String) {
        if (inList(kind, id)) {
            _myList.value = _myList.value.filterNot { it.kind == kind && it.id == id }
            emit("mylist", "$kind:$id", null, true)
        } else {
            val s = Saved(kind, id, title, image, System.currentTimeMillis())
            _myList.value = listOf(s) + _myList.value
            emit("mylist", "$kind:$id", s.toJson())
        }
        persistList()
    }

    private fun persistList() {
        val a = JSONArray()
        _myList.value.forEach { a.put(it.toJson()) }
        sp?.edit()?.putString("mylist", a.toString())?.apply()
    }

    // ---------- Canales ----------
    fun toggleFavChannel(id: Int) {
        val on = id !in _favChannels.value
        _favChannels.value = if (on) _favChannels.value + id else _favChannels.value - id
        putInts("favch", _favChannels.value)
        emit("favch", id.toString(), if (on) JSONObject().put("f", true) else null, !on)
    }

    fun touchChannel(id: Int) {
        _recentChannels.value = (listOf(id) + _recentChannels.value.filter { it != id }).take(30)
        putInts("recentch", _recentChannels.value)
        emit("recentch", "list", JSONObject().put("ids", JSONArray(_recentChannels.value)))
    }

    var lastChannel: Int
        get() = sp?.getInt("lastch", 0) ?: 0
        set(v) { sp?.edit()?.putInt("lastch", v)?.apply() }

    // ---------- Cambios que llegan de otros aparatos ----------
    fun applyRemote(kind: String, key: String, data: JSONObject?, deleted: Boolean) {
        when (kind) {
            "mylist" -> {
                val k = key.substringBefore(':'); val id = key.substringAfter(':').toIntOrNull() ?: return
                val rest = _myList.value.filterNot { it.kind == k && it.id == id }
                _myList.value = if (deleted || data == null) rest else (listOf(data.toSaved()) + rest).sortedByDescending { it.added }
                persistList()
            }
            "progress" -> {
                val m = _progress.value.toMutableMap()
                if (deleted || data == null) m.remove(key) else {
                    val p = data.toProgress()
                    val cur = m[key]
                    if (cur == null || p.updated >= cur.updated) m[key] = p
                }
                _progress.value = m
                persistProgress()
            }
            "watched" -> {
                val id = key.toIntOrNull() ?: return
                _watched.value = if (deleted) _watched.value - id else _watched.value + id
                putInts("watched", _watched.value)
            }
            "favch" -> {
                val id = key.toIntOrNull() ?: return
                _favChannels.value = if (deleted) _favChannels.value - id else if (id in _favChannels.value) _favChannels.value else _favChannels.value + id
                putInts("favch", _favChannels.value)
            }
            "recentch" -> {
                val a = data?.optJSONArray("ids") ?: return
                _recentChannels.value = List(a.length()) { a.optInt(it) }
                putInts("recentch", _recentChannels.value)
            }
        }
    }

    private fun Saved.toJson() = JSONObject().put("kind", kind).put("id", id).put("title", title).put("image", image).put("added", added)
    private fun JSONObject.toSaved() = Saved(optString("kind"), optInt("id"), optString("title"), optString("image"), optLong("added"))

    private fun Progress.toJson() = JSONObject()
        .put("kind", kind).put("id", id).put("sid", seriesId).put("t", title).put("st", subtitle).put("img", image)
        .put("pos", pos).put("dur", dur).put("up", updated).put("s", season).put("e", ep)

    private fun JSONObject.toProgress() = Progress(
        key = "", kind = optString("kind"), id = optInt("id"), seriesId = optInt("sid"), title = optString("t"),
        subtitle = optString("st"), image = optString("img"), pos = optLong("pos"), dur = optLong("dur"),
        updated = optLong("up"), season = optInt("s"), ep = optInt("e"),
    ).let { it.copy(key = "${it.kind}:${it.id}") }
}
