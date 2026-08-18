package com.scottsx.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// helper for maxMemory reference
private val maxMemory: Long get() = Runtime.getRuntime().maxMemory()

/**
 * ScottsTechX — Application entry point.
 *
 * - Initialises Firebase (via google-services.json)
 * - Registers ONE global Coil ImageLoader. Screens MUST use
 *   Coil.imageLoader(context) — never a fresh ImageLoader.Builder(ctx).
 */
class ScottsTechXApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        UserPrefs.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(maxMemory / 4)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    companion object {
        fun prefs(context: Context) =
            context.getSharedPreferences("scottsx_prefs", Context.MODE_PRIVATE)
    }
}
