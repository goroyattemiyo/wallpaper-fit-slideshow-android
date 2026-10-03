package io.github.goroyattemiyo.wallpaperfitslideshow.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import java.util.concurrent.TimeUnit

class SlideshowScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(intervalSeconds: Long) {
        val safeInterval = intervalSeconds.coerceAtLeast(
            AppSettings.MIN_INTERVAL_SECONDS,
        )

        if (safeInterval < AppSettings.WORK_MANAGER_MIN_INTERVAL_SECONDS) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            FastSlideshowService.start(appContext)
            return
        }

        FastSlideshowService.stop(appContext)

        val request = PeriodicWorkRequest.Builder(
            WallpaperWorker::class.java,
            safeInterval,
            TimeUnit.SECONDS,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        FastSlideshowService.stop(appContext)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "wallpaper-fit-slideshow-periodic"
    }
}
