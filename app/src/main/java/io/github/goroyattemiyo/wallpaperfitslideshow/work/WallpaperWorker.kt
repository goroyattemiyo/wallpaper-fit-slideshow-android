package io.github.goroyattemiyo.wallpaperfitslideshow.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationResult
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService

class WallpaperWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        return when (
            WallpaperOperationService(applicationContext)
                .applyNext(requireSlideshowEnabled = true)
        ) {
            is WallpaperOperationResult.Success,
            is WallpaperOperationResult.Disabled,
            is WallpaperOperationResult.NoImages,
            is WallpaperOperationResult.Failure,
            -> Result.success()

            WallpaperOperationResult.Busy -> Result.success()
        }
    }
}
