package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputSizeLimiterTest {
    @Test
    fun leavesNormalPhoneWallpaperSizeUntouched() {
        assertEquals(
            PixelSize(2160, 2400),
            OutputSizeLimiter.limit(2160, 2400),
        )
    }

    @Test
    fun limitsOversizedOutputWhileKeepingAspectRatioClose() {
        val result = OutputSizeLimiter.limit(
            width = 8000,
            height = 6000,
            maxPixels = 8_000_000,
        )

        assertTrue(result.width.toLong() * result.height <= 8_000_000)
        assertEquals(4.0 / 3.0, result.width.toDouble() / result.height, 0.001)
    }
}
