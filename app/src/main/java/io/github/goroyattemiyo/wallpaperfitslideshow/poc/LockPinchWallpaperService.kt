package io.github.goroyattemiyo.wallpaperfitslideshow.poc

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.BackgroundRenderer
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.SourceBitmapLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class LockPinchWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LockPinchEngine()

    private inner class LockPinchEngine : Engine() {
        private val settingsStore = SettingsStore(applicationContext)
        private val sourceLoader = SourceBitmapLoader(applicationContext)
        private val executor: ExecutorService = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }

        private var sourceBitmap: Bitmap? = null
        private var logicalSourceWidth = 1
        private var logicalSourceHeight = 1
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private var surfaceReady = false
        private var activeItemId: String? = null
        private var layoutDraft = WallpaperLayoutState()
        private var pinchEventCount = 0

        private val scaleDetector = ScaleGestureDetector(
            applicationContext,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    if (!shouldHandleTouch()) {
                        return false
                    }
                    ensureCropModePreservingSize()
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!shouldHandleTouch()) {
                        return false
                    }
                    pinchEventCount += 1
                    layoutDraft = layoutDraft.copy(
                        userScale = (
                            layoutDraft.userScale * detector.scaleFactor.toDouble()
                        ).coerceIn(
                            LayoutCalculator.MIN_CROP_USER_SCALE,
                            LayoutCalculator.MAX_CROP_USER_SCALE,
                        ),
                    )
                    drawFrame()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    persistLayout()
                }
            },
        )

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(false)
            loadCurrentLockImage()
        }

        override fun onDestroy() {
            executor.shutdownNow()
            sourceBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
            sourceBitmap = null
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            drawFrame()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                drawFrame()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!shouldHandleTouch()) {
                return
            }
            scaleDetector.onTouchEvent(event)
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            drawFrame()
        }

        private fun shouldHandleTouch(): Boolean {
            if (isPreview) {
                return true
            }
            if (Build.VERSION.SDK_INT < 34) {
                return true
            }
            val flags = wallpaperFlags
            return flags == 0 || flags and WallpaperManager.FLAG_LOCK != 0
        }

        private fun loadCurrentLockImage() {
            val snapshot = settingsStore.load()
            val item = snapshot.items.firstOrNull {
                it.id == snapshot.currentLockItemId
            } ?: snapshot.items.firstOrNull { it.lockEnabled }

            if (item == null) {
                drawFrame()
                return
            }

            activeItemId = item.id
            layoutDraft = item.lockLayout
            executor.execute {
                val loaded = runCatching {
                    sourceLoader.load(
                        uriString = item.uri,
                        maxDecodePixels = MAX_POC_DECODE_PIXELS,
                    )
                }.getOrNull() ?: return@execute

                mainHandler.post {
                    if (executor.isShutdown) {
                        if (!loaded.bitmap.isRecycled) {
                            loaded.bitmap.recycle()
                        }
                        return@post
                    }

                    sourceBitmap?.let {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    sourceBitmap = loaded.bitmap
                    logicalSourceWidth = loaded.info.logicalWidth.coerceAtLeast(1)
                    logicalSourceHeight = loaded.info.logicalHeight.coerceAtLeast(1)
                    drawFrame()
                }
            }
        }

        private fun ensureCropModePreservingSize() {
            if (layoutDraft.mode == LayoutMode.CROP ||
                surfaceWidth <= 0 ||
                surfaceHeight <= 0
            ) {
                return
            }

            val current = LayoutCalculator.calculate(
                LayoutRequest(
                    sourceWidth = logicalSourceWidth,
                    sourceHeight = logicalSourceHeight,
                    targetWidth = surfaceWidth,
                    targetHeight = surfaceHeight,
                    mode = LayoutMode.CONTAIN,
                    userScale = 1.0,
                    offsetXNormalized = layoutDraft.offsetXNormalized,
                    offsetYNormalized = layoutDraft.offsetYNormalized,
                ),
            )
            val fillScale = max(
                surfaceWidth.toDouble() / logicalSourceWidth.toDouble(),
                surfaceHeight.toDouble() / logicalSourceHeight.toDouble(),
            )
            layoutDraft = layoutDraft.copy(
                mode = LayoutMode.CROP,
                userScale = (current.scale / fillScale).coerceIn(
                    LayoutCalculator.MIN_CROP_USER_SCALE,
                    LayoutCalculator.MAX_CROP_USER_SCALE,
                ),
            )
        }

        private fun persistLayout() {
            val itemId = activeItemId ?: return
            settingsStore.update { settings ->
                settings.copy(
                    items = settings.items.map { item ->
                        if (item.id == itemId) {
                            item.copy(lockLayout = layoutDraft)
                        } else {
                            item
                        }
                    },
                )
            }
        }

        private fun drawFrame() {
            if (!surfaceReady) {
                return
            }

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                val source = sourceBitmap
                if (source == null || source.isRecycled) {
                    canvas.drawColor(Color.BLACK)
                    drawDebugText(canvas, "LOCK PINCH PoC / image unavailable")
                    return
                }

                val destination = RectF(
                    0f,
                    0f,
                    surfaceWidth.toFloat(),
                    surfaceHeight.toFloat(),
                )
                BackgroundRenderer.draw(
                    canvas = canvas,
                    source = source,
                    destination = destination,
                    layout = layoutDraft,
                )

                val transform = LayoutCalculator.calculate(
                    LayoutRequest(
                        sourceWidth = logicalSourceWidth,
                        sourceHeight = logicalSourceHeight,
                        targetWidth = surfaceWidth,
                        targetHeight = surfaceHeight,
                        mode = layoutDraft.mode,
                        userScale = layoutDraft.userScale,
                        offsetXNormalized = layoutDraft.offsetXNormalized,
                        offsetYNormalized = layoutDraft.offsetYNormalized,
                    ),
                )

                canvas.drawBitmap(
                    source,
                    null,
                    RectF(
                        transform.translationX.toFloat(),
                        transform.translationY.toFloat(),
                        (transform.translationX + transform.renderedWidth).toFloat(),
                        (transform.translationY + transform.renderedHeight).toFloat(),
                    ),
                    paint,
                )

                drawDebugText(
                    canvas,
                    "LOCK PINCH PoC  scale=" +
                        "${(layoutDraft.userScale * 100).toInt()}%  touch=$pinchEventCount",
                )
            } finally {
                canvas?.let {
                    runCatching {
                        surfaceHolder.unlockCanvasAndPost(it)
                    }
                }
            }
        }

        private fun drawDebugText(
            canvas: Canvas,
            text: String,
        ) {
            canvas.drawText(
                text,
                24f,
                (surfaceHeight - 36).coerceAtLeast(36).toFloat(),
                debugPaint,
            )
        }

    }

    companion object {
        private const val MAX_POC_DECODE_PIXELS = 4_000_000L
    }
}
