package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.goroyattemiyo.wallpaperfitslideshow.core.render.DecodeSampleCalculator
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class SourceImageInfo(
    val rawWidth: Int,
    val rawHeight: Int,
    val orientation: Int,
    val logicalWidth: Int,
    val logicalHeight: Int,
) {
    val swapsAxes: Boolean
        get() = orientation in SWAPPED_ORIENTATIONS

    companion object {
        private val SWAPPED_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}

data class LoadedSourceBitmap(
    val bitmap: Bitmap,
    val info: SourceImageInfo,
    val sampleSize: Int,
)

class SourceBitmapLoader(
    context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    @Throws(SourceImageException::class)
    fun inspect(uriString: String): SourceImageInfo {
        val uri = parseUri(uriString)
        val bounds = decodeBounds(uri)
        val orientation = readOrientation(uri)
        val swapsAxes = orientation in setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )

        return SourceImageInfo(
            rawWidth = bounds.first,
            rawHeight = bounds.second,
            orientation = orientation,
            logicalWidth = if (swapsAxes) bounds.second else bounds.first,
            logicalHeight = if (swapsAxes) bounds.first else bounds.second,
        )
    }

    @Throws(SourceImageException::class)
    fun load(
        uriString: String,
        info: SourceImageInfo = inspect(uriString),
        preferredMinLogicalWidth: Int = 0,
        preferredMinLogicalHeight: Int = 0,
        maxDecodePixels: Long = DecodeSampleCalculator.DEFAULT_MAX_DECODE_PIXELS,
    ): LoadedSourceBitmap {
        val uri = parseUri(uriString)

        val preferredRawWidth = if (info.swapsAxes) {
            preferredMinLogicalHeight
        } else {
            preferredMinLogicalWidth
        }
        val preferredRawHeight = if (info.swapsAxes) {
            preferredMinLogicalWidth
        } else {
            preferredMinLogicalHeight
        }

        val sampleSize = DecodeSampleCalculator.calculate(
            width = info.rawWidth,
            height = info.rawHeight,
            preferredMinWidth = preferredRawWidth,
            preferredMinHeight = preferredRawHeight,
            maxPixels = maxDecodePixels,
        )
        val decoded = decodeBitmap(uri, sampleSize)

        val upright = try {
            applyExifOrientation(decoded, info.orientation)
        } catch (throwable: Throwable) {
            if (!decoded.isRecycled) {
                decoded.recycle()
            }
            throw DecodeException("画像の向きを補正できませんでした。", throwable)
        }

        if (upright !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }

        return LoadedSourceBitmap(
            bitmap = upright,
            info = info,
            sampleSize = sampleSize,
        )
    }

    private fun parseUri(uriString: String): Uri =
        runCatching { Uri.parse(uriString) }
            .getOrElse { throw SourceUnavailableException("画像URIが不正です。", it) }

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
        if (uri.scheme == "file") {
            runCatching {
                FileInputStream(File(requireNotNull(uri.path)))
            }.getOrElse {
                throw SourceUnavailableException("画像を開けませんでした。", it)
            }
        } else {
            contentResolver.openInputStream(uri)
                ?: throw SourceUnavailableException("画像を開けませんでした。")
        }

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

    open class SourceImageException(
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)

    class SourceUnavailableException(
        message: String,
        cause: Throwable? = null,
    ) : SourceImageException(message, cause)

    class DecodeException(
        message: String,
        cause: Throwable? = null,
    ) : SourceImageException(message, cause)
}
