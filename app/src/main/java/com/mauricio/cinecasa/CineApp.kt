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
        Repo.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Net.client }
        .crossfade(true)
        .respectCacheHeaders(false)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(300L * 1024 * 1024).build() }
        .build()
}
