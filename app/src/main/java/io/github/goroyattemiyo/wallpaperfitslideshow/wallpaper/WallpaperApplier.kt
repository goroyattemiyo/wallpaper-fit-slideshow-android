package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import java.io.IOException

class WallpaperApplier(
    context: Context,
) {
    private val wallpaperManager = WallpaperManager.getInstance(context)

    @Throws(ApplyException::class)
    fun apply(
        bitmap: Bitmap,
        target: WallpaperTarget,
    ) {
        if (!wallpaperManager.isWallpaperSupported) {
            throw ApplyException("この端末では壁紙変更がサポートされていません。")
        }
        if (!wallpaperManager.isSetWallpaperAllowed) {
            throw ApplyException("端末設定により壁紙変更が許可されていません。")
        }

        val flags = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }

        try {
            val wallpaperId = wallpaperManager.setBitmap(
                bitmap,
                null,
                false,
                flags,
            )
            if (wallpaperId == 0) {
                throw ApplyException("壁紙の適用に失敗しました。")
            }
        } catch (exception: SecurityException) {
            throw ApplyException("壁紙変更が拒否されました。", exception)
        } catch (exception: IOException) {
            throw ApplyException("壁紙の保存中にエラーが発生しました。", exception)
        }
    }

    class ApplyException(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)
}
