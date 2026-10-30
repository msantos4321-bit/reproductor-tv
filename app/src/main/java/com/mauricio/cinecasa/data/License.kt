package com.mauricio.cinecasa.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Códigos de activación.
 * - Los aparatos del hogar dueño (y los que se emparejan con él) siempre están activados.
 * - Los demás necesitan un código que genera el dueño; se puede desactivar en cualquier momento.
 * - Sin internet la app sigue funcionando 7 días desde la última revisión.
 */
object License {
    sealed interface State {
        data object Checking : State
        data object Ok : State
        data class Need(val msg: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> get() = _state

    /** Datos de la activación de este aparato (tipo, vencimiento, vendedor). */
    val info = MutableStateFlow<org.json.JSONObject?>(null)

    /** Servidor (liga) que puso el vendedor en el Centro de control. */
    data class Server(val id: String, val name: String, val url: String, val isDefault: Boolean)

    /** El servidor que le toca a este aparato y los demás del mismo vendedor (para elegir otro). */
    val server = MutableStateFlow<Server?>(null)
    val servers = MutableStateFlow<List<Server>>(emptyList())

    /** true mientras se reproduce algo (solo para mostrar «en uso ahora» en el panel; no se dice qué). */
    @Volatile var playing = false

    private fun parseServer(o: org.json.JSONObject?): Server? {
        val url = o?.optString("url").orEmpty()
        if (o == null || url.isBlank() || url == "null") return null
        return Server(o.optString("id"), o.optString("name").ifBlank { "Servidor" }, url, o.optBoolean("default"))
    }

    /** Guarda el servidor del vendedor (también en el aparato, para usarlo sin internet). */
    private fun takeServers(j: org.json.JSONObject?) {
        if (j == null || !j.has("server")) return
        val s = parseServer(j.optJSONObject("server"))
        val arr = j.optJSONArray("servers")
        val list = if (arr == null) emptyList() else List(arr.length()) { parseServer(arr.optJSONObject(it)) }.filterNotNull()
        server.value = s; servers.value = list
        Cloud.prefs.edit().putString("licServer", j.toString()).apply()
    }

    private fun loadSavedServers() {
        if (server.value != null) return
        runCatching { Cloud.prefs.getString("licServer", null)?.let { takeServersLocal(org.json.JSONObject(it)) } }
    }
    private fun takeServersLocal(j: org.json.JSONObject) {
        server.value = parseServer(j.optJSONObject("server"))
        val arr = j.optJSONArray("servers")
        servers.value = if (arr == null) emptyList() else List(arr.length()) { parseServer(arr.optJSONObject(it)) }.filterNotNull()
    }

    /** Pregunta el servidor del vendedor (para la pantalla de entrar). */
    suspend fun loadServer() {
        loadSavedServers()
        val j = if (_owner.value) Cloud.ownerServer() else if (code.isNotBlank()) Cloud.licServer(code) else null
        takeServers(j)
    }

    /**
     * Si la cuenta usa el servidor del vendedor y él cambió la liga en el Centro de control,
     * prueba la nueva y, si responde, la usa sin que el cliente haga nada.
     */
    @Volatile private var following = false
    private suspend fun followServer() {
        if (following) return
        following = true
        try { followServerNow() } finally { following = false }
    }
    private suspend fun followServerNow() {
        val s = server.value ?: return
        val acc = Prefs.account.value ?: return
        if (!Prefs.vendorServer || Prefs.inDemo) return
        val bare = { u: String -> Account.normalize(u).substringAfter("://") }
        if (bare(s.url) == bare(acc.base)) return
        val r = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { Xtream.Login.login(acc.copy(server = s.url)) } }
        r.onSuccess { (ok, info) ->
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (Prefs.account.value?.user != acc.user) return@withContext
                Prefs.serverFormats = info.formats
                Prefs.setAccount(ok, vendor = true)
                Repo.start(force = true)
            }
        }
    }

    /** Este aparato fue quitado de la cuenta: deja de funcionar y se borran aquí los datos de la cuenta. */
    private suspend fun wipeRemoved() {
        code = ""; info.value = null; server.value = null; servers.value = emptyList()
        Cloud.prefs.edit().remove("licServer").remove("licOkAt").apply()
        kotlinx.coroutines.withContext(Dispatchers.Main) { if (Prefs.account.value != null && !Prefs.inDemo) Repo.logout() }
    }

    private val _owner = MutableStateFlow(false)
    val owner: StateFlow<Boolean> get() = _owner

    /** Trabajo que no debe cancelarse si la pantalla cambia (p. ej. al pasar a "Revisando…"). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.w("CineCasa", "error en segundo plano", e) })

    /** Revisa la licencia fuera de cualquier pantalla, con límite de tiempo. */
    fun refresh(quiet: Boolean = false) {
        scope.launch {
            val done = withTimeoutOrNull(25_000) { verify(quiet); true }
            if (done == null && _state.value is State.Checking) {
                _state.value = if (System.currentTimeMillis() - okAt < GRACE) State.Ok
                else State.Need("La revisión tardó demasiado. Revisa tu internet e inténtalo de nuevo.")
            }
        }
    }

    private const val GRACE = 7L * 24 * 3600_000

