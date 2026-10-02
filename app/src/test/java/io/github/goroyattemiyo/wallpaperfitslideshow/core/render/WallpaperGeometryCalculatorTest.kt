package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperGeometryCalculatorTest {
    @Test
    fun keepsDisplayAsVisibleViewportInsideWiderDesiredWallpaper() {
        val result = WallpaperGeometryCalculator.calculate(
            displayWidth = 1080,
            displayHeight = 2400,
            desiredWidth = 2160,
            desiredHeight = 2400,
        )

        assertEquals(PixelSize(2160, 2400), result.outputSize)
        assertEquals(540, result.visibleLeft)
        assertEquals(0, result.visibleTop)
        assertEquals(1080, result.visibleWidth)
        assertEquals(2400, result.visibleHeight)
    }

    @Test
    fun fallsBackToDisplayWhenDesiredSizeIsUnavailable() {
        val result = WallpaperGeometryCalculator.calculate(
            displayWidth = 1080,
            displayHeight = 2400,
            desiredWidth = 0,
            desiredHeight = 0,
        )

        assertEquals(PixelSize(1080, 2400), result.outputSize)
        assertEquals(0, result.visibleLeft)
        assertEquals(0, result.visibleTop)
        assertEquals(1080, result.visibleWidth)
        assertEquals(2400, result.visibleHeight)
    }

    @Test
    fun outputCapAlsoScalesVisibleViewport() {
        val result = WallpaperGeometryCalculator.calculate(
            displayWidth = 4000,
            displayHeight = 3000,
            desiredWidth = 8000,
            desiredHeight = 6000,
            maxOutputPixels = 8_000_000,
        )

        assertTrue(
            result.outputSize.width.toLong() * result.outputSize.height <= 8_000_000,
        )
        assertTrue(result.visibleWidth <= result.outputSize.width)
        assertTrue(result.visibleHeight <= result.outputSize.height)
        assertEquals(
            4.0 / 3.0,
            result.visibleWidth.toDouble() / result.visibleHeight.toDouble(),
            0.002,
        )
    }
}
