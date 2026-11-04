package com.mauricio.cinecasa.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * «Revisar mi servicio»: revisa paso por paso internet, CineCasa, la liga del proveedor y la cuenta,
 * y dice en palabras sencillas qué está fallando (y si es culpa del proveedor o de la app).
 */
object Diagnose {
    /** ok = true bien, false mal, null = no se pudo revisar (porque falló un paso anterior). */
    data class Step(val label: String, val ok: Boolean?, val detail: String)
    data class Result(val steps: List<Step>, val verdict: String, val allOk: Boolean) {
        /** Texto corto para mandarlo al vendedor por WhatsApp. */
        fun summary(): String = buildString {
            append("Revisión de CineCasa")
            if (License.code.isNotBlank()) append(" (código ${License.code})")
            append(", versión ${com.mauricio.cinecasa.BuildConfig.VERSION_NAME}:\n")
            steps.forEach { append(if (it.ok == true) "OK - " else if (it.ok == false) "FALLA - " else "- ").append(it.label).append(": ").append(it.detail.take(70)).append('\n') }
            append("Resultado: ").append(verdict)
        }
    }

    private fun reach(url: String): Boolean = try {
        Net.quick.newCall(Request.Builder().url(url).build()).execute().use { it.code in 200..399 }
    } catch (_: Exception) { false }

    suspend fun run(): Result = withContext(Dispatchers.IO) {
        val steps = ArrayList<Step>()
        fun done(verdict: String, ok: Boolean = false) = Result(steps.toList(), verdict, ok)

        // 1) Internet
        val net = reach("https://clients3.google.com/generate_204") || reach("https://www.cloudflare.com/cdn-cgi/trace")
        steps += Step("Internet", net, if (net) "Funciona" else "Este aparato no tiene salida a internet")
        if (!net) return@withContext done("Este aparato no tiene internet. Revisa el WiFi o el cable, o reinicia el módem.")

        // 2) CineCasa (activación)
        val owner = License.owner.value
        val code = License.code
        if (owner) steps += Step("Activación", true, "Aparato del dueño")
        else if (code.isBlank()) steps += Step("Activación", false, "Este aparato no tiene código")
        else {
            val st = Cloud.licCheck(code)
            val info = Cloud.licInfo(code)
            val exp = info?.optString("expires")?.takeIf { it.length >= 10 && it != "null" }?.take(10)
            steps += when (st) {
                "ok" -> Step("Activación", true, "Código $code activo" + (exp?.let { " hasta $it" } ?: ""))
                "expired" -> Step("Activación", false, "Tu CineCasa venció" + (exp?.let { " el $it" } ?: ""))
                null -> Step("Activación", null, "No se pudo revisar ahora (la app sigue funcionando unos días)")
                else -> Step("Activación", false, "Código $code: $st")
            }
            if (st == "expired") return@withContext done("Tu CineCasa venció. Renuévala con quien te la vendió; tu código sigue siendo el mismo.")
        }

        // 3) Liga del servidor
        val acc = Prefs.account.value
        if (acc == null) {
            steps += Step("Cuenta del proveedor", false, "No hay cuenta guardada")
            return@withContext done("Falta escribir los datos de tu proveedor (usuario y contraseña).")
        }
        val host = runCatching { java.net.URI(acc.base).host }.getOrNull().orEmpty()
        val dns = host.isNotBlank() && runCatching { java.net.InetAddress.getAllByName(host).isNotEmpty() }.getOrDefault(false)
        steps += Step("Liga del servidor", dns, if (dns) host else "«$host» no existe o ya no funciona")
        if (!dns) return@withContext done(
            if (Prefs.vendorServer) "La liga de tu servidor no funciona. Avísale a tu vendedor para que la cambie en su panel; tu app la toma sola."
            else "La liga «$host» no existe o ya no funciona. Pide la liga nueva a tu proveedor y cámbiala en Ajustes → Cambiar servidor."
        )

        // 4) El proveedor contesta y la cuenta
        val r = runCatching { Xtream.Login.login(acc) }
        val e = r.exceptionOrNull()
        if (e != null) {
            val t = (e as? Xtream.Login.LoginException)?.title ?: "No se pudo conectar con tu proveedor."
            steps += Step("Tu proveedor", false, t)
            return@withContext done(t)
        }
        val info = r.getOrThrow().second
        steps += Step("Tu proveedor", true, "Contesta bien")
        val exp = info.expires
        val now = System.currentTimeMillis()
        val expTxt = exp?.let { java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale("es", "MX")).format(java.util.Date(it)) }
        when {
            exp != null && exp < now -> {
                steps += Step("Cuenta del proveedor", false, "Venció el $expTxt")
                return@withContext done("Tu cuenta con tu proveedor venció el $expTxt. Renuévala con quien te vende el servicio (no es falla de CineCasa).")
            }
            else -> steps += Step("Cuenta del proveedor", true, (if (info.status.equals("Active", true)) "Activa" else info.status.ifBlank { "Activa" }) +
                (expTxt?.let { " hasta el $it" } ?: ""))
        }
        if (info.maxCons > 0) {
            val full = info.activeCons >= info.maxCons
            steps += Step("Pantallas", !full, "${info.activeCons} de ${info.maxCons} en uso")
            if (full) return@withContext done("Todas las pantallas de tu cuenta están en uso (${info.activeCons} de ${info.maxCons}). Cierra CineCasa en otro aparato o pide más pantallas a tu proveedor. A veces el proveedor tarda unos minutos en liberarlas.")
        }
        done("Todo está bien. Si un canal o película no abre, es probable que tu proveedor lo tenga caído; prueba otro.", ok = true)
    }
}
