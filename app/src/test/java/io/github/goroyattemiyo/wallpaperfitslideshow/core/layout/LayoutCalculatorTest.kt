package io.github.goroyattemiyo.wallpaperfitslideshow.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LayoutCalculatorTest {
    @Test
    fun containShrinksLargeImageAndCentersIt() {
        val result = LayoutCalculator.calculate(
            LayoutRequest(2000, 2000, 1080, 2400, LayoutMode.CONTAIN),
        )

        assertEquals(0.54, result.scale, EPSILON)
        assertEquals(1080.0, result.renderedWidth, EPSILON)
        assertEquals(1080.0, result.renderedHeight, EPSILON)
        assertEquals(0.0, result.translationX, EPSILON)
        assertEquals(660.0, result.translationY, EPSILON)
    }

    @Test
    fun containDoesNotUpscaleSmallImage() {
        val result = LayoutCalculator.calculate(
            LayoutRequest(500, 500, 1080, 2400, LayoutMode.CONTAIN),
        )

        assertEquals(1.0, result.scale, EPSILON)
        assertEquals(500.0, result.renderedWidth, EPSILON)
        assertEquals(500.0, result.renderedHeight, EPSILON)
        assertEquals(290.0, result.translationX, EPSILON)
        assertEquals(950.0, result.translationY, EPSILON)
    }

    @Test
    fun cropFillsTargetWithoutBlankArea() {
        val result = LayoutCalculator.calculate(
            LayoutRequest(1920, 1080, 1080, 2400, LayoutMode.CROP),
        )

        assertEquals(2400.0 / 1080.0, result.scale, EPSILON)
        assertEquals(2400.0, result.renderedHeight, EPSILON)
        assertEquals(0.0, result.translationY, EPSILON)
    }

    @Test
    fun cropClampsHorizontalOffsetToSafeRange() {
        val left = LayoutCalculator.calculate(
            LayoutRequest(
                1920, 1080, 1080, 2400, LayoutMode.CROP,
                offsetXNormalized = -2.0,
            ),
        )
        val right = LayoutCalculator.calculate(
            LayoutRequest(
                1920, 1080, 1080, 2400, LayoutMode.CROP,
                offsetXNormalized = 2.0,
            ),
        )

        assertEquals(1080.0 - left.renderedWidth, left.translationX, EPSILON)
        assertEquals(0.0, right.translationX, EPSILON)
    }

    @Test
    fun cropAppliesUserScaleAboveFillScale() {
        val base = LayoutCalculator.calculate(
            LayoutRequest(1080, 2400, 1080, 2400, LayoutMode.CROP),
        )
        val zoomed = LayoutCalculator.calculate(
            LayoutRequest(
                1080, 2400, 1080, 2400, LayoutMode.CROP,
                userScale = 1.5,
            ),
        )

        assertEquals(base.scale * 1.5, zoomed.scale, EPSILON)
    }

    @Test
    fun cropCanShrinkBelowFillScale() {
        val result = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = 1080,
                sourceHeight = 2400,
                targetWidth = 1080,
                targetHeight = 2400,
                mode = LayoutMode.CROP,
                userScale = 0.5,
            ),
        )

        assertEquals(0.5, result.scale, EPSILON)
        assertEquals(540.0, result.renderedWidth, EPSILON)
        assertEquals(1200.0, result.renderedHeight, EPSILON)
    }

    @Test
    fun containCanMoveInsideUnusedVerticalSpace() {
        val top = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = 1080,
                sourceHeight = 1080,
                targetWidth = 1080,
                targetHeight = 2400,
                mode = LayoutMode.CONTAIN,
                offsetYNormalized = -1.0,
            ),
        )
        val bottom = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = 1080,
                sourceHeight = 1080,
                targetWidth = 1080,
                targetHeight = 2400,
                mode = LayoutMode.CONTAIN,
                offsetYNormalized = 1.0,
            ),
        )

        assertEquals(0.0, top.translationY, EPSILON)
        assertEquals(1320.0, bottom.translationY, EPSILON)
    }

    @Test
    fun invalidDimensionsFailFast() {
        assertThrows(IllegalArgumentException::class.java) {
            LayoutCalculator.calculate(
                LayoutRequest(0, 100, 1080, 2400, LayoutMode.CONTAIN),
            )
        }
    }

    private companion object {
        const val EPSILON = 0.000001
    }
}
