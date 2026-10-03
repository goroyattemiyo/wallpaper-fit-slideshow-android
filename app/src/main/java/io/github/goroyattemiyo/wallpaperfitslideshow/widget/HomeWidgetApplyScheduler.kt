package io.github.goroyattemiyo.wallpaperfitslideshow.widget

import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
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

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()

    @Volatile
    private var pending: ScheduledFuture<*>? = null

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            pending?.cancel(false)
            pending = executor.schedule(
                {
                    applyLatest(appContext)
                },
                APPLY_DEBOUNCE_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun applyLatest(context: Context) {
        val currentId = SettingsStore(context).load().currentHomeItemId ?: return
        WallpaperOperationService(context).applyItem(
            currentId,
            WallpaperTarget.HOME,
        )
    }
}
