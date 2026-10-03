package io.github.goroyattemiyo.wallpaperfitslideshow.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.SourceBitmapLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ThumbnailLoader(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun load(
        uri: String,
        imageView: ImageView,
    ) {
        imageView.tag = uri
        val cached = synchronized(cache) { cache.get(uri) }
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageDrawable(null)
        executor.execute {
            val bitmap = runCatching {
                SourceBitmapLoader(appContext).load(
                    uriString = uri,
                    maxDecodePixels = THUMBNAIL_MAX_PIXELS,
                ).bitmap
            }.getOrNull() ?: return@execute

            synchronized(cache) {
                cache.put(uri, bitmap)
            }

            mainHandler.post {
                if (imageView.tag == uri && !bitmap.isRecycled) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun release() {
        executor.shutdownNow()
        synchronized(cache) {
            cache.evictAll()
        }
    }

    companion object {
        private const val CACHE_KB = 8 * 1024
        private const val THUMBNAIL_MAX_PIXELS = 40_000L
    }
}
