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

    private val _owner = MutableStateFlow(false)
    val owner: StateFlow<Boolean> get() = _owner

    /** Trabajo que no debe cancelarse si la pantalla cambia (p. ej. al pasar a "Revisando…"). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        if (Cloud.hasHousehold) {
            when (Cloud.isOwner()) {
                true -> { ok(true); return }
                null -> if (ownerCached && System.currentTimeMillis() - okAt < 30L * 24 * 3600_000) { ok(true); return }
                false -> { ownerCached = false; _owner.value = false }
            }
        }
        val c = code
        if (c.isBlank()) { _state.value = State.Need(""); return }
        when (Cloud.licCheck(c)) {
            "ok" -> { ok(false); info.value = Cloud.licInfo(c) }
            "expired" -> _state.value = State.Need("Tu periodo de CineCasa terminó. Pide a quien te la vendió que la renueve; tu código sigue siendo el mismo.")
            "revoked" -> _state.value = State.Need("Este código fue desactivado. Pide uno nuevo a quien te compartió CineCasa.")
            "removed" -> {
                // Se reiniciaron los aparatos de este código: intenta volver a activarse
                if (Cloud.licActivate(c) == "ok") ok(false)
                else _state.value = State.Need("Este aparato ya no está activado con el código $c.")
            }
            "unknown" -> _state.value = State.Need("El código $c no existe.")
            null -> if (recent) _state.value = State.Ok else _state.value = State.Need("Sin conexión para revisar tu activación. Conéctate a internet e inténtalo de nuevo.")
            else -> _state.value = State.Need("No se pudo revisar la activación.")
        }
    }

    /** Activa este aparato con un código. Devuelve un mensaje de error o null si funcionó. */
    suspend fun activate(input: String): String? {
        val c = input.trim().uppercase().replace(" ", "").let { if (it.startsWith("CC") && !it.startsWith("CC-") && it.length == 10) "CC-" + it.drop(2).take(4) + "-" + it.drop(6) else it }
        return when (Cloud.licActivate(c)) {
            "ok" -> { code = c; ok(false); info.value = Cloud.licInfo(c); null }
            "expired" -> "Ese código ya venció. Pide que lo renueven."
            "full" -> "Ese código ya se usó en todos los aparatos que permite."
            "revoked" -> "Ese código está desactivado."
            "unknown" -> "Ese código no existe. Revísalo letra por letra."
            null -> "Sin conexión. Revisa tu internet."
            else -> "No se pudo activar."
        }
    }
}
