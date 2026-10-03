package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeSampleCalculatorTest {
    @Test
    fun keepsSmallImagesAtFullResolution() {
        assertEquals(
            1,
            DecodeSampleCalculator.calculate(1920, 1080),
        )
    }

    @Test
    fun samplesVeryLargeImagesToMemoryCap() {
        val sample = DecodeSampleCalculator.calculate(
            width = 12000,
            height = 9000,
        )

        val decodedPixels =
            (12000 / sample).toLong() * (9000 / sample).toLong()
        assertTrue(decodedPixels <= DecodeSampleCalculator.DEFAULT_MAX_DECODE_PIXELS)
    }

    @Test
    fun preservesEnoughResolutionWhenPreferredSizeRequiresIt() {
        val sample = DecodeSampleCalculator.calculate(
            width = 4000,
            height = 3000,
            preferredMinWidth = 2160,
            preferredMinHeight = 1600,
        )

        assertEquals(1, sample)
    }

    @Test
    fun usesPowerOfTwoSamplingWhenQualityAllowsIt() {
        val sample = DecodeSampleCalculator.calculate(
            width = 8000,
            height = 6000,
            preferredMinWidth = 1800,
            preferredMinHeight = 1300,
        )

        assertEquals(4, sample)
    }
}
