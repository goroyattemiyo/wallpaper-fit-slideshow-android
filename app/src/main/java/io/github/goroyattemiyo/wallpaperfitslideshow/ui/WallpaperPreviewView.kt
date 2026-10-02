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
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import kotlin.math.max
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
                if (layoutState.mode != LayoutMode.CROP) return false
                layoutState = layoutState.copy(
                    userScale = (
                        layoutState.userScale * detector.scaleFactor.toDouble()
                    ).coerceIn(MIN_USER_SCALE, MAX_USER_SCALE),
                )
                invalidate()
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
        layoutState = value.copy(
            userScale = value.userScale.coerceIn(MIN_USER_SCALE, MAX_USER_SCALE),
            offsetXNormalized = value.offsetXNormalized.coerceIn(-1.0, 1.0),
            offsetYNormalized = value.offsetYNormalized.coerceIn(-1.0, 1.0),
        )
        invalidate()
    }

    fun setMode(mode: LayoutMode) {
        layoutState = layoutState.copy(mode = mode)
        invalidate()
    }

    fun setBackgroundColorValue(color: Int) {
        layoutState = layoutState.copy(backgroundColor = color)
        invalidate()
    }

    fun resetCurrentMode() {
        layoutState = layoutState.copy(
            userScale = 1.0,
            offsetXNormalized = 0.0,
            offsetYNormalized = 0.0,
        )
        invalidate()
    }

    fun release() {
        bitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        bitmap = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val desiredHeight = (
            measuredWidth.toDouble() * targetHeight.toDouble() / targetWidth.toDouble()
        ).roundToInt().coerceAtLeast(1)

        setMeasuredDimension(
            resolveSize(measuredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(layoutState.backgroundColor)

        val source = bitmap
        if (source == null || source.isRecycled) {
            canvas.drawText(
                "画像を読み込み中…",
                width / 2f,
                height / 2f,
                textPaint,
            )
            return
        }

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

        if (layoutState.mode != LayoutMode.CROP) {
            return true
        }

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

    private fun panBy(deltaX: Float, deltaY: Float) {
        val source = bitmap ?: return
        if (width <= 0 || height <= 0) return

        val transform = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = logicalSourceWidth.coerceAtLeast(1),
                sourceHeight = logicalSourceHeight.coerceAtLeast(1),
                targetWidth = width,
                targetHeight = height,
                mode = LayoutMode.CROP,
                userScale = layoutState.userScale,
                offsetXNormalized = layoutState.offsetXNormalized,
                offsetYNormalized = layoutState.offsetYNormalized,
            ),
        )

        val maxPanX = max(0.0, (transform.renderedWidth - width) / 2.0)
        val maxPanY = max(0.0, (transform.renderedHeight - height) / 2.0)

        val nextX = if (maxPanX > 0.0) {
            layoutState.offsetXNormalized + deltaX / maxPanX
        } else {
            0.0
        }
        val nextY = if (maxPanY > 0.0) {
            layoutState.offsetYNormalized + deltaY / maxPanY
        } else {
            0.0
        }

        layoutState = layoutState.copy(
            offsetXNormalized = nextX.coerceIn(-1.0, 1.0),
            offsetYNormalized = nextY.coerceIn(-1.0, 1.0),
        )
        invalidate()
    }

    companion object {
        private const val MIN_USER_SCALE = 1.0
        private const val MAX_USER_SCALE = 5.0
    }
}
