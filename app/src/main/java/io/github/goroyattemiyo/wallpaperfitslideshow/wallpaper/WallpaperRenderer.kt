package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.PixelSize
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import java.io.IOException

class WallpaperRenderer(
    context: Context,
) {
    private val sourceLoader = SourceBitmapLoader(context)

    @Throws(RenderException::class)
    fun render(
        item: WallpaperItem,
        targetSize: PixelSize,
    ): Bitmap {
        val source = try {
            sourceLoader.load(item.uri)
        } catch (exception: SourceBitmapLoader.SourceImageException) {
            throw RenderException(
                exception.message ?: "画像を読み込めませんでした。",
                exception,
            )
        }

        return try {
            renderToTarget(
                source = source,
                item = item,
                targetSize = targetSize,
            )
        } catch (outOfMemory: OutOfMemoryError) {
            throw RenderException("壁紙生成中にメモリが不足しました。", outOfMemory)
        } finally {
            if (!source.isRecycled) {
                source.recycle()
            }
        }
    }

    private fun renderToTarget(
        source: Bitmap,
        item: WallpaperItem,
        targetSize: PixelSize,
    ): Bitmap {
        val layout = item.layout
        val transform = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = source.width,
                sourceHeight = source.height,
                targetWidth = targetSize.width,
                targetHeight = targetSize.height,
                mode = layout.mode,
                userScale = layout.userScale,
                offsetXNormalized = layout.offsetXNormalized,
                offsetYNormalized = layout.offsetYNormalized,
            ),
        )

        val output = Bitmap.createBitmap(
            targetSize.width,
            targetSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        canvas.drawColor(layout.backgroundColor)

        val destination = RectF(
            transform.translationX.toFloat(),
            transform.translationY.toFloat(),
            (transform.translationX + transform.renderedWidth).toFloat(),
            (transform.translationY + transform.renderedHeight).toFloat(),
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, destination, paint)
        return output
    }

    class RenderException(
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)
}
