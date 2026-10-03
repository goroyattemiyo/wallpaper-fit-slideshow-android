package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

object WallpaperBlurRenderer {
    private const val MAX_WORK_WIDTH = 360

    fun apply(
        bitmap: Bitmap,
        region: Rect,
        radius: Int,
    ) {
        val safeRadius = radius.coerceAtLeast(0)
        if (safeRadius == 0 || region.width() <= 0 || region.height() <= 0) {
            return
        }

        val clipped = Rect(
            region.left.coerceIn(0, bitmap.width),
            region.top.coerceIn(0, bitmap.height),
            region.right.coerceIn(0, bitmap.width),
            region.bottom.coerceIn(0, bitmap.height),
        )
        if (clipped.width() <= 0 || clipped.height() <= 0) {
            return
        }

        val workWidth = min(MAX_WORK_WIDTH, clipped.width()).coerceAtLeast(1)
        val workHeight = (
            workWidth.toDouble() * clipped.height().toDouble() / clipped.width().toDouble()
        ).roundToInt().coerceAtLeast(1)

        val work = Bitmap.createBitmap(
            workWidth,
            workHeight,
            Bitmap.Config.ARGB_8888,
        )

        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            Canvas(work).drawBitmap(
                bitmap,
                clipped,
                RectF(
                    0f,
                    0f,
                    workWidth.toFloat(),
                    workHeight.toFloat(),
                ),
                paint,
            )
            BitmapBlur.blurInPlace(work, safeRadius)
            Canvas(bitmap).drawBitmap(
                work,
                null,
                RectF(
                    clipped.left.toFloat(),
                    clipped.top.toFloat(),
                    clipped.right.toFloat(),
                    clipped.bottom.toFloat(),
                ),
                paint,
            )
        } finally {
            if (!work.isRecycled) {
                work.recycle()
            }
        }
    }
}
