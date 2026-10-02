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

    fun applyItem(itemId: String): WallpaperOperationResult {
        return WallpaperOperationGate.tryRun {
            val settings = settingsStore.load()
            val item = settings.items.firstOrNull { it.id == itemId && it.enabled }
                ?: return@tryRun WallpaperOperationResult.NoImages

            applyExactItem(item)
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

        val candidates = SlideshowSelector.candidates(
            items = initial.items,
            currentItemId = initial.currentItemId,
            orderMode = initial.orderMode,
        )
        var lastRenderError: String? = null

        for (candidate in candidates) {
            val rendered = try {
                renderer.render(
                    item = candidate,
                    geometry = targetSizeResolver.resolve(),
                )
            } catch (exception: WallpaperRenderer.RenderException) {
                lastRenderError = exception.message ?: "画像を処理できませんでした。"
                continue
            }

            val latest = settingsStore.load()
            if (requireSlideshowEnabled && !latest.slideshowEnabled) {
                if (!rendered.bitmap.isRecycled) {
                    rendered.bitmap.recycle()
                }
                return WallpaperOperationResult.Disabled
            }
            if (latest.items.none { it.id == candidate.id && it.enabled }) {
                if (!rendered.bitmap.isRecycled) {
                    rendered.bitmap.recycle()
                }
                continue
            }

            try {
                applier.apply(
                    bitmap = rendered.bitmap,
                    visibleCropHint = rendered.visibleCropHint,
                    target = latest.target,
                )
            } catch (exception: WallpaperApplier.ApplyException) {
                val message = exception.message ?: "壁紙を適用できませんでした。"
                persistError(message)
                return WallpaperOperationResult.Failure(message)
            } finally {
                if (!rendered.bitmap.isRecycled) {
                    rendered.bitmap.recycle()
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

    private fun applyExactItem(
        item: io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem,
    ): WallpaperOperationResult {
        val rendered = try {
            renderer.render(
                item = item,
                geometry = targetSizeResolver.resolve(),
            )
        } catch (exception: WallpaperRenderer.RenderException) {
            val message = exception.message ?: "画像を処理できませんでした。"
            persistError(message)
            return WallpaperOperationResult.Failure(message)
        }

        val latest = settingsStore.load()
        if (latest.items.none { it.id == item.id && it.enabled }) {
            if (!rendered.bitmap.isRecycled) {
                rendered.bitmap.recycle()
            }
            return WallpaperOperationResult.NoImages
        }

        try {
            applier.apply(
                bitmap = rendered.bitmap,
                visibleCropHint = rendered.visibleCropHint,
                target = latest.target,
            )
        } catch (exception: WallpaperApplier.ApplyException) {
            val message = exception.message ?: "壁紙を適用できませんでした。"
            persistError(message)
            return WallpaperOperationResult.Failure(message)
        } finally {
            if (!rendered.bitmap.isRecycled) {
                rendered.bitmap.recycle()
            }
        }

        val now = System.currentTimeMillis()
        settingsStore.update {
            it.copy(
                currentItemId = item.id,
                lastSuccessEpochMillis = now,
                lastError = null,
            )
        }
        return WallpaperOperationResult.Success(item.id)
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
