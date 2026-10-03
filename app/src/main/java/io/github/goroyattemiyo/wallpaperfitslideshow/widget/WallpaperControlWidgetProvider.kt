package io.github.goroyattemiyo.wallpaperfitslideshow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.goroyattemiyo.wallpaperfitslideshow.MainActivity
import io.github.goroyattemiyo.wallpaperfitslideshow.R
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WallpaperControlWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                buildViews(context),
            )
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)

        if (intent.action !in CONTROL_ACTIONS) {
            return
        }

        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                HomeWidgetController(context).handle(intent.action.orEmpty())
            } finally {
                updateAll(context)
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_ZOOM_IN =
            "io.github.goroyattemiyo.wallpaperfitslideshow.widget.ZOOM_IN"
        internal const val ACTION_ZOOM_OUT =
            "io.github.goroyattemiyo.wallpaperfitslideshow.widget.ZOOM_OUT"
        internal const val ACTION_ZOOM_RESET =
            "io.github.goroyattemiyo.wallpaperfitslideshow.widget.ZOOM_RESET"
        internal const val ACTION_BLUR_IN =
            "io.github.goroyattemiyo.wallpaperfitslideshow.widget.BLUR_IN"
        internal const val ACTION_BLUR_OUT =
            "io.github.goroyattemiyo.wallpaperfitslideshow.widget.BLUR_OUT"

        private val CONTROL_ACTIONS = setOf(
            ACTION_ZOOM_IN,
            ACTION_ZOOM_OUT,
            ACTION_ZOOM_RESET,
            ACTION_BLUR_IN,
            ACTION_BLUR_OUT,
        )

        private val EXECUTOR = Executors.newSingleThreadExecutor()

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(
                appContext,
                WallpaperControlWidgetProvider::class.java,
            )
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) {
                return
            }

            val views = buildViews(appContext)
            manager.updateAppWidget(ids, views)
        }

        private fun buildViews(context: Context): RemoteViews {
            val settings = SettingsStore(context).load()
            val current = settings.items.firstOrNull {
                it.id == settings.currentHomeItemId
            }
            val zoomText = when {
                current == null -> "Zoom --"
                current.homeLayout.mode == LayoutMode.CONTAIN -> "Zoom 全体"
                else -> "Zoom ${(current.homeLayout.userScale * 100).roundToInt()}%"
            }
            val blurText = "Blur ${settings.homeWallpaperBlurRadius}"
            val title = current?.displayName ?: "ホーム壁紙なし"

            return RemoteViews(
                context.packageName,
                R.layout.widget_wallpaper_control,
            ).apply {
                setTextViewText(R.id.widget_current_name, title)
                setTextViewText(R.id.widget_zoom_value, zoomText)
                setTextViewText(R.id.widget_blur_value, blurText)

                setOnClickPendingIntent(
                    R.id.widget_zoom_out,
                    broadcastPendingIntent(context, ACTION_ZOOM_OUT, 101),
                )
                setOnClickPendingIntent(
                    R.id.widget_zoom_in,
                    broadcastPendingIntent(context, ACTION_ZOOM_IN, 102),
                )
                setOnClickPendingIntent(
                    R.id.widget_zoom_reset,
                    broadcastPendingIntent(context, ACTION_ZOOM_RESET, 103),
                )
                setOnClickPendingIntent(
                    R.id.widget_blur_out,
                    broadcastPendingIntent(context, ACTION_BLUR_OUT, 104),
                )
                setOnClickPendingIntent(
                    R.id.widget_blur_in,
                    broadcastPendingIntent(context, ACTION_BLUR_IN, 105),
                )
                setOnClickPendingIntent(
                    R.id.widget_current_name,
                    appPendingIntent(context),
                )
            }
        }

        private fun broadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, WallpaperControlWidgetProvider::class.java)
                    .setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun appPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                200,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
