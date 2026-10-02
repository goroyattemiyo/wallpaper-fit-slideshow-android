package io.github.goroyattemiyo.wallpaperfitslideshow.core.layout

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
    fun calculate(request: LayoutRequest): LayoutTransform {
        validate(request)

        val sourceWidth = request.sourceWidth.toDouble()
        val sourceHeight = request.sourceHeight.toDouble()
        val targetWidth = request.targetWidth.toDouble()
        val targetHeight = request.targetHeight.toDouble()

        return when (request.mode) {
            LayoutMode.CONTAIN -> {
                val fitScale = min(targetWidth / sourceWidth, targetHeight / sourceHeight)
                val scale = min(1.0, fitScale)
                centeredTransform(
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    scale = scale,
                )
            }

            LayoutMode.CROP -> {
                val fillScale = max(targetWidth / sourceWidth, targetHeight / sourceHeight)
                val scale = fillScale * max(1.0, request.userScale)
                val renderedWidth = sourceWidth * scale
                val renderedHeight = sourceHeight * scale
                val centeredX = (targetWidth - renderedWidth) / 2.0
                val centeredY = (targetHeight - renderedHeight) / 2.0
                val maxPanX = max(0.0, (renderedWidth - targetWidth) / 2.0)
                val maxPanY = max(0.0, (renderedHeight - targetHeight) / 2.0)

                LayoutTransform(
                    scale = scale,
                    translationX = centeredX +
                        request.offsetXNormalized.coerceIn(-1.0, 1.0) * maxPanX,
                    translationY = centeredY +
                        request.offsetYNormalized.coerceIn(-1.0, 1.0) * maxPanY,
                    renderedWidth = renderedWidth,
                    renderedHeight = renderedHeight,
                )
            }
        }
    }

    private fun centeredTransform(
        sourceWidth: Double,
        sourceHeight: Double,
        targetWidth: Double,
        targetHeight: Double,
        scale: Double,
    ): LayoutTransform {
        val renderedWidth = sourceWidth * scale
        val renderedHeight = sourceHeight * scale

        return LayoutTransform(
            scale = scale,
            translationX = (targetWidth - renderedWidth) / 2.0,
            translationY = (targetHeight - renderedHeight) / 2.0,
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
