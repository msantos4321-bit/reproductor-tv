package com.mauricio.cinecasa.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Profile(
    val id: String,
    val name: String,
    val color: Int = 0,
    val pinHash: String = "",
    val kids: Boolean = false,
    val admin: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val lang: String = "",
    val theme: String = "",
    /** Privado: no aparece en la lista; se entra escribiendo su PIN. No deja rastro en el aparato. */
    val private: Boolean = false,
    /** Invitado: no guarda nada. */
    val guest: Boolean = false,
) {
    val hasPin get() = pinHash.isNotBlank()
    val initial get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    fun toJson(): JSONObject = JSONObject().put("name", name).put("color", color).put("pin", pinHash).put("kids", kids)
        .put("admin", admin).put("created", created).put("lang", lang).put("theme", theme)

    companion object {
        fun from(id: String, o: JSONObject, private: Boolean = false) = Profile(
            id = id, name = o.optString("name"), color = o.optInt("color"), pinHash = o.optString("pin"),
            kids = o.optBoolean("kids"), admin = o.optBoolean("admin"), created = o.optLong("created"),
            lang = o.optString("lang"), theme = o.optString("theme"), private = private,
        )
    }
}

/**
 * Perfiles del hogar ("¿Quién está viendo?").
 * - Los normales se sincronizan entre aparatos.
 * - "Invitado" siempre está y no guarda nada.
 * - Los privados no aparecen en ninguna lista: su identificador sale del PIN, así que nadie sabe que existen.
 * - Nunca se registra qué perfil se está usando ni en qué aparato.
 */
object Profiles {
    private lateinit var sp: SharedPreferences

    val GUEST = Profile("guest", "Invitado", color = 6, guest = true)

    private val _list = MutableStateFlow<List<Profile>>(emptyList())
    val list: StateFlow<List<Profile>> get() = _list

    private val _current = MutableStateFlow<Profile?>(null)
    val current: StateFlow<Profile?> get() = _current

    /** Colores de los avatares (tonos sobrios). */
    val colors = listOf(0xFF0A84FF, 0xFF30B0C7, 0xFF34C759, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2, 0xFF8E8E93, 0xFFA2845E)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)
        _list.value = try {
            val o = JSONObject(sp.getString("list", "{}") ?: "{}")
            o.keys().asSequence().map { Profile.from(it, o.getJSONObject(it)) }.sortedBy { it.created }.toList()
        } catch (_: Exception) { emptyList() }
    }

    private fun persist() {
        val o = JSONObject(); _list.value.forEach { o.put(it.id, it.toJson()) }
        sp.edit().putString("list", o.toString()).apply()
    }

    /** Perfil que este aparato abre solo (vacío = preguntar siempre). Nunca guarda el privado ni el invitado. */
    var deviceDefault: String
        get() = sp.getString("deviceDefault", "") ?: ""
        set(v) = sp.edit().putString("deviceDefault", v).apply()

    fun applyRemote(id: String, d: JSONObject?, deleted: Boolean) {
        val rest = _list.value.filterNot { it.id == id }
        _list.value = if (deleted || d == null) rest else (rest + Profile.from(id, d)).sortedBy { it.created }
        persist()
        // Si borraron o cambiaron el perfil activo
        val cur = _current.value
        if (cur != null && !cur.private && !cur.guest && cur.id == id) {
            _current.value = if (deleted) null else _list.value.firstOrNull { it.id == id }
        }
        if (deleted && deviceDefault == id) deviceDefault = ""
    }

    fun save(p: Profile) {
        if (p.guest) return
        if (p.private) {
            Cloud.enqueue(p.id, "meta", "meta", p.toJson())
            if (_current.value?.id == p.id) _current.value = p
            return
        }
        _list.value = (_list.value.filterNot { it.id == p.id } + p).sortedBy { it.created }
        persist()
        Cloud.enqueue("_", "profile", p.id, p.toJson())
        if (_current.value?.id == p.id) _current.value = p
    }

    fun create(name: String, color: Int, kids: Boolean = false, admin: Boolean = false): Profile {
        val p = Profile(UUID.randomUUID().toString().take(8), name.trim().ifBlank { "Perfil" }, color, kids = kids, admin = admin)
        save(p)
        return p
    }

    suspend fun delete(p: Profile) {
        if (p.guest) return
        if (!p.private) {
            _list.value = _list.value.filterNot { it.id == p.id }
            persist()
            Cloud.enqueue("_", "profile", p.id, null, true)
            if (deviceDefault == p.id) deviceDefault = ""
        }
        Cloud.flush()
        Cloud.wipeScope(p.id)
    }

    fun pinHash(profileId: String, pin: String) = Cloud.sha(Cloud.householdSecret + ":" + profileId + ":" + pin)
    fun checkPin(p: Profile, pin: String) = !p.hasPin || p.pinHash == pinHash(p.id, pin)
    fun withPin(p: Profile, pin: String?) = p.copy(pinHash = if (pin.isNullOrBlank()) "" else pinHash(p.id, pin))

    /** El identificador del perfil privado sale del PIN: sin el PIN no hay forma de saber que existe. */
    fun privateId(pin: String) = "x" + Cloud.sha(Cloud.householdSecret + ":privado:" + pin).take(20)

    suspend fun openPrivate(pin: String): Profile? {
        val id = privateId(pin)
        Cloud.resetSince(id)
        val items = Cloud.pull(id) ?: return null
        val meta = items.lastOrNull { it.optString("kind") == "meta" && !it.optBoolean("deleted") }?.optJSONObject("data") ?: return null
        return Profile.from(id, meta, private = true)
    }

    suspend fun createPrivate(name: String, pin: String, color: Int): Profile {
        val p = Profile(privateId(pin), name.trim().ifBlank { "Privado" }, color, private = true)
        save(p)
        Cloud.flush()
        return p
    }

    /** Activa un perfil en este aparato. */
    fun select(p: Profile) {
        _current.value = p
        Library.switchTo(p.id, persist = !p.guest && !p.private, sync = !p.guest)
        if (p.lang.isNotBlank()) LangFilter.set(p.lang)
        if (p.private) Cloud.resetSince(p.id) // siempre baja todo: no queda nada guardado en la tele
        Repo.refilter() // perfil infantil cambia lo que se ve
    }

    fun signOut() { _current.value = null }

    /** Primer arranque: crea el perfil principal (administrador) con lo que ya tenías guardado. */
    fun ensureFirst() {
        if (_list.value.isNotEmpty()) return
        val p = Profile(UUID.randomUUID().toString().take(8), "Principal", 0, admin = true)
        Library.migrateLegacy(p.id)
        save(p)
        Library.switchTo(p.id, persist = true, sync = true)
        Library.pushAll()
        _current.value = null
    }

    val anyAdmin get() = _list.value.any { it.admin }
}
