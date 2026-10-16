package com.mauricio.cinecasa.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/**
 * TMDB (themoviedb.org) es opcional: con una clave gratuita permite buscar por actor
 * aunque el proveedor no etiquete el reparto de cada película.
 */
object Tmdb {
    data class Person(val id: Int, val name: String, val photo: String, val dept: String)
    data class Credit(val mediaType: String, val tmdbId: String, val title: String, val original: String, val year: Int)

    private fun url(path: String, vararg q: Pair<String, String>): String {
        val b = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", Prefs.tmdbKey)
            .addQueryParameter("language", "es-MX")
        q.forEach { b.addQueryParameter(it.first, it.second) }
        return b.build().toString()
    }

    val enabled: Boolean get() = Prefs.tmdbKey.length >= 16

    fun test(key: String): Boolean = try {
        val u = "https://api.themoviedb.org/3/configuration".toHttpUrl().newBuilder().addQueryParameter("api_key", key).build().toString()
        Net.get(u, Net.quick).contains("images")
    } catch (e: Exception) { false }

    fun searchPeople(q: String): List<Person> {
        val root = JSONObject(Net.get(url("/search/person", "query" to q, "include_adult" to "false"), Net.quick))
        val arr = root.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            Person(
                it.optInt("id"), it.optString("name"),
                it.optString("profile_path").takeIf { p -> p.isNotBlank() && p != "null" }?.let { p -> "https://image.tmdb.org/t/p/w185$p" } ?: "",
                it.optString("known_for_department"),
            )
        }
    }

    fun credits(personId: Int): List<Credit> {
        val root = JSONObject(Net.get(url("/person/$personId/combined_credits"), Net.quick))
        val out = ArrayList<Credit>()
        for (key in listOf("cast", "crew")) {
            val arr = root.optJSONArray(key) ?: continue
            for (n in 0 until arr.length()) {
                val o = arr.optJSONObject(n) ?: continue
                if (key == "crew" && o.optString("job") != "Director") continue
                val type = o.optString("media_type")
                val title = if (type == "tv") o.optString("name") else o.optString("title")
                val orig = if (type == "tv") o.optString("original_name") else o.optString("original_title")
                val date = if (type == "tv") o.optString("first_air_date") else o.optString("release_date")
                out.add(Credit(type, o.optInt("id").toString(), title, orig, TextUtil.yearOf(date)))
            }
        }
        return out
    }

    /** Busca el tráiler (YouTube) por id de TMDB; primero en español. */
    fun trailer(tmdbId: String, tv: Boolean): String {
        if (!enabled || tmdbId.isBlank() || tmdbId == "0") return ""
        val path = (if (tv) "/tv/" else "/movie/") + tmdbId + "/videos"
        for (lang in listOf("es-MX", "es-ES", "en-US")) {
            try {
                val u = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", Prefs.tmdbKey).addQueryParameter("language", lang).build().toString()
                val arr = JSONObject(Net.get(u, Net.quick)).optJSONArray("results") ?: continue
                val vids = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optString("site") == "YouTube" }
                val pick = vids.firstOrNull { it.optString("type") == "Trailer" } ?: vids.firstOrNull { it.optString("type") == "Teaser" }
                if (pick != null) return pick.optString("key")
            } catch (_: Exception) {}
        }
        return ""
    }
}
