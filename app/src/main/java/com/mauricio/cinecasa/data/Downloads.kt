package com.mauricio.cinecasa.data

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Descargas para ver sin internet (pensado para celular y tableta; también funciona en la tele si hay espacio).
 * Usa el administrador de descargas de Android: sigue bajando aunque cierres la app y avisa al terminar.
 * Los archivos quedan dentro de la carpeta privada de la app; se borran al desinstalarla.
 */
object Downloads {
    data class Dl(
        val key: String,          // "movie:123" | "episode:456" (igual que el progreso)
        val kind: String,
        val id: Int,
        val seriesId: Int,
        val title: String,
        val subtitle: String,
        val image: String,
        val season: Int,
        val ep: Int,
        val dmId: Long,
        val path: String,
        val status: Int = DownloadManager.STATUS_PENDING,
        val done: Long = 0,
        val total: Long = 0,
        val added: Long = System.currentTimeMillis(),
    ) {
        val finished get() = status == DownloadManager.STATUS_SUCCESSFUL
        val failed get() = status == DownloadManager.STATUS_FAILED
        val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private lateinit var appCtx: Context
    private lateinit var sp: SharedPreferences
    private val _list = MutableStateFlow<List<Dl>>(emptyList())
    val list: StateFlow<List<Dl>> get() = _list

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        sp = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        _list.value = try {
            val a = JSONArray(sp.getString("list", "[]"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.toDl() }
        } catch (_: Exception) { emptyList() }
    }

    private fun dm() = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun dir(): File = File(appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "cinecasa").apply { mkdirs() }

    fun freeBytes(): Long = try { StatFs(dir().path).availableBytes } catch (_: Exception) { 0L }

    fun get(key: String) = _list.value.firstOrNull { it.key == key }
    fun isDownloaded(key: String) = get(key)?.finished == true

    /** Ruta local para reproducir sin internet (o null si no está completa). */
    fun localUri(key: String): String? = get(key)?.takeIf { it.finished && File(it.path).exists() }?.let { Uri.fromFile(File(it.path)).toString() }

    fun start(item: PlayItem): Boolean {
        if (get(item.key)?.let { !it.failed } == true) return true
        return try {
            val ext = item.url.substringAfterLast('.', "mp4").take(4).ifBlank { "mp4" }
            val f = File(dir(), item.key.replace(':', '_') + "." + ext)
            if (f.exists()) f.delete()
            val req = DownloadManager.Request(Uri.parse(item.url))
                .addRequestHeader("User-Agent", Net.UA)
                .setTitle(item.title + if (item.subtitle.isNotBlank()) " · " + item.subtitle else "")
                .setDescription("CineCasa")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_MOVIES, "cinecasa/" + f.name)
            val id = dm().enqueue(req)
            val d = Dl(item.key, item.kind, item.id, item.seriesId, item.title, item.subtitle, item.image, item.season, item.ep, id, f.path)
            _list.value = _list.value.filterNot { it.key == item.key } + d
            persist()
            true
        } catch (e: Exception) { false }
    }

    fun remove(key: String) {
        val d = get(key) ?: return
        try { dm().remove(d.dmId) } catch (_: Exception) {}
        try { File(d.path).delete() } catch (_: Exception) {}
        _list.value = _list.value.filterNot { it.key == key }
        persist()
    }

    /** Actualiza avance y estado consultando al administrador de descargas. */
    fun refresh() {
        val cur = _list.value
        if (cur.isEmpty()) return
        val pending = cur.filter { !it.finished }
        if (pending.isEmpty()) return
        val byId = HashMap<Long, Triple<Int, Long, Long>>()
        try {
            dm().query(DownloadManager.Query().setFilterById(*pending.map { it.dmId }.toLongArray()))?.use { c ->
                val iId = c.getColumnIndex(DownloadManager.COLUMN_ID)
                val iSt = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val iDone = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val iTot = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) byId[c.getLong(iId)] = Triple(c.getInt(iSt), c.getLong(iDone), c.getLong(iTot))
            }
        } catch (_: Exception) { return }
        var changed = false
        val next = cur.map { d ->
            if (d.finished) d else {
                val t = byId[d.dmId]
                val upd = if (t == null) d.copy(status = DownloadManager.STATUS_FAILED) else d.copy(status = t.first, done = t.second, total = t.third)
                if (upd != d) changed = true
                upd
            }
        }
        if (changed) { _list.value = next; persist() }
    }

    private fun persist() {
        val a = JSONArray(); _list.value.forEach { a.put(it.toJson()) }
        sp.edit().putString("list", a.toString()).apply()
    }

    private fun Dl.toJson() = JSONObject().put("key", key).put("kind", kind).put("id", id).put("sid", seriesId).put("t", title)
        .put("st", subtitle).put("img", image).put("s", season).put("e", ep).put("dm", dmId).put("path", path)
        .put("status", status).put("done", done).put("total", total).put("added", added)

    private fun JSONObject.toDl() = Dl(optString("key"), optString("kind"), optInt("id"), optInt("sid"), optString("t"), optString("st"),
        optString("img"), optInt("s"), optInt("e"), optLong("dm"), optString("path"), optInt("status"), optLong("done"), optLong("total"), optLong("added"))
}
