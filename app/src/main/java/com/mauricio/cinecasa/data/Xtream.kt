package com.mauricio.cinecasa.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

/** Cliente del API "Xtream Codes" (player_api.php) que usan casi todos los proveedores IPTV. */
class Xtream(private val acc: Account) {

    private fun api(action: String?, vararg params: Pair<String, String>): String {
        val b = "${acc.base}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", acc.user)
            .addQueryParameter("password", acc.pass)
        if (action != null) b.addQueryParameter("action", action)
        params.forEach { b.addQueryParameter(it.first, it.second) }
        return b.build().toString()
    }

    // ---------- URLs de reproducción ----------
    fun liveUrl(id: Int, fmt: String) = "${acc.base}/live/${acc.user}/${acc.pass}/$id.$fmt"
    fun movieUrl(id: Int, ext: String) = "${acc.base}/movie/${acc.user}/${acc.pass}/$id.${ext.ifBlank { "mp4" }}"
    fun episodeUrl(id: Int, ext: String) = "${acc.base}/series/${acc.user}/${acc.pass}/$id.${ext.ifBlank { "mp4" }}"

    // ---------- Cuenta ----------
    fun accountInfo(quick: Boolean = false): AccountInfo {
        val txt = Net.get(api(null), if (quick) Net.quick else Net.client)
        val root = JSONObject(txt)
        val ui = root.optJSONObject("user_info") ?: JSONObject()
        val auth = ui.optString("auth", "0")
        val formats = ui.optJSONArray("allowed_output_formats")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
        return AccountInfo(
            ok = auth == "1" || auth == "true",
            status = ui.optString("status", ""),
            expires = ui.optString("exp_date", "").toLongOrNull()?.times(1000),
            maxCons = ui.optString("max_connections", "0").toIntOrNull() ?: 0,
            activeCons = ui.optString("active_cons", "0").toIntOrNull() ?: 0,
            formats = formats,
            trial = ui.optString("is_trial", "0") == "1",
        )
    }

    // ---------- Listas (lectura en streaming para no agotar memoria en la tele) ----------
    private fun <T> streamList(url: String, map: (Map<String, String>) -> T?): List<T> {
        val out = ArrayList<T>()
        Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val body = r.body ?: return out
            JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { jr ->
                jr.isLenient = true
                when (jr.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        jr.beginArray()
                        while (jr.hasNext()) {
                            if (jr.peek() == JsonToken.BEGIN_OBJECT) map(jr.readFlat())?.let(out::add) else jr.skipValue()
                        }
                        jr.endArray()
                    }
                    JsonToken.BEGIN_OBJECT -> { // algunos paneles responden {"0":{...},"1":{...}}
                        jr.beginObject()
                        while (jr.hasNext()) {
                            jr.nextName()
                            if (jr.peek() == JsonToken.BEGIN_OBJECT) map(jr.readFlat())?.let(out::add) else jr.skipValue()
                        }
                        jr.endObject()
                    }
                    else -> {}
                }
            }
        }
        return out
    }

    fun categories(action: String): List<Category> = streamList(api(action)) { m ->
        val id = m["category_id"] ?: return@streamList null
        Category(id, m["category_name"].orEmpty().trim())
    }

    fun vods(): List<Vod> = streamList(api("get_vod_streams")) { m -> vodFrom(m) }
    fun shows(): List<Show> = streamList(api("get_series")) { m -> showFrom(m) }
    fun channels(): List<Channel> = streamList(api("get_live_streams")) { m -> channelFrom(m) }

    // ---------- Detalles ----------
    fun vodInfo(id: Int): VodInfo {
        val root = JSONObject(Net.get(api("get_vod_info", "vod_id" to id.toString())))
        val i = root.optJSONObject("info") ?: JSONObject()
        val secs = i.str("duration_secs").toIntOrNull() ?: parseDuration(i.str("duration"))
        return VodInfo(
            plot = i.str("plot").ifEmpty { i.str("description") },
            cast = i.str("cast").ifEmpty { i.str("actors") },
            director = i.str("director"),
            genre = i.str("genre"),
            release = i.str("releasedate").ifEmpty { i.str("release_date") },
            durationSecs = secs,
            rating = i.str("rating").toFloatOrNull() ?: 0f,
            backdrop = i.str("backdrop_path"),
            poster = i.str("movie_image").ifEmpty { i.str("cover_big") },
            trailer = TextUtil.youtubeId(i.str("youtube_trailer")),
            tmdb = i.str("tmdb_id"),
            country = i.str("country"),
        )
    }

    fun seriesInfo(id: Int): SeriesInfo {
        val root = JSONObject(Net.get(api("get_series_info", "series_id" to id.toString())))
        val i = root.optJSONObject("info") ?: JSONObject()
        val eps = HashMap<Int, MutableList<Episode>>()
        fun addEp(o: JSONObject, seasonKey: Int) {
            val inf = o.optJSONObject("info") ?: JSONObject()
            val season = o.str("season").toIntOrNull() ?: seasonKey
            val e = Episode(
                id = o.str("id").toIntOrNull() ?: return,
                season = season,
                num = o.str("episode_num").toIntOrNull() ?: 0,
                title = o.str("title"),
                ext = o.str("container_extension").ifEmpty { "mp4" },
                plot = inf.str("plot"),
                image = inf.str("movie_image"),
                durationSecs = inf.str("duration_secs").toIntOrNull() ?: parseDuration(inf.str("duration")),
            )
            eps.getOrPut(season) { mutableListOf() }.add(e)
        }
        when (val e = root.opt("episodes")) {
            is JSONObject -> e.keys().forEach { k ->
                val arr = e.optJSONArray(k) ?: return@forEach
                for (n in 0 until arr.length()) arr.optJSONObject(n)?.let { addEp(it, k.toIntOrNull() ?: 0) }
            }
            is JSONArray -> for (n in 0 until e.length()) {
                when (val x = e.opt(n)) {
                    is JSONArray -> for (m in 0 until x.length()) x.optJSONObject(m)?.let { addEp(it, n + 1) }
                    is JSONObject -> addEp(x, 0)
                }
            }
        }
        eps.values.forEach { l -> l.sortWith(compareBy({ it.num }, { it.id })) }
        return SeriesInfo(
            plot = i.str("plot"),
            cast = i.str("cast"),
            director = i.str("director"),
            genre = i.str("genre"),
            release = i.str("releaseDate").ifEmpty { i.str("release_date") },
            backdrop = i.str("backdrop_path"),
            cover = i.str("cover"),
            trailer = TextUtil.youtubeId(i.str("youtube_trailer")),
            rating = i.str("rating").toFloatOrNull() ?: 0f,
            seasons = eps.keys.sorted(),
            episodes = eps,
        )
    }

    fun shortEpg(streamId: Int, limit: Int = 4): List<EpgItem> {
        val root = JSONObject(Net.get(api("get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString()), Net.quick))
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgItem>()
        for (n in 0 until arr.length()) {
            val o = arr.optJSONObject(n) ?: continue
            val start = o.str("start_timestamp").toLongOrNull()?.times(1000) ?: parseDate(o.str("start"))
            val end = o.str("stop_timestamp").toLongOrNull()?.times(1000) ?: parseDate(o.str("end"))
            if (start <= 0 || end <= 0) continue
            out.add(EpgItem(TextUtil.maybeB64(o.str("title")).trim(), TextUtil.maybeB64(o.str("description")).trim(), start, end))
        }
        val now = System.currentTimeMillis()
        return out.filter { it.end > now }.sortedBy { it.start }
    }

    companion object {
        fun vodFrom(m: Map<String, String>): Vod? {
            val id = m.int("stream_id") ?: return null
            return Vod(
                id = id,
                name = m["name"].orEmpty().trim(),
                icon = m["stream_icon"].orEmpty(),
                rating = m["rating"]?.toFloatOrNull() ?: 0f,
                added = m["added"]?.toLongOrNull() ?: 0L,
                cat = m["category_id"].orEmpty(),
                ext = m["container_extension"].orEmpty().ifEmpty { "mp4" },
                tmdb = (m["tmdb"] ?: m["tmdb_id"]).orEmpty(),
                yearField = m["year"]?.toIntOrNull() ?: 0,
            )
        }

        fun showFrom(m: Map<String, String>): Show? {
            val id = m.int("series_id") ?: return null
            return Show(
                id = id,
                name = m["name"].orEmpty().trim(),
                cover = m["cover"].orEmpty(),
                backdrop = m["backdrop_path"].orEmpty(),
                plot = m["plot"].orEmpty(),
                cast = m["cast"].orEmpty(),
                director = m["director"].orEmpty(),
                genre = m["genre"].orEmpty(),
                release = (m["releaseDate"] ?: m["release_date"]).orEmpty(),
                rating = m["rating"]?.toFloatOrNull() ?: 0f,
                lastMod = m["last_modified"]?.toLongOrNull() ?: 0L,
                cat = m["category_id"].orEmpty(),
                trailer = TextUtil.youtubeId(m["youtube_trailer"].orEmpty()),
                tmdb = (m["tmdb"] ?: m["tmdb_id"]).orEmpty(),
            )
        }

        fun channelFrom(m: Map<String, String>): Channel? {
            val id = m.int("stream_id") ?: return null
            return Channel(
                id = id,
                num = m.int("num") ?: 0,
                name = m["name"].orEmpty().trim(),
                icon = m["stream_icon"].orEmpty(),
                epgId = m["epg_channel_id"].orEmpty(),
                cat = m["category_id"].orEmpty(),
                archiveDays = if (m["tv_archive"] == "1") (m.int("tv_archive_duration") ?: 0) else 0,
            )
        }

        private fun Map<String, String>.int(k: String): Int? = this[k]?.toDoubleOrNull()?.toInt()

        private fun parseDuration(s: String): Int {
            val p = s.split(':').mapNotNull { it.trim().toIntOrNull() }
            return when (p.size) {
                3 -> p[0] * 3600 + p[1] * 60 + p[2]
                2 -> p[0] * 60 + p[1]
                else -> 0
            }
        }

        private fun parseDate(s: String): Long = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(s)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }
}

/** Lee un objeto JSON "plano": textos y números; de un arreglo toma el primer texto. */
fun JsonReader.readFlat(): Map<String, String> {
    val m = HashMap<String, String>(24)
    beginObject()
    while (hasNext()) {
        val k = nextName()
        when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> m[k] = nextString()
            JsonToken.BOOLEAN -> m[k] = if (nextBoolean()) "1" else "0"
            JsonToken.NULL -> nextNull()
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                var first: String? = null
                while (hasNext()) {
                    if (first == null && peek() == JsonToken.STRING) first = nextString() else skipValue()
                }
                endArray()
                if (first != null) m[k] = first
            }
            else -> skipValue()
        }
    }
    endObject()
    return m
}

/** Lee un campo que puede venir como texto, número o arreglo. */
fun JSONObject.str(k: String): String = when (val v = opt(k)) {
    null, JSONObject.NULL -> ""
    is JSONArray -> if (v.length() > 0) v.optString(0, "") else ""
    is JSONObject -> ""
    else -> v.toString().let { if (it == "null") "" else it.trim() }
}
