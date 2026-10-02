package io.github.goroyattemiyo.wallpaperfitslideshow.core.render

object DecodeSampleCalculator {
    const val DEFAULT_MAX_DECODE_PIXELS = 8_000_000L

    fun calculate(
        width: Int,
        height: Int,
        maxPixels: Long = DEFAULT_MAX_DECODE_PIXELS,
    ): Int {
        require(width > 0 && height > 0)
        require(maxPixels > 0)

        var sample = 1
        while (true) {
            val next = sample * 2
            val nextWidth = (width / next).coerceAtLeast(1)
            val nextHeight = (height / next).coerceAtLeast(1)
            val nextPixels = nextWidth.toLong() * nextHeight.toLong()

            if (nextPixels > maxPixels) {
                sample = next
                continue
            }

            val currentWidth = (width / sample).coerceAtLeast(1)
            val currentHeight = (height / sample).coerceAtLeast(1)
            val currentPixels = currentWidth.toLong() * currentHeight.toLong()

            return if (currentPixels > maxPixels) next else sample
        }
    }
}
