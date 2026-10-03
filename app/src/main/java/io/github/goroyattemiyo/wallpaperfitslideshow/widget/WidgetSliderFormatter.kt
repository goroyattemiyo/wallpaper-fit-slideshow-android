package io.github.goroyattemiyo.wallpaperfitslideshow.widget

import kotlin.math.roundToInt

internal object WidgetSliderFormatter {
    private const val DEFAULT_STEPS = 10

    fun emptyBar(steps: Int = DEFAULT_STEPS): String =
        "━".repeat(steps.coerceAtLeast(1) + 1)

    fun bar(
        value: Double,
        min: Double,
        max: Double,
        steps: Int = DEFAULT_STEPS,
    ): String {
        require(min.isFinite() && max.isFinite() && max > min)
        require(value.isFinite())

        val safeSteps = steps.coerceAtLeast(1)
        val ratio = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        val knob = (ratio * safeSteps).roundToInt()

        return buildString(safeSteps + 1) {
            for (index in 0..safeSteps) {
                append(if (index == knob) '●' else '━')
            }
        }
    }
}
