package io.github.goroyattemiyo.wallpaperfitslideshow.widget

import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationResult
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps widget taps responsive by separating setting changes from bitmap work.
 *
 * Rapid taps cancel only the latest not-yet-started apply. If an apply is
 * already rendering, one latest apply can remain queued behind it.
 */
internal object HomeWidgetApplyScheduler {
    private const val APPLY_DEBOUNCE_MILLIS = 250L
    private const val BUSY_RETRY_MILLIS = 300L
    private const val MAX_BUSY_RETRIES = 3

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()

    @Volatile
    private var pending: ScheduledFuture<*>? = null

    @Volatile
    private var generation: Long = 0L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            generation += 1
            val scheduledGeneration = generation
            pending?.cancel(false)
            pending = executor.schedule(
                {
                    applyLatest(
                        context = appContext,
                        busyRetriesRemaining = MAX_BUSY_RETRIES,
                        scheduledGeneration = scheduledGeneration,
                    )
                },
                APPLY_DEBOUNCE_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun applyLatest(
        context: Context,
        busyRetriesRemaining: Int,
        scheduledGeneration: Long,
    ) {
        if (scheduledGeneration != generation) {
            return
        }

        val currentId = SettingsStore(context).load().currentHomeItemId ?: return
        val result = WallpaperOperationService(context).applyItem(
            currentId,
            WallpaperTarget.HOME,
        )
        if (result == WallpaperOperationResult.Busy &&
            busyRetriesRemaining > 0
        ) {
            synchronized(lock) {
                if (scheduledGeneration != generation) {
                    return
                }
                pending = executor.schedule(
                    {
                        applyLatest(
                            context = context,
                            busyRetriesRemaining = busyRetriesRemaining - 1,
                            scheduledGeneration = scheduledGeneration,
                        )
                    },
                    BUSY_RETRY_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
    }
}
