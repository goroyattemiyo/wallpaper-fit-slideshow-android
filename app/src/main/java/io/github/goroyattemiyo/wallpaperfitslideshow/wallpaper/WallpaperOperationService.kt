package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.core.SlideshowSelector
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore

class WallpaperOperationService(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore(appContext)
    private val renderer = WallpaperRenderer(appContext)
    private val applier = WallpaperApplier(appContext)
    private val targetSizeResolver = WallpaperTargetSizeResolver(appContext)

    fun applyNext(requireSlideshowEnabled: Boolean): WallpaperOperationResult {
        return WallpaperOperationGate.tryRun {
            performApplyNext(requireSlideshowEnabled)
        } ?: WallpaperOperationResult.Busy
    }

    private fun performApplyNext(
        requireSlideshowEnabled: Boolean,
    ): WallpaperOperationResult {
        val initial = settingsStore.load()

        if (requireSlideshowEnabled && !initial.slideshowEnabled) {
            return WallpaperOperationResult.Disabled
        }

        val enabledCount = initial.items.count { it.enabled }
        if (enabledCount == 0) {
            persistError("有効な画像がありません。")
            return WallpaperOperationResult.NoImages
        }

        val attempted = mutableSetOf<String>()
        var currentItemId = initial.currentItemId
        var lastRenderError: String? = null

        while (attempted.size < enabledCount) {
            val candidate = SlideshowSelector.selectNext(
                items = initial.items.filterNot { it.id in attempted },
                currentItemId = currentItemId,
                orderMode = initial.orderMode,
            ) ?: break

            attempted += candidate.id

            val bitmap = try {
                renderer.render(
                    item = candidate,
                    targetSize = targetSizeResolver.resolve(),
                )
            } catch (exception: WallpaperRenderer.RenderException) {
                lastRenderError = exception.message ?: "画像を処理できませんでした。"
                currentItemId = candidate.id
                continue
            }

            try {
                applier.apply(
                    bitmap = bitmap,
                    target = initial.target,
                )
            } catch (exception: WallpaperApplier.ApplyException) {
                val message = exception.message ?: "壁紙を適用できませんでした。"
                persistError(message)
                return WallpaperOperationResult.Failure(message)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            val now = System.currentTimeMillis()
            settingsStore.update {
                it.copy(
                    currentItemId = candidate.id,
                    lastSuccessEpochMillis = now,
                    lastError = null,
                )
            }
            return WallpaperOperationResult.Success(candidate.id)
        }

        val message = lastRenderError ?: "使用できる画像がありません。"
        persistError(message)
        return WallpaperOperationResult.Failure(message)
    }

    private fun persistError(message: String) {
        settingsStore.update {
            it.copy(lastError = message)
        }
    }
}

sealed class WallpaperOperationResult {
    data class Success(val itemId: String) : WallpaperOperationResult()
    data class Failure(val message: String) : WallpaperOperationResult()
    data object Busy : WallpaperOperationResult()
    data object Disabled : WallpaperOperationResult()
    data object NoImages : WallpaperOperationResult()
}
