package io.github.goroyattemiyo.wallpaperfitslideshow.core

import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
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
            target = WallpaperTarget.HOME,
        )
        assertEquals("a", result?.id)
    }

    @Test
    fun sequentialAdvancesAndWraps() {
        assertEquals(
            "b",
            SlideshowSelector.selectNext(
                items,
                "a",
                OrderMode.SEQUENTIAL,
                WallpaperTarget.HOME,
            )?.id,
        )
        assertEquals(
            "a",
            SlideshowSelector.selectNext(
                items,
                "c",
                OrderMode.SEQUENTIAL,
                WallpaperTarget.HOME,
            )?.id,
        )
    }

    @Test
    fun sequentialCandidatesDoNotReturnCurrentAgain() {
        val ids = SlideshowSelector.candidates(
            items = items,
            currentItemId = "b",
            orderMode = OrderMode.SEQUENTIAL,
            target = WallpaperTarget.LOCK,
        ).map { it.id }
        assertEquals(listOf("c", "a"), ids)
    }

    @Test
    fun homeDisabledItemIsSkippedOnlyForHome() {
        val modified = items.map {
            if (it.id == "b") it.copy(homeEnabled = false, lockEnabled = true) else it
        }
        assertEquals(
            "c",
            SlideshowSelector.selectNext(
                modified,
                "a",
                OrderMode.SEQUENTIAL,
                WallpaperTarget.HOME,
            )?.id,
        )
        assertEquals(
            "b",
            SlideshowSelector.selectNext(
                modified,
                "a",
                OrderMode.SEQUENTIAL,
                WallpaperTarget.LOCK,
            )?.id,
        )
    }

    @Test
    fun randomAvoidsImmediateRepeatWhenMultipleItemsExist() {
        repeat(20) { seed ->
            val result = SlideshowSelector.selectNext(
                items = items,
                currentItemId = "b",
                orderMode = OrderMode.RANDOM,
                target = WallpaperTarget.HOME,
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
                WallpaperTarget.HOME,
            )?.id,
        )
    }

    @Test
    fun homeAndLockStateAreIndependent() {
        val item = items.first().copy(
            homeEnabled = true,
            lockEnabled = false,
            homeLayout = WallpaperLayoutState(mode = LayoutMode.CROP, userScale = 1.5),
            lockLayout = WallpaperLayoutState(mode = LayoutMode.CONTAIN, userScale = 1.0),
        )

        assertEquals(true, item.isEnabledFor(WallpaperTarget.HOME))
        assertEquals(false, item.isEnabledFor(WallpaperTarget.LOCK))
        assertEquals(LayoutMode.CROP, item.layoutFor(WallpaperTarget.HOME).mode)
        assertEquals(LayoutMode.CONTAIN, item.layoutFor(WallpaperTarget.LOCK).mode)
        assertEquals(1.5, item.layoutFor(WallpaperTarget.HOME).userScale, 0.0)
        assertEquals(1.0, item.layoutFor(WallpaperTarget.LOCK).userScale, 0.0)
    }

    @Test
    fun emptyTargetPlaylistReturnsNull() {
        val result = SlideshowSelector.selectNext(
            items = items.map { it.copy(homeEnabled = false) },
            currentItemId = null,
            orderMode = OrderMode.SEQUENTIAL,
            target = WallpaperTarget.HOME,
        )
        assertNull(result)
    }
}
