package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutTransform
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.PixelSize
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import java.io.IOException
import kotlin.math.ceil

class WallpaperRenderer(
    context: Context,
) {
    private val sourceLoader = SourceBitmapLoader(context)

    @Throws(RenderException::class)
    fun render(
        item: WallpaperItem,
        targetSize: PixelSize,
    ): Bitmap {
        val info = try {
            sourceLoader.inspect(item.uri)
        } catch (exception: SourceBitmapLoader.SourceImageException) {
            throw RenderException(
                exception.message ?: "画像情報を読み込めませんでした。",
                exception,
            )
        }

        val transform = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = info.logicalWidth,
                sourceHeight = info.logicalHeight,
                targetWidth = targetSize.width,
                targetHeight = targetSize.height,
                mode = item.layout.mode,
                userScale = item.layout.userScale,
                offsetXNormalized = item.layout.offsetXNormalized,
                offsetYNormalized = item.layout.offsetYNormalized,
            ),
        )

        val loaded = try {
            sourceLoader.load(
                uriString = item.uri,
                info = info,
                preferredMinLogicalWidth = ceil(transform.renderedWidth)
                    .toInt()
                    .coerceAtMost(info.logicalWidth),
                preferredMinLogicalHeight = ceil(transform.renderedHeight)
                    .toInt()
                    .coerceAtMost(info.logicalHeight),
            )
        } catch (exception: SourceBitmapLoader.SourceImageException) {
            throw RenderException(
                exception.message ?: "画像を読み込めませんでした。",
                exception,
            )
        }

        return try {
            renderToTarget(
                source = loaded.bitmap,
                layoutTransform = transform,
                backgroundColor = item.layout.backgroundColor,
                targetSize = targetSize,
            )
        } catch (outOfMemory: OutOfMemoryError) {
            throw RenderException("壁紙生成中にメモリが不足しました。", outOfMemory)
        } finally {
            if (!loaded.bitmap.isRecycled) {
                loaded.bitmap.recycle()
            }
        }
    }

    private fun renderToTarget(
        source: Bitmap,
        layoutTransform: LayoutTransform,
        backgroundColor: Int,
        targetSize: PixelSize,
    ): Bitmap {
        val output = Bitmap.createBitmap(
            targetSize.width,
            targetSize.height,
            Bitmap.Config.ARGB_8888,
        )

        try {
            val canvas = Canvas(output)
            canvas.drawColor(backgroundColor)

            val destination = RectF(
                layoutTransform.translationX.toFloat(),
                layoutTransform.translationY.toFloat(),
                (layoutTransform.translationX + layoutTransform.renderedWidth).toFloat(),
                (layoutTransform.translationY + layoutTransform.renderedHeight).toFloat(),
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, null, destination, paint)
            return output
        } catch (throwable: Throwable) {
            if (!output.isRecycled) {
                output.recycle()
            }
            throw throwable
        }
    }

    class RenderException(
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)
}
