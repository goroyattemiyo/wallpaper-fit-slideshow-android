package io.github.goroyattemiyo.wallpaperfitslideshow.core

import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import kotlin.random.Random

object SlideshowSelector {
    fun selectNext(
        items: List<WallpaperItem>,
        currentItemId: String?,
        orderMode: OrderMode,
        random: Random = Random.Default,
    ): WallpaperItem? {
        val enabled = items
            .asSequence()
            .filter { it.enabled }
            .sortedBy { it.order }
            .toList()

        if (enabled.isEmpty()) {
            return null
        }

        return when (orderMode) {
            OrderMode.SEQUENTIAL -> selectSequential(enabled, currentItemId)
            OrderMode.RANDOM -> selectRandom(enabled, currentItemId, random)
        }
    }

    private fun selectSequential(
        enabled: List<WallpaperItem>,
        currentItemId: String?,
    ): WallpaperItem {
        val currentIndex = enabled.indexOfFirst { it.id == currentItemId }
        if (currentIndex < 0) {
            return enabled.first()
        }
        return enabled[(currentIndex + 1) % enabled.size]
    }

    private fun selectRandom(
        enabled: List<WallpaperItem>,
        currentItemId: String?,
        random: Random,
    ): WallpaperItem {
        if (enabled.size == 1) {
            return enabled.first()
        }

        val candidates = enabled.filterNot { it.id == currentItemId }
        if (candidates.isEmpty()) {
            return enabled.first()
        }
        return candidates[random.nextInt(candidates.size)]
    }
}
