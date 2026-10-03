package io.github.goroyattemiyo.wallpaperfitslideshow.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSliderFormatterTest {
    @Test
    fun bar_places_knob_at_minimum() {
        assertEquals(
            "●━━━━━━━━━━",
            WidgetSliderFormatter.bar(
                value = 0.0,
                min = 0.0,
                max = 30.0,
            ),
        )
    }

    @Test
    fun bar_places_knob_at_middle() {
        assertEquals(
            "━━━━━●━━━━━",
            WidgetSliderFormatter.bar(
                value = 15.0,
                min = 0.0,
                max = 30.0,
            ),
        )
    }

    @Test
    fun bar_places_knob_at_maximum() {
        assertEquals(
            "━━━━━━━━━━●",
            WidgetSliderFormatter.bar(
                value = 30.0,
                min = 0.0,
                max = 30.0,
            ),
        )
    }

    @Test
    fun bar_clamps_outside_range() {
        assertEquals(
            "●━━━━━━━━━━",
            WidgetSliderFormatter.bar(
                value = -50.0,
                min = 0.0,
                max = 30.0,
            ),
        )
        assertEquals(
            "━━━━━━━━━━●",
            WidgetSliderFormatter.bar(
                value = 99.0,
                min = 0.0,
                max = 30.0,
            ),
        )
    }
}
