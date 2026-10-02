package io.github.goroyattemiyo.wallpaperfitslideshow.core

import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlideshowSelectorTest {
    private val items = listOf(
        WallpaperItem(id = "a", uri = "content://a", displayName = "A", order = 0),
        WallpaperItem(id = "b", uri = "content://b", displayName = "B", order = 1),
        WallpaperItem(id = "c", uri = "content://c", displayName = "C", order = 2),
    )

    @Test
    fun sequentialStartsAtFirstWhenCurrentIsUnknown() {
        val result = SlideshowSelector.selectNext(
            items = items,
            currentItemId = null,
            orderMode = OrderMode.SEQUENTIAL,
        )

        assertEquals("a", result?.id)
    }

    @Test
    fun sequentialAdvancesAndWraps() {
        assertEquals(
            "b",
            SlideshowSelector.selectNext(items, "a", OrderMode.SEQUENTIAL)?.id,
        )
        assertEquals(
            "a",
            SlideshowSelector.selectNext(items, "c", OrderMode.SEQUENTIAL)?.id,
        )
    }

    @Test
    fun sequentialCandidatesDoNotReturnCurrentAgain() {
        val ids = SlideshowSelector.candidates(
            items = items,
            currentItemId = "b",
            orderMode = OrderMode.SEQUENTIAL,
        ).map { it.id }

        assertEquals(listOf("c", "a"), ids)
    }

    @Test
    fun disabledItemsAreSkipped() {
        val result = SlideshowSelector.selectNext(
            items = items.map { if (it.id == "b") it.copy(enabled = false) else it },
            currentItemId = "a",
            orderMode = OrderMode.SEQUENTIAL,
        )

        assertEquals("c", result?.id)
    }

    @Test
    fun randomAvoidsImmediateRepeatWhenMultipleItemsExist() {
        repeat(20) { seed ->
            val result = SlideshowSelector.selectNext(
                items = items,
                currentItemId = "b",
                orderMode = OrderMode.RANDOM,
                random = Random(seed),
            )

            assertNotEquals("b", result?.id)
        }
    }

    @Test
    fun singleImageCanRepeat() {
        val only = items.take(1)
        assertEquals(
            "a",
            SlideshowSelector.selectNext(
                only,
                "a",
                OrderMode.SEQUENTIAL,
            )?.id,
        )
    }

    @Test
    fun emptyEnabledPlaylistReturnsNull() {
        val result = SlideshowSelector.selectNext(
            items = items.map { it.copy(enabled = false) },
            currentItemId = null,
            orderMode = OrderMode.SEQUENTIAL,
        )

        assertNull(result)
    }
}
