package io.github.goroyattemiyo.wallpaperfitslideshow.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import io.github.goroyattemiyo.wallpaperfitslideshow.MainActivity
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FastSlideshowService : Service() {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null
    private lateinit var settingsStore: SettingsStore
    private lateinit var operationService: WallpaperOperationService

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(applicationContext)
        operationService = WallpaperOperationService(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            settingsStore.update { it.copy(slideshowEnabled = false) }
            stopFastMode()
            return START_NOT_STICKY
        }

        val settings = settingsStore.load()
        if (!settings.slideshowEnabled ||
            settings.intervalSeconds >= AppSettings.WORK_MANAGER_MIN_INTERVAL_SECONDS
        ) {
            stopFastMode()
            return START_NOT_STICKY
        }

        startForegroundCompat(buildNotification(settings.intervalSeconds))
        reschedule(settings.intervalSeconds)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scheduledFuture?.cancel(true)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun reschedule(intervalSeconds: Long) {
        scheduledFuture?.cancel(false)
        val safeInterval = intervalSeconds.coerceAtLeast(
            AppSettings.MIN_INTERVAL_SECONDS,
        )

        scheduledFuture = executor.scheduleWithFixedDelay(
            {
                val latest = settingsStore.load()
                if (!latest.slideshowEnabled ||
                    latest.intervalSeconds >= AppSettings.WORK_MANAGER_MIN_INTERVAL_SECONDS
                ) {
                    stopSelf()
                    return@scheduleWithFixedDelay
                }

                operationService.applyNext(requireSlideshowEnabled = true)
            },
            safeInterval,
            safeInterval,
            TimeUnit.SECONDS,
        )
    }

    private fun buildNotification(intervalSeconds: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FastSlideshowService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("高速壁紙スライドショー")
            .setContentText("${formatInterval(intervalSeconds)}ごとに壁紙を切り替えます")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        android.R.drawable.ic_media_pause,
                    ),
                    "停止",
                    stopIntent,
                ).build(),
            )

        return builder.build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopFastMode() {
        scheduledFuture?.cancel(true)
        scheduledFuture = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "高速壁紙スライドショー",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "15分未満の間隔で壁紙を切り替えている間だけ表示されます"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "fast-wallpaper-slideshow"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP =
            "io.github.goroyattemiyo.wallpaperfitslideshow.STOP_FAST_SLIDESHOW"

        fun start(context: Context) {
            val intent = Intent(context, FastSlideshowService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FastSlideshowService::class.java))
        }

        private fun formatInterval(seconds: Long): String =
            when {
                seconds < 60 -> "${seconds}秒"
                seconds % 3600L == 0L -> "${seconds / 3600L}時間"
                seconds % 60L == 0L -> "${seconds / 60L}分"
                else -> "${seconds}秒"
            }
    }
}
