package io.github.goroyattemiyo.wallpaperfitslideshow.widget

import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutCalculator
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutRequest
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.SourceBitmapLoader
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperTargetSizeResolver
import kotlin.math.max

class HomeWidgetController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore(appContext)
    private val operationService = WallpaperOperationService(appContext)
    private val sourceLoader = SourceBitmapLoader(appContext)
    private val targetSizeResolver = WallpaperTargetSizeResolver(appContext)

    fun handle(action: String) {
        when (action) {
            WallpaperControlWidgetProvider.ACTION_ZOOM_IN -> adjustZoom(ZOOM_FACTOR)
            WallpaperControlWidgetProvider.ACTION_ZOOM_OUT -> adjustZoom(1.0 / ZOOM_FACTOR)
            WallpaperControlWidgetProvider.ACTION_ZOOM_RESET -> resetZoom()
            WallpaperControlWidgetProvider.ACTION_BLUR_IN -> adjustHomeBlur(1)
            WallpaperControlWidgetProvider.ACTION_BLUR_OUT -> adjustHomeBlur(-1)
        }
    }

    private fun adjustZoom(factor: Double) {
        val snapshot = settingsStore.load()
        val currentId = snapshot.currentHomeItemId ?: return
        val item = snapshot.items.firstOrNull { it.id == currentId } ?: return
        val nextLayout = nextZoomLayout(item.homeLayout, item.uri, factor) ?: return

        settingsStore.update { current ->
            current.copy(
                items = current.items.map { candidate ->
                    if (candidate.id == currentId) {
                        candidate.copy(homeLayout = nextLayout)
                    } else {
                        candidate
                    }
                },
            )
        }
        operationService.applyItem(currentId, WallpaperTarget.HOME)
    }

    private fun resetZoom() {
        val snapshot = settingsStore.load()
        val currentId = snapshot.currentHomeItemId ?: return

        settingsStore.update { current ->
            current.copy(
                items = current.items.map { candidate ->
                    if (candidate.id == currentId) {
                        candidate.copy(
                            homeLayout = candidate.homeLayout.copy(
                                userScale = 1.0,
                            ),
                        )
                    } else {
                        candidate
                    }
                },
            )
        }
        operationService.applyItem(currentId, WallpaperTarget.HOME)
    }

    private fun adjustHomeBlur(delta: Int) {
        val updated = settingsStore.update { current ->
            current.copy(
                homeWallpaperBlurRadius = (
                    current.homeWallpaperBlurRadius + delta
                ).coerceIn(
                    0,
                    AppSettings.MAX_WALLPAPER_BLUR_RADIUS,
                ),
            )
        }

        updated.currentHomeItemId?.let { currentId ->
            operationService.applyItem(currentId, WallpaperTarget.HOME)
        }
    }

    private fun nextZoomLayout(
        layout: WallpaperLayoutState,
        uri: String,
        factor: Double,
    ): WallpaperLayoutState? {
        if (layout.mode == LayoutMode.CROP) {
            return layout.copy(
                userScale = (layout.userScale * factor).coerceIn(
                    LayoutCalculator.MIN_CROP_USER_SCALE,
                    LayoutCalculator.MAX_CROP_USER_SCALE,
                ),
            )
        }

        val info = runCatching {
            sourceLoader.inspect(uri)
        }.getOrNull() ?: return null
        val geometry = targetSizeResolver.resolve()
        val currentTransform = LayoutCalculator.calculate(
            LayoutRequest(
                sourceWidth = info.logicalWidth,
                sourceHeight = info.logicalHeight,
                targetWidth = geometry.visibleWidth,
                targetHeight = geometry.visibleHeight,
                mode = LayoutMode.CONTAIN,
                userScale = 1.0,
                offsetXNormalized = layout.offsetXNormalized,
                offsetYNormalized = layout.offsetYNormalized,
            ),
        )
        val fillScale = max(
            geometry.visibleWidth.toDouble() / info.logicalWidth.toDouble(),
            geometry.visibleHeight.toDouble() / info.logicalHeight.toDouble(),
        )
        val preservingScale = (
            currentTransform.scale / fillScale
        ).coerceIn(
            LayoutCalculator.MIN_CROP_USER_SCALE,
            LayoutCalculator.MAX_CROP_USER_SCALE,
        )

        return layout.copy(
            mode = LayoutMode.CROP,
            userScale = (preservingScale * factor).coerceIn(
                LayoutCalculator.MIN_CROP_USER_SCALE,
                LayoutCalculator.MAX_CROP_USER_SCALE,
            ),
        )
    }

    companion object {
        private const val ZOOM_FACTOR = 1.10
    }
}
