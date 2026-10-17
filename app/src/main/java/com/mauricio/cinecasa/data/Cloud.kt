package com.mauricio.cinecasa.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Nube de CineCasa (Supabase): guarda perfiles y lo de cada perfil para que se vea igual en todos los aparatos.
 * Todo va protegido con el "secreto del hogar", que solo tienen los aparatos emparejados.
 * Si no hay internet, los cambios esperan en una cola y se suben después.
 */
object Cloud {
    private const val URL = "https://wnwecaapchcfhcljsvli.supabase.co/rest/v1/rpc/"
    private const val KEY = "sb_publishable_vnkSLd5EzN53_LBv76YxUA_QRo2u8ok"

    private lateinit var sp: SharedPreferences
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val outbox = LinkedHashMap<String, JSONObject>()
    private var flushJob: Job? = null
    private var loopJob: Job? = null

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> get() = _online

    /** Se dispara cuando el administrador quitó este aparato. */
    private val _revoked = MutableStateFlow(false)
    val revoked: StateFlow<Boolean> get() = _revoked

    internal val prefs: SharedPreferences get() = sp

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        if (deviceId.isBlank()) sp.edit().putString("deviceId", UUID.randomUUID().toString()).apply()
        try {
            val a = JSONArray(sp.getString("outbox", "[]"))
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { outbox[itemKey(it)] = it }
        } catch (_: Exception) {}
    }

    val householdId: String get() = sp.getString("hhId", "") ?: ""
    val householdSecret: String get() = sp.getString("hhSecret", "") ?: ""
    val deviceId: String get() = sp.getString("deviceId", "") ?: ""
    var deviceName: String
        get() = sp.getString("deviceName", "")?.ifBlank { null } ?: (Build.MODEL ?: "Aparato")
        set(v) { sp.edit().putString("deviceName", v.trim()).apply(); registerDevice() }
    val hasHousehold: Boolean get() = householdId.isNotBlank() && householdSecret.isNotBlank()

    fun setHousehold(id: String, secret: String) {
        sp.edit().putString("hhId", id).putString("hhSecret", secret).remove("since").apply()
    }

    fun forgetHousehold() {
        sp.edit().remove("hhId").remove("hhSecret").remove("since").remove("outbox").apply()
        synchronized(outbox) { outbox.clear() }
    }

    fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun randomSecret(): String {
        val b = ByteArray(24); SecureRandom().nextBytes(b)
        return android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    // ---------------- Llamadas ----------------
    private fun rpc(fn: String, body: JSONObject): String {
        val req = Request.Builder().url(URL + fn)
            .header("apikey", KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Net.quick.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw java.io.IOException("Nube ${r.code}: ${t.take(120)}")
            return t
        }
    }

    /** Crea el hogar la primera vez (un aparato). */
    suspend fun ensureHousehold(): Boolean = withContext(Dispatchers.IO) {
        if (hasHousehold) return@withContext true
        try {
            val s = randomSecret()
            val id = rpc("cc_create_household", JSONObject().put("p_s", s)).trim().trim('"')
            if (id.length < 30) return@withContext false
            setHousehold(id, s)
            _online.value = true
            true
        } catch (e: Exception) { _online.value = false; false }
    }

    // ---------------- Subir cambios ----------------
    private fun itemKey(o: JSONObject) = o.optString("scope") + "|" + o.optString("kind") + "|" + o.optString("key")

    fun enqueue(scope: String, kind: String, key: String, data: JSONObject?, deleted: Boolean = false) {
        val o = JSONObject().put("scope", scope).put("kind", kind).put("key", key).put("deleted", deleted)
        if (data != null) o.put("data", data)
        synchronized(outbox) {
            outbox[itemKey(o)] = o
            persistOutbox()
        }
        scheduleFlush()
    }

    private fun persistOutbox() {
        val a = JSONArray(); outbox.values.forEach { a.put(it) }
        sp.edit().putString("outbox", a.toString()).apply()
    }

    private fun scheduleFlush(delayMs: Long = 3000) {
        flushJob?.cancel()
        flushJob = io.launch { delay(delayMs); flush() }
    }

    suspend fun flush(): Boolean = mutex.withLock {
        if (!hasHousehold) return false
        val batch = synchronized(outbox) { outbox.values.toList() }
        if (batch.isEmpty()) return true
        return withContext(Dispatchers.IO) {
            try {
                val arr = JSONArray(); batch.take(300).forEach { arr.put(it) }
                rpc("cc_put", JSONObject().put("p_h", householdId).put("p_s", householdSecret).put("p_items", arr))
                synchronized(outbox) {
                    batch.take(300).forEach { sent -> if (outbox[itemKey(sent)]?.toString() == sent.toString()) outbox.remove(itemKey(sent)) }
                    persistOutbox()
                }
                _online.value = true
                true
            } catch (e: Exception) { _online.value = false; false }
        }
    }

    // ---------------- Bajar cambios ----------------
    private fun since(scope: String): String = try { JSONObject(sp.getString("since", "{}") ?: "{}").optString(scope, "1970-01-01T00:00:00Z") } catch (_: Exception) { "1970-01-01T00:00:00Z" }
    private fun setSince(scope: String, ts: String) {
        val o = try { JSONObject(sp.getString("since", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        sp.edit().putString("since", o.put(scope, ts).toString()).apply()
    }
    fun resetSince(scope: String) { setSince(scope, "1970-01-01T00:00:00Z") }

    /** Trae los cambios de un "scope" (el hogar '_' o un perfil). */
    suspend fun pull(scope: String): List<JSONObject>? = withContext(Dispatchers.IO) {
        if (!hasHousehold) return@withContext null
        try {
            val body = JSONObject().put("p_h", householdId).put("p_s", householdSecret)
                .put("p_since", since(scope)).put("p_scopes", JSONArray().put(scope))
            val a = JSONArray(rpc("cc_get", body))
            val out = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            out.lastOrNull()?.optString("updated_at")?.takeIf { it.isNotBlank() }?.let { setSince(scope, it) }
            _online.value = true
            out
        } catch (e: Exception) { _online.value = false; null }
    }

    suspend fun wipeScope(scope: String) = withContext(Dispatchers.IO) {
        try { rpc("cc_wipe_scope", JSONObject().put("p_h", householdId).put("p_s", householdSecret).put("p_scope", scope)) } catch (_: Exception) {}
    }

    /** Sincroniza: sube lo pendiente y baja lo del hogar y del perfil activo. */
    suspend fun syncNow() {
        if (!hasHousehold && !ensureHousehold()) return
        flush()
        pull("_")?.forEach { o ->
            val kind = o.optString("kind"); val key = o.optString("key"); val del = o.optBoolean("deleted")
            when (kind) {
                "profile" -> Profiles.applyRemote(key, o.optJSONObject("data"), del)
                "device" -> if (key == deviceId && del) _revoked.value = true else Devices.applyRemote(key, o.optJSONObject("data"), del)
            }
        }
        val cur = Profiles.current.value
        if (cur != null && !cur.guest) {
            pull(cur.id)?.forEach { o ->
                if (Profiles.current.value?.id == cur.id)
                    Library.applyRemote(o.optString("kind"), o.optString("key"), o.optJSONObject("data"), o.optBoolean("deleted"))
            }
        }
    }

    /** Ciclo en segundo plano mientras la app está abierta. */
    fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = io.launch {
            registerDevice()
            var n = 0
            while (true) {
                try { syncNow() } catch (_: Exception) {}
                if (n++ % 20 == 0) try { License.verify(quiet = true) } catch (_: Exception) {}
                delay(45_000)
            }
        }
    }

    fun registerDevice() {
        if (!::sp.isInitialized) return
        val d = JSONObject().put("name", deviceName).put("model", Build.MODEL ?: "").put("seen", System.currentTimeMillis())
            .put("version", com.mauricio.cinecasa.BuildConfig.VERSION_NAME)
        enqueue("_", "device", deviceId, d)
    }

    fun removeDevice(id: String) = enqueue("_", "device", id, null, true)

    // ---------------- Licencias ----------------
    /** true/false, o null si no se pudo preguntar (sin internet). */
    suspend fun isOwner(): Boolean? = withContext(Dispatchers.IO) {
        if (!hasHousehold) return@withContext false
        try { rpc("cc_is_owner", JSONObject().put("p_h", householdId).put("p_s", householdSecret)).trim() == "true" } catch (_: Exception) { null }
    }

    suspend fun licActivate(code: String): String? = withContext(Dispatchers.IO) {
        try { rpc("cc_lic_activate", JSONObject().put("p_code", code).put("p_device", deviceId).put("p_name", deviceName)).trim().trim('"') } catch (_: Exception) { null }
    }

    suspend fun licCheck(code: String): String? = withContext(Dispatchers.IO) {
        try { rpc("cc_lic_check", JSONObject().put("p_code", code).put("p_device", deviceId)).trim().trim('"') } catch (_: Exception) { null }
    }

    suspend fun licCreate(note: String, devices: Int): String? = withContext(Dispatchers.IO) {
        try { rpc("cc_lic_create", JSONObject().put("p_h", householdId).put("p_s", householdSecret).put("p_note", note).put("p_devices", devices)).trim().trim('"') } catch (_: Exception) { null }
    }

    suspend fun licList(): JSONArray? = withContext(Dispatchers.IO) {
        try { JSONArray(rpc("cc_lic_list", JSONObject().put("p_h", householdId).put("p_s", householdSecret))) } catch (_: Exception) { null }
    }

    suspend fun licSet(code: String, revoked: Boolean, reset: Boolean): Boolean = withContext(Dispatchers.IO) {
        try { rpc("cc_lic_set", JSONObject().put("p_h", householdId).put("p_s", householdSecret).put("p_code", code).put("p_revoked", revoked).put("p_reset", reset)); true } catch (_: Exception) { false }
    }

    // ---------------- Emparejar aparatos ----------------
    data class PairOffer(val code: String, val token: String)

    /** La tele nueva pide un código de 6 números. */
    suspend fun pairRequest(): PairOffer? = withContext(Dispatchers.IO) {
        repeat(4) {
            val code = (100000 + SecureRandom().nextInt(900000)).toString()
            val token = randomSecret()
            try {
                rpc("cc_pair_request", JSONObject().put("p_code", code).put("p_token", token))
                return@withContext PairOffer(code, token)
            } catch (_: Exception) {}
        }
        null
    }

    /** La tele nueva pregunta si ya la aprobaron. Devuelve los datos cuando llegan. */
    suspend fun pairPoll(o: PairOffer): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val t = rpc("cc_pair_poll", JSONObject().put("p_code", o.code).put("p_token", o.token)).trim()
            if (t == "null" || t.isBlank()) null else JSONObject(t)
        } catch (_: Exception) { null }
    }

    /** Un aparato ya configurado aprueba el código y le manda cuenta + hogar. */
    suspend fun pairApprove(code: String): Boolean = withContext(Dispatchers.IO) {
        val acc = Prefs.account.value ?: return@withContext false
        if (!hasHousehold && !ensureHousehold()) return@withContext false
        try {
            val payload = JSONObject()
                .put("server", acc.server).put("user", acc.user).put("pass", acc.pass).put("ua", Net.UA)
                .put("hh", householdId).put("hs", householdSecret).put("lic", License.code)
            rpc("cc_pair_approve", JSONObject().put("p_h", householdId).put("p_s", householdSecret)
                .put("p_code", code.trim()).put("p_payload", payload)).trim() == "true"
        } catch (_: Exception) { false }
    }
}

/** Lista de aparatos del hogar (solo la ve el administrador). */
object Devices {
    data class Device(val id: String, val name: String, val model: String, val seen: Long, val version: String)

    private val _list = MutableStateFlow<Map<String, Device>>(emptyMap())
    val list: StateFlow<Map<String, Device>> get() = _list

    fun applyRemote(key: String, d: JSONObject?, deleted: Boolean) {
        _list.value = if (deleted || d == null) _list.value - key
        else _list.value + (key to Device(key, d.optString("name"), d.optString("model"), d.optLong("seen"), d.optString("version")))
    }
}
