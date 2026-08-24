package com.scottsx.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * ScottsTechX app entry point. Hosts:
 *
 *  - Firebase initialization (Analytics, Auth, Firestore, Storage).
 *  - A custom Coil [ImageLoader] with an aggressive memory cache
 *    (25% of available RAM) and a 50MB on-disk cache. The default
 *    Coil cache is too small for a marketplace with many product
 *    photos; this configuration makes scrolling the catalog feel
 *    instant after the first few frames.
 *
 * Registered in AndroidManifest.xml with `android:name=".ScottsTechXApp"`.
 */
class ScottsTechXApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // FirebaseApp init is automatic via google-services plugin, but
        // calling it explicitly avoids any race when the first composable
        // asks for Firebase auth immediately on first frame.
        FirebaseApp.initializeApp(this)
    }

    /**
     * Called by Coil the first time any `AsyncImage` composable is
     * rendered. Returning a custom loader means every image in the
     * app uses the same cache and OkHttp pool.
     */
    override fun newImageLoader(): ImageLoader {
        val memBytes = (Runtime.getRuntime().maxMemory() / 4).toInt().coerceAtLeast(16 * 1024 * 1024)
        val diskBytes = 50L * 1024 * 1024 // 50 MB
        return ImageLoader.Builder(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build(),
            )
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(memBytes)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .respectCacheHeaders(false) // we always want fresh on cold load
            .crossfade(true)
            .crossfade(120)
            .build()
    }
}
