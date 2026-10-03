package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

object DecodeSampleCalculator {
    const val DEFAULT_MAX_DECODE_PIXELS = 16_000_000L

    fun calculate(
        width: Int,
        height: Int,
        preferredMinWidth: Int = 0,
        preferredMinHeight: Int = 0,
        maxPixels: Long = DEFAULT_MAX_DECODE_PIXELS,
    ): Int {
        require(width > 0 && height > 0)
        require(preferredMinWidth >= 0 && preferredMinHeight >= 0)
        require(maxPixels > 0)

        var sample = 1

        if (preferredMinWidth > 0 && preferredMinHeight > 0) {
            while (true) {
                val next = sample * 2
                val nextWidth = (width / next).coerceAtLeast(1)
                val nextHeight = (height / next).coerceAtLeast(1)
                if (nextWidth < preferredMinWidth || nextHeight < preferredMinHeight) {
                    break
                }
                sample = next
            }
        }

        while (decodedPixels(width, height, sample) > maxPixels) {
            sample *= 2
        }

        return sample
    }

    private fun decodedPixels(
        width: Int,
        height: Int,
        sample: Int,
    ): Long {
        val decodedWidth = (width / sample).coerceAtLeast(1)
        val decodedHeight = (height / sample).coerceAtLeast(1)
        return decodedWidth.toLong() * decodedHeight.toLong()
    }
}
