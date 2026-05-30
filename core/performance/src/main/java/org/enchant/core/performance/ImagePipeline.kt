package org.enchant.core.performance

import android.content.Context
import android.util.Log
import android.widget.ImageView
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImagePipeline {
    private const val TAG = "ImagePipeline"

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val memoryCacheSize = (Runtime.getRuntime().maxMemory() / 4).toLong()
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
        initialized = true
    }

    fun loadImage(context: Context, url: String, target: ImageView) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .target(target)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        Coil.imageLoader(context).enqueue(request)
    }

    suspend fun prefetchImage(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .size(Size.ORIGINAL)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            if (result is ErrorResult) {
                Log.w(TAG, "Prefetch failed for $url: ${result.throwable?.message}")
            }
        }
    }

    fun clearMemoryCache(context: Context) {
        Coil.imageLoader(context).memoryCache?.clear()
    }

    fun clearDiskCache(context: Context) {
        Coil.imageLoader(context).diskCache?.clear()
    }
}
