package com.mauricio.cinecasa.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class Account(val server: String, val user: String, val pass: String) {
    /** "http://host:puerto" sin diagonal final ni rutas extra. */
    val base: String = normalize(server)

    companion object {
        fun normalize(s: String): String {
            var t = s.trim()
            if (t.isEmpty()) return t
            if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) t = "http://$t"
            val cut = listOf("/player_api.php", "/get.php", "/xmltv.php", "/c/", "/panel_api.php")
                .map { t.indexOf(it, ignoreCase = true) }.filter { it > 0 }.minOrNull()
            if (cut != null) t = t.substring(0, cut)
            return t.trimEnd('/')
        }
    }
}

object Net {
    @Volatile var UA = "IPTVSmartersPro"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val r = chain.request()
                val req = if (r.header("User-Agent") == null) r.newBuilder().header("User-Agent", UA).build() else r
                chain.proceed(req)
            }
            .build()
    }

    val quick: OkHttpClient by lazy {
        client.newBuilder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    }

    fun get(url: String, c: OkHttpClient = client): String {
        c.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> get() = _account

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("cinecasa", Context.MODE_PRIVATE)
        val s = sp.getString("server", null)
        val u = sp.getString("user", null)
        val p = sp.getString("pass", null)
        _account.value = if (s != null && u != null && p != null) Account(s, u, p) else null
        sp.getString("ua", null)?.let { Net.UA = it }
    }

    fun setAccount(a: Account?) {
        sp.edit().apply {
            if (a == null) { remove("server"); remove("user"); remove("pass") }
            else { putString("server", a.server); putString("user", a.user); putString("pass", a.pass); putString("ua", Net.UA) }
        }.apply()
        _account.value = a
    }

    var tmdbKey: String
        get() = sp.getString("tmdb", "") ?: ""
        set(v) = sp.edit().putString("tmdb", v.trim()).apply()

    var showAdult: Boolean
        get() = sp.getBoolean("adult", false)
        set(v) = sp.edit().putBoolean("adult", v).apply()

    /** Tema: "light" | "dark" | "auto" */
    var themeMode: String
        get() = sp.getString("theme", "light") ?: "light"
        set(v) = sp.edit().putString("theme", v).apply()

    /** Filtro de idioma: "all" | "es" | "es_sub" | "en" */
    var langPreset: String
        get() = sp.getString("langPreset", "all") ?: "all"
        set(v) = sp.edit().putString("langPreset", v).apply()

    /** "auto" | "ts" | "m3u8" */
    var liveFormat: String
        get() = sp.getString("liveFormat", "auto") ?: "auto"
        set(v) = sp.edit().putString("liveFormat", v).apply()

    var serverFormats: List<String>
        get() = (sp.getString("formats", "") ?: "").split(',').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("formats", v.joinToString(",")).apply()

    var lastRefresh: Long
        get() = sp.getLong("lastRefresh", 0L)
        set(v) = sp.edit().putLong("lastRefresh", v).apply()

    var recentSearches: List<String>
        get() = try {
            val a = JSONArray(sp.getString("recentSearches", "[]"))
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) { emptyList() }
        set(v) = sp.edit().putString("recentSearches", JSONArray(v.take(12)).toString()).apply()

    fun addRecentSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentSearches = listOf(t) + recentSearches.filter { !it.equals(t, true) }
    }
}
