package com.mauricio.cinecasa.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import androidx.media3.common.C as MC
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Grabaciones programadas de canales en vivo (por ejemplo, un partido mientras no estás en casa).
 * - Se agenda con una alarma del sistema: a la hora indicada arranca un servicio que guarda el canal en un archivo.
 * - Funciona con la app cerrada; la tele debe estar conectada a la corriente y con internet.
 * - Se puede ver desde el principio aunque todavía se esté grabando.
 * - Los archivos quedan en la carpeta privada de la app (se borran al desinstalar).
 */
object Recorder {
    data class Rec(
        val id: Int,
        val chId: Int,
        val chName: String,
        val title: String,
        val start: Long,
        val end: Long,
        val status: String = SCHEDULED, // scheduled | recording | done | failed | canceled
        val path: String = "",
        val bytes: Long = 0,
        val err: String = "",
        val evKey: String = "",
        val image: String = "",
        val pos: Long = 0,
        val dur: Long = 0,
    ) {
        val scheduled get() = status == SCHEDULED
        val recording get() = status == RECORDING
        val finished get() = status == DONE
        val playable get() = (status == DONE || status == RECORDING || (status == FAILED && bytes > 2_000_000)) && path.isNotBlank()
    }

    const val SCHEDULED = "scheduled"
    const val RECORDING = "recording"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELED = "canceled"

    /** Más o menos cuánto ocupa una hora de video en vivo (HD). */
    const val BYTES_PER_HOUR = 2_700_000_000L

    private lateinit var appCtx: Context
    private lateinit var sp: SharedPreferences
    private val _list = MutableStateFlow<List<Rec>>(emptyList())
    val list: StateFlow<List<Rec>> get() = _list

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        sp = ctx.getSharedPreferences("grabaciones", Context.MODE_PRIVATE)
        _list.value = try {
            val a = JSONArray(sp.getString("list", "[]"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.toRec() }
        } catch (_: Exception) { emptyList() }
        // Si la app se cerró a media grabación y ya pasó la hora, se marca como terminada
        val now = System.currentTimeMillis()
        _list.value.filter { it.recording && !RecordService.isRunning(it.id) }.forEach { r ->
            if (now >= r.end) update(r.copy(status = if (r.bytes > 2_000_000) DONE else FAILED, err = if (r.bytes > 2_000_000) "" else "Se interrumpió la grabación."))
            else startNow(r)
        }
        rescheduleAll()
    }

    fun dir(): File = File(appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appCtx.filesDir, "grabaciones").apply { mkdirs() }
    fun freeBytes(): Long = try { StatFs(dir().path).availableBytes } catch (_: Exception) { 0L }
    fun estimate(ms: Long): Long = ms * BYTES_PER_HOUR / 3_600_000L

    fun get(id: Int) = _list.value.firstOrNull { it.id == id }

    /** ¿Ya hay una grabación para este canal que se empalma con ese horario? */
    fun overlapping(chId: Int, start: Long, end: Long) = _list.value.firstOrNull {
        it.chId == chId && (it.scheduled || it.recording) && it.start < end && start < it.end
    }

    fun forEvent(evKey: String) = _list.value.firstOrNull { it.evKey == evKey && (it.scheduled || it.recording) }