    var code: String
        get() = Cloud.prefs.getString("licCode", "") ?: ""
        set(v) = Cloud.prefs.edit().putString("licCode", v.trim().uppercase()).apply()
    private var okAt: Long
        get() = Cloud.prefs.getLong("licOkAt", 0L)
        set(v) = Cloud.prefs.edit().putLong("licOkAt", v).apply()
    private var ownerCached: Boolean
        get() = Cloud.prefs.getBoolean("licOwner", false)
        set(v) = Cloud.prefs.edit().putBoolean("licOwner", v).apply()

    private fun ok(owner: Boolean) { okAt = System.currentTimeMillis(); ownerCached = owner; _owner.value = owner; _state.value = State.Ok }

    /** Revisa la licencia. quiet = no muestra "Revisando…" (revisión periódica en segundo plano). */
    suspend fun verify(quiet: Boolean = false) {
        if (!quiet && _state.value !is State.Ok) _state.value = State.Checking
        val recent = System.currentTimeMillis() - okAt < GRACE
        loadSavedServers()
        if (Cloud.hasHousehold) {
            when (Cloud.isOwner()) {
                true -> { ok(true); takeServers(Cloud.ownerServer()); scope.launch { followServer() }; return }
                null -> if (ownerCached && System.currentTimeMillis() - okAt < 30L * 24 * 3600_000) { ok(true); return }
                false -> { ownerCached = false; _owner.value = false }
            }
        }
        val c = code
        if (c.isBlank()) { _state.value = State.Need(""); return }
        val ping = Cloud.licPing(c, playing)
        when (ping?.optString("status")) {
            "ok" -> {
                ok(false); takeServers(ping); scope.launch { followServer() }
                if (!quiet || info.value == null) info.value = Cloud.licInfo(c)
            }
            "blocked" -> {
                wipeRemoved()
                _state.value = State.Need("Este aparato se quitó de la cuenta $c desde otro aparato o por tu vendedor. Si fue un error, pide que lo permitan otra vez.")
            }
            "expired" -> _state.value = State.Need("Tu periodo de CineCasa terminó. Pide a quien te la vendió que la renueve; tu código sigue siendo el mismo.")
            "revoked" -> _state.value = State.Need("Este código fue desactivado. Pide uno nuevo a quien te compartió CineCasa.")
            "removed" -> {
                // Se reiniciaron los aparatos de este código: intenta volver a activarse
                if (Cloud.licActivate(c) == "ok") ok(false)
                else _state.value = State.Need("Este aparato ya no está activado con el código $c.")
            }
            "unknown" -> _state.value = State.Need("El código $c no existe.")
            null -> if (recent) _state.value = State.Ok else _state.value = State.Need("Sin conexión para revisar tu activación. Conéctate a internet e inténtalo de nuevo.")
            else -> if (recent) _state.value = State.Ok else _state.value = State.Need("No se pudo revisar la activación.")
        }
    }

    /** Pide la prueba gratis automática. Devuelve un mensaje de error o null si funcionó. */
    suspend fun startTrial(ref: String): String? {
        val r = Cloud.trialAuto(ref) ?: return "Sin conexión. Revisa tu internet."
        return when (r.optString("error")) {
            "" -> when (r.optString("result")) {
                "ok" -> { code = r.optString("code"); ok(false); info.value = Cloud.licInfo(code); loadServer(); null }
                "trial_used" -> "Este aparato ya usó su prueba gratis. Para seguir, pide tu plan anual."
                else -> "No se pudo activar la prueba."
            }
            "trial_used" -> "Este aparato ya usó su prueba gratis. Para seguir, pide tu plan anual."
            "bad_ref" -> "Ese código de recomendación no existe. Revísalo o déjalo vacío."
            "off" -> "Las pruebas gratis no están disponibles ahora. Pide tu código a quien te compartió la app."
            "busy" -> "Hay muchas solicitudes en este momento. Intenta en unos minutos."
            else -> "Este aparato no se pudo identificar para la prueba."
        }
    }

    /** Activa este aparato con un código. Devuelve un mensaje de error o null si funcionó. */
    suspend fun activate(input: String): String? {
        val c = input.trim().uppercase().replace(" ", "").let { if (it.startsWith("CC") && !it.startsWith("CC-") && it.length == 10) "CC-" + it.drop(2).take(4) + "-" + it.drop(6) else it }
        return when (Cloud.licActivate(c)) {
            "ok" -> { code = c; ok(false); info.value = Cloud.licInfo(c); loadServer(); null }
            "expired" -> "Ese código ya venció. Pide que lo renueven."
            "trial_used" -> "Este aparato ya usó su prueba gratis. Pide un código anual a quien te la compartió."
            "full" -> "Ese código ya se usó en todos los aparatos que permite. Desde otro aparato de la cuenta puedes quitar uno en Ajustes → Tu suscripción → Mis aparatos."
            "blocked" -> "Este aparato fue quitado de ese código. Pide a quien te lo vendió que lo permita otra vez."
            "unknown" -> "Ese código no existe. Revísalo letra por letra."
            null -> "Sin conexión. Revisa tu internet."
            else -> "No se pudo activar."
        }
    }
}
