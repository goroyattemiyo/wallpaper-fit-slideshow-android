package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import io.github.goroyattemiyo.wallpaperfitslideshow.model.BackgroundMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BackgroundRenderer {
    private const val MAX_WORK_WIDTH = 180

    fun draw(
        canvas: Canvas,
        source: Bitmap,
        destination: RectF,
        layout: WallpaperLayoutState,
    ) {
        canvas.drawColor(layout.backgroundColor)

        if (layout.backgroundMode != BackgroundMode.BLUR ||
            destination.width() <= 0f ||
            destination.height() <= 0f
        ) {
            return
        }

        val workWidth = min(MAX_WORK_WIDTH, destination.width().roundToInt())
            .coerceAtLeast(1)
        val workHeight = (
            workWidth.toDouble() * destination.height() / destination.width()
        ).roundToInt().coerceAtLeast(1)

        val work = Bitmap.createBitmap(
            workWidth,
            workHeight,
            Bitmap.Config.ARGB_8888,
        )

        try {
            val workCanvas = Canvas(work)
            drawCenterCrop(
                canvas = workCanvas,
                source = source,
                destination = RectF(
                    0f,
                    0f,
                    workWidth.toFloat(),
                    workHeight.toFloat(),
                ),
                paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )

            val radius = layout.blurRadius.coerceIn(
                0,
                WallpaperLayoutState.MAX_BLUR_RADIUS,
            )
            if (radius > 0) {
                BitmapBlur.blurInPlace(work, radius)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = layout.backgroundImageAlpha.coerceIn(0, 255)
            }
            canvas.drawBitmap(work, null, destination, paint)
        } finally {
            if (!work.isRecycled) {
                work.recycle()
            }
        }
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        source: Bitmap,
        destination: RectF,
        paint: Paint,
    ) {
        val scale = max(
            destination.width() / source.width.toFloat(),
            destination.height() / source.height.toFloat(),
        )
        val width = source.width * scale
        val height = source.height * scale
        val left = destination.left + (destination.width() - width) / 2f
        val top = destination.top + (destination.height() - height) / 2f

        canvas.drawBitmap(
            source,
            null,
            RectF(left, top, left + width, top + height),
            paint,
        )
    }

}