    /** Agenda (o empieza ya) una grabación. Devuelve la grabación creada. */
    fun schedule(ch: Channel, title: String, start: Long, end: Long, evKey: String = "", image: String = ""): Rec {
        overlapping(ch.id, start, end)?.let { old ->
            val merged = old.copy(start = minOf(old.start, start), end = maxOf(old.end, end))
            update(merged); setAlarm(merged); return merged
        }
        var id = (System.currentTimeMillis() / 1000 % Int.MAX_VALUE).toInt()
        while (get(id) != null) id++
        val safe = title.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ ._-]"), "").trim().take(40).ifBlank { "grabacion" }
        val r = Rec(id, ch.id, ch.name, title, start, end, path = File(dir(), "${id}_${safe.replace(' ', '_')}.ts").path, evKey = evKey, image = image.ifBlank { ch.icon })
        _list.value = _list.value + r
        persist()
        if (start <= System.currentTimeMillis() + 20_000) startNow(r) else setAlarm(r)
        return r
    }

    fun stop(id: Int) {
        val r = get(id) ?: return
        if (r.recording) RecordService.stop(appCtx, id)
        else if (r.scheduled) { cancelAlarm(id); update(r.copy(status = CANCELED)) }
    }

    fun delete(id: Int) {
        val r = get(id) ?: return
        if (r.recording) RecordService.stop(appCtx, id)
        cancelAlarm(id)
        runCatching { if (r.path.isNotBlank()) File(r.path).delete() }
        _list.value = _list.value.filterNot { it.id == id }
        persist()
    }

    fun savePos(id: Int, pos: Long, dur: Long) {
        val r = get(id) ?: return
        update(r.copy(pos = if (dur > 0 && pos > dur * 0.95) 0 else pos, dur = dur))
    }

    fun isGrowing(uri: Uri): Boolean {
        val p = uri.path ?: return false
        return _list.value.any { it.recording && it.path == p }
    }

    internal fun update(r: Rec) {
        _list.value = _list.value.map { if (it.id == r.id) r else it }
        persist()
    }

    private var lastPersist = 0L
    internal fun updateQuiet(r: Rec) {
        _list.value = _list.value.map { if (it.id == r.id) r else it }
        if (System.currentTimeMillis() - lastPersist > 20_000) persist()
    }

    private fun persist() {
        if (!::sp.isInitialized) return
        lastPersist = System.currentTimeMillis()
        val a = JSONArray()
        _list.value.sortedByDescending { it.start }.take(200).forEach { a.put(it.toJson()) }
        sp.edit().putString("list", a.toString()).apply()
    }

    // ---------------- Alarmas ----------------
    /** ¿Android deja poner alarmas exactas? (en Android 14 hay que permitirlo en Ajustes) */
    fun canExact(): Boolean = if (Build.VERSION.SDK_INT >= 31)
        (appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true

    fun exactSettingsIntent(): Intent? = if (Build.VERSION.SDK_INT >= 31)
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + appCtx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    else null

    private fun pending(id: Int): PendingIntent = PendingIntent.getBroadcast(
        appCtx, id, Intent(appCtx, RecordReceiver::class.java).setAction(RecordReceiver.START).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(r: Rec) {
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = r.start
        try {
            if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(r.id))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - 60_000, pending(r.id))
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - 60_000, pending(r.id))
        }
    }

    private fun cancelAlarm(id: Int) {
        runCatching { (appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(id)) }
    }

    /** Vuelve a poner las alarmas (al abrir la app o al prender la tele). */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (r in _list.value) {
            if (!r.scheduled) continue
            when {
                r.end <= now -> update(r.copy(status = FAILED, err = "La tele estaba apagada o sin la app a la hora de grabar."))
                r.start <= now + 20_000 -> startNow(r)
                else -> setAlarm(r)
            }
        }
    }

    internal fun startNow(r: Rec) {
        try {
            val i = Intent(appCtx, RecordService::class.java).setAction(RecordService.START).putExtra("id", r.id)
            if (Build.VERSION.SDK_INT >= 26) appCtx.startForegroundService(i) else appCtx.startService(i)
        } catch (e: Exception) {
            update(r.copy(status = FAILED, err = "Android no dejó empezar la grabación. Abre CineCasa antes de la hora."))
        }
    }

    private fun Rec.toJson() = JSONObject().put("id", id).put("ch", chId).put("cn", chName).put("t", title).put("s", start).put("e", end)
        .put("st", status).put("p", path).put("b", bytes).put("err", err).put("ev", evKey).put("img", image).put("pos", pos).put("dur", dur)
    private fun JSONObject.toRec() = Rec(
        optInt("id"), optInt("ch"), optString("cn"), optString("t"), optLong("s"), optLong("e"), optString("st", SCHEDULED),
        optString("p"), optLong("b"), optString("err"), optString("ev"), optString("img"), optLong("pos"), optLong("dur"),
    )
}

/** Recibe la alarma (y el encendido de la tele) para arrancar la grabación. */
class RecordReceiver : BroadcastReceiver() {
    companion object { const val START = "com.mauricio.cinecasa.GRABAR" }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            START -> {
                val id = intent.getIntExtra("id", 0)
                val r = Recorder.get(id) ?: return
                if (r.scheduled) Recorder.startNow(r)
            }
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON", Intent.ACTION_MY_PACKAGE_REPLACED -> Recorder.rescheduleAll()
        }
    }
}

