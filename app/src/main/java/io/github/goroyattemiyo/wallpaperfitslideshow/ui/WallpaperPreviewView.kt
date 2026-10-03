package io.github.goroyattemiyo.wallpaperfitslideshow.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.model.BackgroundMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.BackgroundRenderer
import kotlin.math.abs
import kotlin.math.roundToInt

class WallpaperPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (bitmap == null || width <= 0 || height <= 0) {
                    return false
                }

                if (layoutState.mode != LayoutMode.CROP) {
                    switchToFreeScalePreservingSize()
                }

                updateLayoutState(
                    layoutState.copy(
                        userScale = (
                            layoutState.userScale * detector.scaleFactor.toDouble()
                        ).coerceIn(
                            LayoutCalculator.MIN_CROP_USER_SCALE,
                            LayoutCalculator.MAX_CROP_USER_SCALE,
                        ),
                    ),
                )
                return true
            }
        },
    )

    private var bitmap: Bitmap? = null
    private var logicalSourceWidth = 0
    private var logicalSourceHeight = 0
    private var targetWidth = 9
    private var targetHeight = 20

    private var lastX = 0f
    private var lastY = 0f

    var onLayoutStateChanged: ((WallpaperLayoutState) -> Unit)? = null

    var layoutState: WallpaperLayoutState = WallpaperLayoutState()
        private set

    fun setTargetSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        targetWidth = width
        targetHeight = height
        requestLayout()
    }

    fun setBitmap(
        value: Bitmap,
        sourceWidth: Int = value.width,
        sourceHeight: Int = value.height,
    ) {
        val old = bitmap
        bitmap = value
        logicalSourceWidth = sourceWidth.coerceAtLeast(1)
        logicalSourceHeight = sourceHeight.coerceAtLeast(1)
        if (old !== value && old != null && !old.isRecycled) {
            old.recycle()
        }
        invalidate()
    }

    fun setLayoutState(value: WallpaperLayoutState) {
        updateLayoutState(
            value.copy(
                userScale = value.userScale.coerceIn(
                    LayoutCalculator.MIN_CROP_USER_SCALE,
                    LayoutCalculator.MAX_CROP_USER_SCALE,
                ),
                offsetXNormalized = value.offsetXNormalized.coerceIn(-1.0, 1.0),
                offsetYNormalized = value.offsetYNormalized.coerceIn(-1.0, 1.0),
                blurRadius = value.blurRadius.coerceIn(
                    0,
                    WallpaperLayoutState.MAX_BLUR_RADIUS,
                ),
                backgroundImageAlpha = value.backgroundImageAlpha.coerceIn(0, 255),
            ),
            notify = false,
        )
    }

    fun showWholeImage() {
        updateLayoutState(
            layoutState.copy(
                mode = LayoutMode.CONTAIN,
                userScale = 1.0,
                offsetXNormalized = 0.0,
                offsetYNormalized = 0.0,
            ),
        )
    }

    fun setUserScale(scale: Double) {
        updateLayoutState(
            layoutState.copy(
                userScale = scale.coerceIn(
                    LayoutCalculator.MIN_CROP_USER_SCALE,
                    LayoutCalculator.MAX_CROP_USER_SCALE,
                ),
            ),
        )
    }

    fun setVerticalOffset(normalized: Double) {
        updateLayoutState(
            layoutState.copy(
                offsetYNormalized = normalized.coerceIn(-1.0, 1.0),
            ),
        )
    }

    fun setBackgroundColorValue(color: Int) {
        updateLayoutState(layoutState.copy(backgroundColor = color))
    }

    fun setBackgroundMode(mode: BackgroundMode) {
        updateLayoutState(layoutState.copy(backgroundMode = mode))
    }

    fun setBlurRadius(radius: Int) {
        updateLayoutState(
            layoutState.copy(
                blurRadius = radius.coerceIn(
                    0,
                    WallpaperLayoutState.MAX_BLUR_RADIUS,
                ),
            ),
        )
    }

    fun setBackgroundImageAlpha(alpha: Int) {
        updateLayoutState(
            layoutState.copy(
                backgroundImageAlpha = alpha.coerceIn(0, 255),
            ),
        )
    }

    fun resetCurrentMode() {
        updateLayoutState(
            layoutState.copy(
                userScale = 1.0,
                offsetXNormalized = 0.0,
                offsetYNormalized = 0.0,
            ),
        )
    }

    fun release() {
        bitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        bitmap = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        val aspect = targetWidth.toDouble() / targetHeight.toDouble()

        var measuredWidth = maxWidth
        var measuredHeight = (measuredWidth / aspect)
            .roundToInt()
            .coerceAtLeast(1)

        if (measuredHeight > maxHeight) {
            measuredHeight = maxHeight
            measuredWidth = (measuredHeight * aspect)
                .roundToInt()
                .coerceAtLeast(1)
        }

        setMeasuredDimension(
            measuredWidth.coerceAtMost(maxWidth),
            measuredHeight.coerceAtMost(maxHeight),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val source = bitmap
        if (source == null || source.isRecycled) {
            canvas.drawColor(layoutState.backgroundColor)
            canvas.drawText(
                "画像を読み込み中…",
                width / 2f,
                height / 2f,
                textPaint,
            )
            return
        }

        BackgroundRenderer.draw(
            canvas = canvas,
            source = source,
            destination = RectF(0f, 0f, width.toFloat(), height.toFloat()),
            layout = layoutState,
        )

        val transform = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = logicalSourceWidth.coerceAtLeast(1),
                sourceHeight = logicalSourceHeight.coerceAtLeast(1),
                targetWidth = width.coerceAtLeast(1),
                targetHeight = height.coerceAtLeast(1),
                mode = layoutState.mode,
                userScale = layoutState.userScale,
                offsetXNormalized = layoutState.offsetXNormalized,
                offsetYNormalized = layoutState.offsetYNormalized,
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    panBy(
                        deltaX = event.x - lastX,
                        deltaY = event.y - lastY,
                    )
                }
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun switchToFreeScalePreservingSize() {
        if (logicalSourceWidth <= 0 || logicalSourceHeight <= 0 ||
            width <= 0 || height <= 0
        ) {
            updateLayoutState(layoutState.copy(mode = LayoutMode.CROP))
            return
        }

        val current = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = logicalSourceWidth,
                sourceHeight = logicalSourceHeight,
                targetWidth = width,
                targetHeight = height,
                mode = LayoutMode.CONTAIN,
                userScale = 1.0,
                offsetXNormalized = layoutState.offsetXNormalized,
                offsetYNormalized = layoutState.offsetYNormalized,
            ),
        )
        val fillScale = kotlin.math.max(
            width.toDouble() / logicalSourceWidth.toDouble(),
            height.toDouble() / logicalSourceHeight.toDouble(),
        )
        val initialUserScale = (
            current.scale / fillScale
        ).coerceIn(
            LayoutCalculator.MIN_CROP_USER_SCALE,
            LayoutCalculator.MAX_CROP_USER_SCALE,
        )

        updateLayoutState(
            layoutState.copy(
                mode = LayoutMode.CROP,
                userScale = initialUserScale,
            ),
        )
    }

    private fun panBy(
        deltaX: Float,
        deltaY: Float,
    ) {
        val source = bitmap ?: return
        if (width <= 0 || height <= 0) return

        val transform = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = logicalSourceWidth.coerceAtLeast(1),
                sourceHeight = logicalSourceHeight.coerceAtLeast(1),
                targetWidth = width,
                targetHeight = height,
                mode = layoutState.mode,
                userScale = layoutState.userScale,
                offsetXNormalized = layoutState.offsetXNormalized,
                offsetYNormalized = layoutState.offsetYNormalized,
            ),
        )

        val travelX = abs(width - transform.renderedWidth) / 2.0
        val travelY = abs(height - transform.renderedHeight) / 2.0

        val nextX = if (travelX > 0.0) {
            layoutState.offsetXNormalized + deltaX / travelX
        } else {
            0.0
        }
        val nextY = if (travelY > 0.0) {
            layoutState.offsetYNormalized + deltaY / travelY
        } else {
            0.0
        }

        updateLayoutState(
            layoutState.copy(
                offsetXNormalized = nextX.coerceIn(-1.0, 1.0),
                offsetYNormalized = nextY.coerceIn(-1.0, 1.0),
            ),
        )
    }

    private fun updateLayoutState(
        value: WallpaperLayoutState,
        notify: Boolean = true,
    ) {
        layoutState = value
        invalidate()
        if (notify) {
            onLayoutStateChanged?.invoke(value)
        }
    }
}
