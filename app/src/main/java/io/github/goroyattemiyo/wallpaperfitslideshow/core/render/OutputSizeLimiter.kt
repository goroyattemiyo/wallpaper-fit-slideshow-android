package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

import kotlin.math.sqrt

data class PixelSize(
    val width: Int,
    val height: Int,
)

object OutputSizeLimiter {
    const val DEFAULT_MAX_OUTPUT_PIXELS = 8_000_000L

    fun limit(
        width: Int,
        height: Int,
        maxPixels: Long = DEFAULT_MAX_OUTPUT_PIXELS,
    ): PixelSize {
        require(width > 0 && height > 0)
        require(maxPixels > 0)

        val pixels = width.toLong() * height.toLong()
        if (pixels <= maxPixels) {
            return PixelSize(width, height)
        }

        val ratio = sqrt(maxPixels.toDouble() / pixels.toDouble())
        return PixelSize(
            width = (width * ratio).toInt().coerceAtLeast(1),
            height = (height * ratio).toInt().coerceAtLeast(1),
        )
    }
}