/** Servicio que guarda el canal en un archivo mientras dura la grabación. */
class RecordService : Service() {
    companion object {
        const val START = "start"
        const val STOP = "stop"
        private const val CHANNEL = "grabaciones"
        private val running = ConcurrentHashMap<Int, Job>()
        fun isRunning(id: Int) = running[id]?.isActive == true
        /** Sigue viva la grabación (también cuenta si el trabajo apenas está arrancando). */
        private fun alive(id: Int) = running[id]?.isActive ?: true
        fun stop(ctx: Context, id: Int) {
            ctx.startService(Intent(ctx, RecordService::class.java).setAction(STOP).putExtra("id", id))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private val http by lazy {
        Net.client.newBuilder().readTimeout(20, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", 0) ?: 0
        goForeground()
        when (intent?.action) {
            STOP -> { running[id]?.cancel(); finishIfIdle() }
            else -> {
                val r = Recorder.get(id)
                if (r == null || isRunning(id) || r.status == Recorder.CANCELED || r.finished) { finishIfIdle(); return START_NOT_STICKY }
                running[id] = scope.launch { record(r) }
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Grabaciones", NotificationManager.IMPORTANCE_LOW))
        val n = notif(text())
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(77, n)
    }

    private fun text(): String {
        val recs = Recorder.list.value.filter { it.recording }
        return when {
            recs.isEmpty() -> "Preparando grabación…"
            recs.size == 1 -> "Grabando: ${recs[0].title} · termina ${TextUtil.hhmm(recs[0].end)}"
            else -> "Grabando ${recs.size} programas"
        }
    }

    private fun notif(t: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("CineCasa").setContentText(t).setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true).setContentIntent(open).build()
    }

    private fun refreshNotif() {
        runCatching { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(77, notif(text())) }
    }

    private fun locks(on: Boolean) {
        if (on) {
            if (wake == null) wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cinecasa:grabar").apply { setReferenceCounted(false); acquire(8 * 3600_000L) }
            if (wifi == null) wifi = runCatching {
                @Suppress("DEPRECATION")
                (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "cinecasa:grabar").apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        } else {
            runCatching { wake?.release() }; wake = null
            runCatching { wifi?.release() }; wifi = null
        }
    }

    private suspend fun record(r0: Recorder.Rec) {
        locks(true)
        var r = r0.copy(status = Recorder.RECORDING, err = "")
        Recorder.update(r)
        refreshNotif()
        val file = File(r.path)
        file.parentFile?.mkdirs()
        var fails = 0
        var useHls = Prefs.liveFormat == "m3u8" || (Prefs.serverFormats.isNotEmpty() && "ts" !in Prefs.serverFormats && "m3u8" in Prefs.serverFormats)
        var lastErr = ""
        try {
            FileOutputStream(file, true).use { out ->
                while (scope.isActive && alive(r.id) && System.currentTimeMillis() < r.end) {
                    val x = Repo.xtream()
                    if (x == null) { lastErr = "No hay cuenta del proveedor en este aparato."; break }
                    if (Recorder.freeBytes() < 400_000_000L) { lastErr = "Se llenó el espacio de la tele."; break }
                    val got = try {
                        if (useHls) hls(x.liveUrl(r.chId, "m3u8"), out, r) else ts(x.liveUrl(r.chId, "ts"), out, r)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        lastErr = "Se cortó la señal del canal."
                        0L
                    }
                    r = Recorder.get(r.id) ?: r
                    if (got > 0) fails = 0 else {
                        fails++
                        if (fails % 3 == 0) useHls = !useHls // prueba el otro formato
                        delay(minOf(30_000L, 3_000L * fails))
                    }
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            lastErr = ""
        } catch (e: Exception) {
            lastErr = "No se pudo guardar el archivo."
        } finally {
            val cur = Recorder.get(r.id) ?: r
            val size = file.length()
            val ok = size > 2_000_000
            Recorder.update(cur.copy(
                status = if (ok) Recorder.DONE else Recorder.FAILED,
                bytes = size,
                end = if (System.currentTimeMillis() < cur.end) System.currentTimeMillis() else cur.end,
                err = if (ok) (if (lastErr.isNotBlank() && System.currentTimeMillis() < cur.end - 60_000) "Se detuvo antes: $lastErr" else "") else lastErr.ifBlank { "El canal no mandó video." },
            ))
            running.remove(r.id)
            refreshNotif()
            finishIfIdle()
        }
    }

    /** Guarda el canal en formato TS (un solo chorro de video). Devuelve los bytes guardados. */
    private fun ts(url: String, out: FileOutputStream, r0: Recorder.Rec): Long {
        var r = r0
        val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val input = resp.body?.byteStream() ?: return 0
            val buf = ByteArray(64 * 1024)
            var total = 0L
            var all = 0L
            var tick = System.currentTimeMillis()
            while (System.currentTimeMillis() < r.end && alive(r.id)) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
                all += n
                if (System.currentTimeMillis() - tick > 5_000) {
                    tick = System.currentTimeMillis()
                    out.flush()
                    r = (Recorder.get(r.id) ?: r).copy(bytes = r.bytes + total)
                    Recorder.updateQuiet(r)
                    total = 0
                    if (Recorder.freeBytes() < 400_000_000L) break
                }
            }
            out.flush()
            Recorder.updateQuiet((Recorder.get(r.id) ?: r).copy(bytes = r.bytes + total))
            return all
        }
    }

    /** Guarda el canal desde la lista HLS (m3u8): baja cada pedacito nuevo y lo agrega al archivo. */
    private suspend fun hls(url: String, out: FileOutputStream, r0: Recorder.Rec): Long {
        var r = r0
        var playlist = url
        val seen = LinkedHashSet<String>()
        var saved = 0L
        var empty = 0
        while (System.currentTimeMillis() < r.end && alive(r.id)) {
            val body = http.newCall(Request.Builder().url(playlist).header("User-Agent", Net.UA).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                playlist = resp.request.url.toString()
                resp.body?.string().orEmpty()
            }
            val lines = body.lines().map { it.trim() }
            // Lista maestra: tomar la primera variante
            val variant = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
            if (variant >= 0) {
                val v = lines.drop(variant + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return saved
                playlist = resolve(playlist, v); continue
            }
            val target = lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION") }?.substringAfter(':')?.toDoubleOrNull() ?: 6.0
            val segs = lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { resolve(playlist, it) }
            var fresh = 0
            for (s in segs) {
                if (s in seen) continue
                seen.add(s); if (seen.size > 300) seen.remove(seen.first())
                http.newCall(Request.Builder().url(s).header("User-Agent", Net.UA).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.byteStream()?.use { inp ->
                        val n = inp.copyTo(out)
                        saved += n; fresh++
                        r = (Recorder.get(r.id) ?: r).copy(bytes = r.bytes + n)
                        Recorder.updateQuiet(r)
                    }
                }
                if (System.currentTimeMillis() >= r.end) break
            }
            out.flush()
            if (fresh == 0) { if (++empty > 10) break } else empty = 0
            delay((target * 500).toLong().coerceIn(1_000, 6_000))
        }
        return saved
    }

    private fun resolve(base: String, ref: String): String =
        runCatching { java.net.URI(base).resolve(ref).toString() }.getOrDefault(ref)

    private fun finishIfIdle() {
        if (running.values.none { it.isActive }) {
            locks(false)
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        locks(false)
        super.onDestroy()
    }
}

/**
 * Lee un archivo que todavía se está grabando: cuando llega al final, espera a que haya más
 * en vez de terminar. Así se puede ver el partido desde el principio mientras sigue grabando.
 */
@UnstableApi
class GrowingFileDataSource : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val f = RandomAccessFile(File(dataSpec.uri.path!!), "r")
        f.seek(dataSpec.position)
        raf = f
        transferStarted(dataSpec)
        return MC.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val f = raf ?: return MC.RESULT_END_OF_INPUT
        var waited = 0
        while (true) {
            val n = f.read(buffer, offset, length)
            if (n > 0) { bytesTransferred(n); return n }
            val u = uri
            if (u == null || !Recorder.isGrowing(u) || waited > 30_000) return MC.RESULT_END_OF_INPUT
            Thread.sleep(250); waited += 250
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { raf?.close() }
        raf = null
        if (uri != null) { uri = null; transferEnded() }
    }
}

/** Usa el lector de «archivo que crece» solo para las grabaciones en curso; lo demás va normal. */
@UnstableApi
class RecordingAwareDataSource(private val normal: DataSource) : DataSource {
    private var cur: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        normal.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val d = if (dataSpec.uri.scheme == "file" && Recorder.isGrowing(dataSpec.uri)) GrowingFileDataSource().also { g -> listeners.forEach { g.addTransferListener(it) } } else normal
        cur = d
        return d.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = cur?.read(buffer, offset, length) ?: MC.RESULT_END_OF_INPUT
    override fun getUri(): Uri? = cur?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = cur?.responseHeaders ?: emptyMap()
    override fun close() { cur?.close(); cur = null }

    class Factory(private val base: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = RecordingAwareDataSource(base.createDataSource())
    }
}
