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
    fun samplesVeryLargeImagesByPowerOfTwo() {
        val sample = DecodeSampleCalculator.calculate(
            width = 12000,
            height = 9000,
        )

        assertTrue(sample == 2 || sample == 4 || sample == 8)
        val decodedPixels =
            (12000 / sample).toLong() * (9000 / sample).toLong()
        assertTrue(decodedPixels <= DecodeSampleCalculator.DEFAULT_MAX_DECODE_PIXELS)
    }
}
