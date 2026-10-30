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

    /**
     * vendor = la liga del servidor la puso el vendedor en el Centro de control
     * (si él la cambia, la app la sigue sola). Si el cliente escribió su propia liga, no se toca.
     */
    fun setAccount(a: Account?, vendor: Boolean = false) {
        sp.edit().apply {
            if (a == null) { remove("server"); remove("user"); remove("pass"); remove("vendorSrv") }
            else { putString("server", a.server); putString("user", a.user); putString("pass", a.pass); putString("ua", Net.UA); putBoolean("vendorSrv", vendor) }
        }.apply()
        _account.value = a
    }

    /** La cuenta usa el servidor que puso el vendedor. */
    val vendorServer: Boolean get() = sp.getBoolean("vendorSrv", false)

    const val DEMO_SERVER = "https://wnwecaapchcfhcljsvli.supabase.co/functions/v1/demo"

    /** True mientras este aparato usa el catálogo de demostración (se decide por la cuenta activa). */
    val inDemo: Boolean get() = _account.value?.server == DEMO_SERVER

    /** Guarda la cuenta real aparte y pone la de demostración (para grabar videos sin perder tus datos). */
    fun enterDemo() {
        val cur = _account.value
        if (cur == null || cur.server == DEMO_SERVER) return // ya está en demostración: no tocar el respaldo
        sp.edit().putString("bk_server", cur.server).putString("bk_user", cur.user)
            .putString("bk_pass", cur.pass).putString("bk_formats", sp.getString("formats", ""))
            .putBoolean("bk_vendor", vendorServer).apply()
        serverFormats = listOf("m3u8")
        setAccount(Account(DEMO_SERVER, "demo", "demo"))
    }

    /**
     * Regresa a la cuenta real guardada. Devuelve false si no había respaldo válido: en ese caso
     * cierra la demostración y la app pide entrar (con «Tengo CineCasa en otro aparato» se recupera sin escribir nada).
     */
    fun exitDemo(): Boolean {
        val s = sp.getString("bk_server", null); val u = sp.getString("bk_user", null); val p = sp.getString("bk_pass", null)
        val f = sp.getString("bk_formats", "")
        val v = sp.getBoolean("bk_vendor", false)
        sp.edit().remove("bk_server").remove("bk_user").remove("bk_pass").remove("bk_formats").remove("bk_vendor").putString("formats", f).apply()
        val ok = s != null && u != null && p != null && s != DEMO_SERVER
        setAccount(if (ok) Account(s!!, u!!, p!!) else null, vendor = ok && v)
        return ok
    }

    var tmdbKey: String
        get() = sp.getString("tmdb", "") ?: ""
        set(v) = sp.edit().putString("tmdb", v.trim()).apply()

    var showAdult: Boolean
        get() = sp.getBoolean("adult", false)
        set(v) = sp.edit().putBoolean("adult", v).apply()

    /** Al abrir la app: true = entra directo al último canal en vivo. */
    var startOnLive: Boolean
        get() = sp.getBoolean("startOnLive", false)
        set(v) = sp.edit().putBoolean("startOnLive", v).apply()

    /** Tema: "light" | "dark" | "auto" */
    var themeMode: String
        get() = sp.getString("theme", "dark") ?: "dark"
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
