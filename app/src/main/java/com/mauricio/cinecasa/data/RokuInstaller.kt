package com.mauricio.cinecasa.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Instala CineCasa en una tele Roku desde el celular (misma red WiFi):
 * busca la tele, abre su modo desarrollador con el "control remoto" de red de Roku
 * y sube el canal con la contraseña que el cliente puso en la tele.
 */
object RokuInstaller {
    const val ZIP_URL = "https://msantos4321-bit.github.io/reproductor-tv/roku/CineCasa-roku.zip"
    data class Tv(val ip: String, val name: String)

    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS).build()

    /** Busca teles Roku en la red con SSDP. */
    suspend fun discover(ctx: Context): List<Tv> = withContext(Dispatchers.IO) {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("cinecasa-roku").apply { setReferenceCounted(false); acquire() }
        val ips = linkedSetOf<String>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 1200
                val msg = "M-SEARCH * HTTP/1.1\r\nHost: 239.255.255.250:1900\r\nMan: \"ssdp:discover\"\r\nST: roku:ecp\r\nMX: 2\r\n\r\n".toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(3) { s.send(DatagramPacket(msg, msg.size, group, 1900)); Thread.sleep(250) }
                val buf = ByteArray(2048)
                val until = System.currentTimeMillis() + 4000
                while (System.currentTimeMillis() < until) {
                    try {
                        val p = DatagramPacket(buf, buf.size); s.receive(p)
                        Regex("(?im)^LOCATION:\\s*http://([0-9.]+):8060").find(String(p.data, 0, p.length))?.let { ips += it.groupValues[1] }
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) {
        } finally { runCatching { lock.release() } }
        ips.map { ip -> Tv(ip, name(ip)) }
    }

    private fun name(ip: String): String = try {
        val x = http.newCall(Request.Builder().url("http://$ip:8060/query/device-info").build()).execute().use { it.body?.string().orEmpty() }
        listOf("user-device-name", "friendly-device-name", "model-name").firstNotNullOfOrNull { t ->
            Regex("<$t>([^<]+)</$t>").find(x)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        } ?: "Roku"
    } catch (_: Exception) { "Roku" }

    /** ¿La tele ya tiene el modo desarrollador (servidor web en el puerto 80)? */
    suspend fun devModeOn(ip: String): Boolean = withContext(Dispatchers.IO) {
        try { http.newCall(Request.Builder().url("http://$ip/").build()).execute().use { true } } catch (_: Exception) { false }
    }

    /** Manda una tecla del control por la red. Devuelve el código HTTP (403 = la tele no deja que el celular la controle). */
    private fun key(ip: String, k: String, pause: Long = 450): Int {
        val c = runCatching { http.newCall(Request.Builder().url("http://$ip:8060/keypress/$k").post(ByteArray(0).toRequestBody()).build()).execute().use { it.code } }.getOrDefault(0)
        Thread.sleep(pause)
        return c
    }

    /**
     * Abre en la tele la pantalla para activar el modo desarrollador.
     * Devuelve "ok", "blocked" (la tele no acepta control desde el celular) o "offline".
     */
    suspend fun openDevScreen(ip: String): String = withContext(Dispatchers.IO) {
        when (key(ip, "Home")) { 403 -> return@withContext "blocked"; 0 -> return@withContext "offline" }
        Thread.sleep(1500)
        listOf("Home", "Home", "Home", "Up", "Up", "Right", "Left", "Right", "Left", "Right").forEach { key(ip, it) }
        "ok"
    }

    /** Escribe un texto en el teclado que tenga abierto la tele (p. ej. la contraseña del permiso). */
    suspend fun typeText(ip: String, text: String) = withContext(Dispatchers.IO) {
        text.forEach { ch -> key(ip, "Lit_" + java.net.URLEncoder.encode(ch.toString(), "UTF-8"), 250) }
    }

    /** Espera a que la tele vuelva con el permiso activado (después de reiniciarse). */
    suspend fun waitDevMode(ip: String, seconds: Int = 180): Boolean {
        repeat(seconds / 3) {
            if (devModeOn(ip)) return true
            kotlinx.coroutines.delay(3000)
        }
        return false
    }

    /**
     * Abre CineCasa en la tele y le pasa los datos de este celular (código, proveedor y cuenta),
     * para que la tele entre sola sin escribir nada con el control.
     */
    suspend fun launchWith(ip: String, params: Map<String, String>) = withContext(Dispatchers.IO) {
        key(ip, "Home", 2500)
        val q = params.filterValues { it.isNotBlank() }.entries.joinToString("&") { (k, v) -> k + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
        runCatching { http.newCall(Request.Builder().url("http://$ip:8060/launch/dev" + (if (q.isEmpty()) "" else "?$q")).post(ByteArray(0).toRequestBody()).build()).execute().close() }
    }

    /** Descarga la versión más reciente y la instala. Devuelve "ok", "password" o un mensaje de error. */
    suspend fun install(ip: String, password: String): String = withContext(Dispatchers.IO) {
        val zip = try {
            http.newCall(Request.Builder().url(ZIP_URL).build()).execute().use { r -> if (!r.isSuccessful) throw Exception(); r.body!!.bytes() }
        } catch (_: Exception) { return@withContext "No se pudo descargar CineCasa para Roku. Revisa tu internet." }
        fun body(): RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("mysubmit", "Install")
            .addFormDataPart("archive", "CineCasa-roku.zip", zip.toRequestBody("application/zip".toMediaType()))
            .build()
        val url = "http://$ip/plugin_install"
        try {
            // 1) primer intento sin clave para recibir el "reto" de la tele (autenticación Digest)
            val challenge = http.newCall(Request.Builder().url(url).post(body()).build()).execute().use { r ->
                if (r.code != 401) return@withContext if (r.isSuccessful) "ok" else "La tele respondió ${r.code}."
                r.header("WWW-Authenticate").orEmpty()
            }
            val realm = Regex("realm=\"([^\"]*)\"").find(challenge)?.groupValues?.get(1).orEmpty()
            val nonce = Regex("nonce=\"([^\"]*)\"").find(challenge)?.groupValues?.get(1).orEmpty()
            val qop = Regex("qop=\"?([^\",]*)").find(challenge)?.groupValues?.get(1)
            fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
            val ha1 = md5("rokudev:$realm:$password")
            val ha2 = md5("POST:/plugin_install")
            val cnonce = md5(System.nanoTime().toString()).take(16)
            val resp = if (qop != null) md5("$ha1:$nonce:00000001:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
            val auth = buildString {
                append("Digest username=\"rokudev\", realm=\"$realm\", nonce=\"$nonce\", uri=\"/plugin_install\", response=\"$resp\"")
                if (qop != null) append(", qop=$qop, nc=00000001, cnonce=\"$cnonce\"")
            }
            http.newCall(Request.Builder().url(url).header("Authorization", auth).post(body()).build()).execute().use { r ->
                val t = r.body?.string().orEmpty()
                when {
                    r.code == 401 -> "password"
                    t.contains("Install Success") || t.contains("Identical") || t.contains("Received") || r.isSuccessful -> {
                        runCatching { http.newCall(Request.Builder().url("http://$ip:8060/launch/dev").post(ByteArray(0).toRequestBody()).build()).execute().close() }
                        "ok"
                    }
                    else -> "La tele no aceptó la instalación (${r.code})."
                }
            }
        } catch (_: Exception) { "No hay conexión con la tele. Revisa que esté encendida y en el mismo WiFi." }
    }
}
