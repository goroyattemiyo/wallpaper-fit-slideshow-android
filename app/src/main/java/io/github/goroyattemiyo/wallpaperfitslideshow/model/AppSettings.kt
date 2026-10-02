package io.github.goroyattemiyo.wallpaperfitslideshow.model

import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode

enum class OrderMode {
    SEQUENTIAL,
    RANDOM,
}

enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH,
}

data class WallpaperLayoutState(
    val mode: LayoutMode = LayoutMode.CONTAIN,
    val userScale: Double = 1.0,
    val offsetXNormalized: Double = 0.0,
    val offsetYNormalized: Double = 0.0,
    val backgroundColor: Int = 0xFF000000.toInt(),
)

data class WallpaperItem(
    val id: String,
    val uri: String,
    val displayName: String,
    val order: Int,
    val enabled: Boolean = true,
    val layout: WallpaperLayoutState = WallpaperLayoutState(),
)

data class AppSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val slideshowEnabled: Boolean = false,
    val intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
    val orderMode: OrderMode = OrderMode.SEQUENTIAL,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val currentItemId: String? = null,
    val lastSuccessEpochMillis: Long? = null,
    val lastError: String? = null,
    val items: List<WallpaperItem> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_INTERVAL_MINUTES = 60L
        const val MIN_INTERVAL_MINUTES = 15L
    }
}
