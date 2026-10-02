package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.app.WallpaperManager
import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.OutputSizeLimiter
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.PixelSize

class WallpaperTargetSizeResolver(
    private val context: Context,
) {
    fun resolve(): PixelSize {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val displayMetrics = context.resources.displayMetrics

        val requestedWidth = wallpaperManager.desiredMinimumWidth
            .takeIf { it > 0 }
            ?: displayMetrics.widthPixels
        val requestedHeight = wallpaperManager.desiredMinimumHeight
            .takeIf { it > 0 }
            ?: displayMetrics.heightPixels

        val safeWidth = requestedWidth.coerceAtLeast(displayMetrics.widthPixels.coerceAtLeast(1))
        val safeHeight = requestedHeight.coerceAtLeast(displayMetrics.heightPixels.coerceAtLeast(1))

        return OutputSizeLimiter.limit(
            width = safeWidth,
            height = safeHeight,
        )
    }
}
