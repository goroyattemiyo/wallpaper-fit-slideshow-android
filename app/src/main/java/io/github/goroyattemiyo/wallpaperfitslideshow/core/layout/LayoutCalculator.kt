package io.github.goroyattemiyo.wallpaperfitslideshow.core.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class LayoutMode {
    CONTAIN,
    CROP,
}

data class LayoutRequest(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val mode: LayoutMode,
    val userScale: Double = 1.0,
    val offsetXNormalized: Double = 0.0,
    val offsetYNormalized: Double = 0.0,
)

data class LayoutTransform(
    val scale: Double,
    val translationX: Double,
    val translationY: Double,
    val renderedWidth: Double,
    val renderedHeight: Double,
)

object LayoutCalculator {
    const val MIN_CROP_USER_SCALE = 0.2
    const val MAX_CROP_USER_SCALE = 5.0

    fun calculate(request: LayoutRequest): LayoutTransform {
        validate(request)

        val sourceWidth = request.sourceWidth.toDouble()
        val sourceHeight = request.sourceHeight.toDouble()
        val targetWidth = request.targetWidth.toDouble()
        val targetHeight = request.targetHeight.toDouble()

        val scale = when (request.mode) {
            LayoutMode.CONTAIN -> min(
                1.0,
                min(targetWidth / sourceWidth, targetHeight / sourceHeight),
            )

            LayoutMode.CROP -> {
                val fillScale = max(
                    targetWidth / sourceWidth,
                    targetHeight / sourceHeight,
                )
                fillScale * request.userScale.coerceIn(
                    MIN_CROP_USER_SCALE,
                    MAX_CROP_USER_SCALE,
                )
            }
        }

        return positionedTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scale = scale,
            offsetXNormalized = request.offsetXNormalized,
            offsetYNormalized = request.offsetYNormalized,
        )
    }

    private fun positionedTransform(
        sourceWidth: Double,
        sourceHeight: Double,
        targetWidth: Double,
        targetHeight: Double,
        scale: Double,
        offsetXNormalized: Double,
        offsetYNormalized: Double,
    ): LayoutTransform {
        val renderedWidth = sourceWidth * scale
        val renderedHeight = sourceHeight * scale
        val centeredX = (targetWidth - renderedWidth) / 2.0
        val centeredY = (targetHeight - renderedHeight) / 2.0
        val travelX = abs(targetWidth - renderedWidth) / 2.0
        val travelY = abs(targetHeight - renderedHeight) / 2.0

        return LayoutTransform(
            scale = scale,
            translationX = centeredX +
                offsetXNormalized.coerceIn(-1.0, 1.0) * travelX,
            translationY = centeredY +
                offsetYNormalized.coerceIn(-1.0, 1.0) * travelY,
            renderedWidth = renderedWidth,
            renderedHeight = renderedHeight,
        )
    }

    private fun validate(request: LayoutRequest) {
        require(request.sourceWidth > 0 && request.sourceHeight > 0) {
            "Source dimensions must be positive."
        }
        require(request.targetWidth > 0 && request.targetHeight > 0) {
            "Target dimensions must be positive."
        }
        require(request.userScale.isFinite() && request.userScale > 0.0) {
            "userScale must be finite and positive."
        }
        require(
            request.offsetXNormalized.isFinite() &&
                request.offsetYNormalized.isFinite(),
        ) {
            "Offsets must be finite."
        }
    }
}
