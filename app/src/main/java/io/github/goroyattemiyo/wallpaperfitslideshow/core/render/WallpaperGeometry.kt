package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class WallpaperGeometry(
    val outputSize: PixelSize,
    val visibleLeft: Int,
    val visibleTop: Int,
    val visibleWidth: Int,
    val visibleHeight: Int,
) {
    val visibleRight: Int
        get() = visibleLeft + visibleWidth

    val visibleBottom: Int
        get() = visibleTop + visibleHeight
}

object WallpaperGeometryCalculator {
    fun calculate(
        displayWidth: Int,
        displayHeight: Int,
        desiredWidth: Int,
        desiredHeight: Int,
        maxOutputPixels: Long = OutputSizeLimiter.DEFAULT_MAX_OUTPUT_PIXELS,
    ): WallpaperGeometry {
        require(displayWidth > 0 && displayHeight > 0)

        val requestedWidth = max(displayWidth, desiredWidth.coerceAtLeast(0))
        val requestedHeight = max(displayHeight, desiredHeight.coerceAtLeast(0))
        val output = OutputSizeLimiter.limit(
            width = requestedWidth,
            height = requestedHeight,
            maxPixels = maxOutputPixels,
        )

        val outputScale = min(
            output.width.toDouble() / requestedWidth.toDouble(),
            output.height.toDouble() / requestedHeight.toDouble(),
        )

        val visibleWidth = (displayWidth * outputScale)
            .roundToInt()
            .coerceIn(1, output.width)
        val visibleHeight = (displayHeight * outputScale)
            .roundToInt()
            .coerceIn(1, output.height)

        return WallpaperGeometry(
            outputSize = output,
            visibleLeft = (output.width - visibleWidth) / 2,
            visibleTop = (output.height - visibleHeight) / 2,
            visibleWidth = visibleWidth,
            visibleHeight = visibleHeight,
        )
    }
}
