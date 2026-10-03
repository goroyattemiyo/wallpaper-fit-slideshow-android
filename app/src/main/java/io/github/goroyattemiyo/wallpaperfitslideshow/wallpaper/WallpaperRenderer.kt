package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutTransform
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.WallpaperGeometry
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import java.io.IOException
import kotlin.math.ceil

data class RenderedWallpaper(
    val bitmap: Bitmap,
    val visibleCropHint: Rect,
)

class WallpaperRenderer(
    context: Context,
) {
    private val sourceLoader = SourceBitmapLoader(context)

    @Throws(RenderException::class)
    fun render(
        item: WallpaperItem,
        layout: WallpaperLayoutState,
        geometry: WallpaperGeometry,
    ): RenderedWallpaper {
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
                targetWidth = geometry.visibleWidth,
                targetHeight = geometry.visibleHeight,
                mode = layout.mode,
                userScale = layout.userScale,
                offsetXNormalized = layout.offsetXNormalized,
                offsetYNormalized = layout.offsetYNormalized,
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
                layout = layout,
                geometry = geometry,
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
        layout: io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState,
        geometry: WallpaperGeometry,
    ): RenderedWallpaper {
        val output = Bitmap.createBitmap(
            geometry.outputSize.width,
            geometry.outputSize.height,
            Bitmap.Config.ARGB_8888,
        )

        try {
            val canvas = Canvas(output)
            val visibleRect = RectF(
                geometry.visibleLeft.toFloat(),
                geometry.visibleTop.toFloat(),
                geometry.visibleRight.toFloat(),
                geometry.visibleBottom.toFloat(),
            )
            BackgroundRenderer.draw(
                canvas = canvas,
                source = source,
                destination = visibleRect,
                layout = layout,
            )

            val left = geometry.visibleLeft + layoutTransform.translationX
            val top = geometry.visibleTop + layoutTransform.translationY
            val destination = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + layoutTransform.renderedWidth).toFloat(),
                (top + layoutTransform.renderedHeight).toFloat(),
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, null, destination, paint)

            return RenderedWallpaper(
                bitmap = output,
                visibleCropHint = Rect(
                    geometry.visibleLeft,
                    geometry.visibleTop,
                    geometry.visibleRight,
                    geometry.visibleBottom,
                ),
            )
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
