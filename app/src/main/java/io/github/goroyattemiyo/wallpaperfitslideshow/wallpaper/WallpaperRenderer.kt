package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.DecodeSampleCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.PixelSize
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import java.io.IOException

class WallpaperRenderer(
    context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    @Throws(RenderException::class)
    fun render(
        item: WallpaperItem,
        targetSize: PixelSize,
    ): Bitmap {
        val uri = runCatching { Uri.parse(item.uri) }
            .getOrElse { throw SourceUnavailableException("画像URIが不正です。", it) }

        val bounds = decodeBounds(uri)
        val orientation = readOrientation(uri)
        val sampleSize = DecodeSampleCalculator.calculate(
            width = bounds.first,
            height = bounds.second,
        )
        val decoded = decodeBitmap(uri, sampleSize)
        val upright = try {
            applyExifOrientation(decoded, orientation)
        } catch (throwable: Throwable) {
            if (!decoded.isRecycled) {
                decoded.recycle()
            }
            throw DecodeException("画像の向きを補正できませんでした。", throwable)
        }

        if (upright !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }

        return try {
            renderToTarget(
                source = upright,
                item = item,
                targetSize = targetSize,
            )
        } catch (outOfMemory: OutOfMemoryError) {
            throw DecodeException("壁紙生成中にメモリが不足しました。", outOfMemory)
        } finally {
            if (!upright.isRecycled) {
                upright.recycle()
            }
        }
    }

    private fun decodeBounds(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw DecodeException("画像サイズを取得できませんでした。")
        }
        return options.outWidth to options.outHeight
    }

    private fun readOrientation(uri: Uri): Int =
        runCatching {
            openInputStream(uri).use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun decodeBitmap(uri: Uri, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return try {
            openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: throw DecodeException("画像をデコードできませんでした。")
        } catch (outOfMemory: OutOfMemoryError) {
            throw DecodeException("画像の読み込み中にメモリが不足しました。", outOfMemory)
        }
    }

    private fun openInputStream(uri: Uri) =
        contentResolver.openInputStream(uri)
            ?: throw SourceUnavailableException("画像を開けませんでした。")

    private fun applyExifOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
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

    open class RenderException(
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)

    class SourceUnavailableException(
        message: String,
        cause: Throwable? = null,
    ) : RenderException(message, cause)

    class DecodeException(
        message: String,
        cause: Throwable? = null,
    ) : RenderException(message, cause)
}
