package io.github.goroyattemiyo.wallpaperfitslideshow.core

import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import kotlin.random.Random

object SlideshowSelector {
    fun selectNext(
        items: List<WallpaperItem>,
        currentItemId: String?,
        orderMode: OrderMode,
        target: WallpaperTarget,
        random: Random = Random.Default,
    ): WallpaperItem? =
        candidates(
            items = items,
            currentItemId = currentItemId,
            orderMode = orderMode,
            target = target,
            random = random,
        ).firstOrNull()

    fun candidates(
        items: List<WallpaperItem>,
        currentItemId: String?,
        orderMode: OrderMode,
        target: WallpaperTarget,
        random: Random = Random.Default,
    ): List<WallpaperItem> {
        require(target != WallpaperTarget.BOTH) {
            "Slideshow target must be HOME or LOCK."
        }

        val enabled = items
            .asSequence()
            .filter { it.isEnabledFor(target) }
            .sortedBy { it.order }
            .toList()

        if (enabled.size <= 1) {
            return enabled
        }

        return when (orderMode) {
            OrderMode.SEQUENTIAL -> sequentialCandidates(enabled, currentItemId)
            OrderMode.RANDOM -> randomCandidates(enabled, currentItemId, random)
        }
    }

    private fun sequentialCandidates(
        enabled: List<WallpaperItem>,
        currentItemId: String?,
    ): List<WallpaperItem> {
        val currentIndex = enabled.indexOfFirst { it.id == currentItemId }
        if (currentIndex < 0) {
            return enabled
        }

        val nextIndex = (currentIndex + 1) % enabled.size
        return buildList(enabled.size - 1) {
            for (offset in 0 until enabled.size - 1) {
                val index = (nextIndex + offset) % enabled.size
                val item = enabled[index]
                if (item.id != currentItemId) {
                    add(item)
                }
            }
        }
    }

    private fun randomCandidates(
        enabled: List<WallpaperItem>,
        currentItemId: String?,
        random: Random,
    ): List<WallpaperItem> {
        val withoutCurrent = enabled.filterNot { it.id == currentItemId }
        return if (withoutCurrent.isEmpty()) {
            enabled
        } else {
            withoutCurrent.shuffled(random)
        }
    }
}
