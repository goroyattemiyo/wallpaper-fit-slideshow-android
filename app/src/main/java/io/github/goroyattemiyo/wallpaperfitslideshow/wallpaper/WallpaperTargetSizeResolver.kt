package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.app.WallpaperManager
import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.WallpaperGeometry
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.WallpaperGeometryCalculator

class WallpaperTargetSizeResolver(
    private val context: Context,
) {
    fun resolve(): WallpaperGeometry {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val displayMetrics = context.resources.displayMetrics
        val displayWidth = displayMetrics.widthPixels.coerceAtLeast(1)
        val displayHeight = displayMetrics.heightPixels.coerceAtLeast(1)

        return WallpaperGeometryCalculator.calculate(
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            desiredWidth = wallpaperManager.desiredMinimumWidth,
            desiredHeight = wallpaperManager.desiredMinimumHeight,
        )
    }
}
