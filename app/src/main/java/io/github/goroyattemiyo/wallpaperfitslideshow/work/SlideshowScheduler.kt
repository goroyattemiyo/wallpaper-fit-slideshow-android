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
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(AppSettings.MIN_INTERVAL_MINUTES)
        val request = PeriodicWorkRequest.Builder(
            WallpaperWorker::class.java,
            safeInterval,
            TimeUnit.MINUTES,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "wallpaper-fit-slideshow-periodic"
    }
}
