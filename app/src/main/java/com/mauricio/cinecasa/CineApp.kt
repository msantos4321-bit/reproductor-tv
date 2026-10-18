package com.mauricio.cinecasa

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mauricio.cinecasa.data.Net
import com.mauricio.cinecasa.data.Repo

class CineApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Si la app se cierra sola, guarda el motivo para enviarlo al abrir de nuevo
        val crash = getSharedPreferences("crash", MODE_PRIVATE)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                crash.edit().putString("last",
                    "v${BuildConfig.VERSION_NAME} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · hilo ${t.name}\n" +
                        android.util.Log.getStackTraceString(e).take(8000)).commit()
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        Repo.init(this)
        crash.getString("last", null)?.let { text ->
            Thread {
                if (com.mauricio.cinecasa.data.Cloud.reportCrash(text)) crash.edit().remove("last").apply()
            }.start()
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Net.client }
        .crossfade(true)
        .respectCacheHeaders(false)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(300L * 1024 * 1024).build() }
        .build()
}
