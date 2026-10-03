package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.graphics.Bitmap

object BitmapBlur {
    fun blurInPlace(
        bitmap: Bitmap,
        radius: Int,
    ) {
        if (radius <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
            return
        }

        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        val temp = IntArray(width * height)
        val output = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)

        boxBlurHorizontal(
            source = source,
            output = temp,
            width = width,
            height = height,
            radius = radius,
        )
        boxBlurVertical(
            source = temp,
            output = output,
            width = width,
            height = height,
            radius = radius,
        )

        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }

    private fun boxBlurHorizontal(
        source: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val window = radius * 2 + 1

        for (y in 0 until height) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            for (offset in -radius..radius) {
                val x = offset.coerceIn(0, width - 1)
                val color = source[y * width + x]
                sumA += color ushr 24
                sumR += (color shr 16) and 0xFF
                sumG += (color shr 8) and 0xFF
                sumB += color and 0xFF
            }

            for (x in 0 until width) {
                output[y * width + x] =
                    ((sumA / window) shl 24) or
                        ((sumR / window) shl 16) or
                        ((sumG / window) shl 8) or
                        (sumB / window)

                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                val remove = source[y * width + removeX]
                val add = source[y * width + addX]

                sumA += (add ushr 24) - (remove ushr 24)
                sumR += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                sumG += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                sumB += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(
        source: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val window = radius * 2 + 1

        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            for (offset in -radius..radius) {
                val y = offset.coerceIn(0, height - 1)
                val color = source[y * width + x]
                sumA += color ushr 24
                sumR += (color shr 16) and 0xFF
                sumG += (color shr 8) and 0xFF
                sumB += color and 0xFF
            }

            for (y in 0 until height) {
                output[y * width + x] =
                    ((sumA / window) shl 24) or
                        ((sumR / window) shl 16) or
                        ((sumG / window) shl 8) or
                        (sumB / window)

                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                val remove = source[removeY * width + x]
                val add = source[addY * width + x]

                sumA += (add ushr 24) - (remove ushr 24)
                sumR += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                sumG += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                sumB += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }
}
