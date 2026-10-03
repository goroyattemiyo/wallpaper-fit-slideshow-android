package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import android.content.Context
import io.github.goroyattemiyo.wallpaperfitslideshow.core.SlideshowSelector
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget

class WallpaperOperationService(
    context: Context,
) {
    private val settingsStore = SettingsStore(context.applicationContext)
    private val renderer = WallpaperRenderer(context.applicationContext)
    private val applier = WallpaperApplier(context.applicationContext)
    private val targetSizeResolver = WallpaperTargetSizeResolver(context.applicationContext)

    fun applyNext(requireSlideshowEnabled: Boolean): WallpaperOperationResult =
        WallpaperOperationGate.tryRun {
            performApplyNext(requireSlideshowEnabled)
        } ?: WallpaperOperationResult.Busy

    fun applyItem(
        itemId: String,
        target: WallpaperTarget,
    ): WallpaperOperationResult {
        require(target != WallpaperTarget.BOTH) {
            "Direct item target must be HOME or LOCK."
        }

        return WallpaperOperationGate.tryRun {
            val settings = settingsStore.load()
            val item = settings.items.firstOrNull { it.id == itemId }
                ?: return@tryRun WallpaperOperationResult.NoImages
            applyExactItem(item, target)
        } ?: WallpaperOperationResult.Busy
    }

    private fun performApplyNext(
        requireSlideshowEnabled: Boolean,
    ): WallpaperOperationResult {
        val initial = settingsStore.load()
        if (requireSlideshowEnabled && !initial.slideshowEnabled) {
            return WallpaperOperationResult.Disabled
        }

        val minimumCount = if (requireSlideshowEnabled) 2 else 1
        val targets = listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
            .filter { target ->
                initial.items.count { it.isEnabledFor(target) } >= minimumCount
            }

        if (targets.isEmpty()) {
            persistError(
                if (requireSlideshowEnabled) {
                    "ホームまたはロックに2枚以上の対象画像が必要です。"
                } else {
                    "スライドショー対象の画像がありません。"
                },
            )
            return WallpaperOperationResult.NoImages
        }

        val appliedTargets = linkedSetOf<WallpaperTarget>()
        val warnings = mutableListOf<String>()

        for (target in targets) {
            when (val result = applyNextForTarget(target, requireSlideshowEnabled)) {
                is TargetApplyResult.Applied -> appliedTargets += target
                is TargetApplyResult.Failed -> warnings +=
                    "${targetLabel(target)}: ${result.message}"
                TargetApplyResult.Disabled ->
                    return WallpaperOperationResult.Disabled
            }
        }

        return if (appliedTargets.isNotEmpty()) {
            settingsStore.update {
                it.copy(
                    lastError = warnings.takeIf { values -> values.isNotEmpty() }
                        ?.joinToString(" / "),
                )
            }
            WallpaperOperationResult.Success(
                appliedTargets = appliedTargets,
                warnings = warnings,
            )
        } else {
            val message = warnings.joinToString(" / ")
                .ifBlank { "使用できる画像がありません。" }
            persistError(message)
            WallpaperOperationResult.Failure(message)
        }
    }

    private fun applyNextForTarget(
        target: WallpaperTarget,
        requireSlideshowEnabled: Boolean,
    ): TargetApplyResult {
        val initial = settingsStore.load()
        val candidates = SlideshowSelector.candidates(
            items = initial.items,
            currentItemId = initial.currentItemIdFor(target),
            orderMode = initial.orderMode,
            target = target,
        )
        var lastRenderError: String? = null

        for (candidate in candidates) {
            val rendered = try {
                renderer.render(
                    item = candidate,
                    layout = candidate.layoutFor(target),
                    geometry = targetSizeResolver.resolve(),
                )
            } catch (exception: WallpaperRenderer.RenderException) {
                lastRenderError = exception.message ?: "画像を処理できませんでした。"
                continue
            }

            val latest = settingsStore.load()
            if (requireSlideshowEnabled && !latest.slideshowEnabled) {
                recycle(rendered)
                return TargetApplyResult.Disabled
            }
            if (latest.items.none {
                    it.id == candidate.id && it.isEnabledFor(target)
                }
            ) {
                recycle(rendered)
                continue
            }

            try {
                applier.apply(
                    bitmap = rendered.bitmap,
                    visibleCropHint = rendered.visibleCropHint,
                    target = target,
                )
            } catch (exception: WallpaperApplier.ApplyException) {
                return TargetApplyResult.Failed(
                    exception.message ?: "壁紙を適用できませんでした。",
                )
            } finally {
                recycle(rendered)
            }

            val now = System.currentTimeMillis()
            settingsStore.update { current ->
                when (target) {
                    WallpaperTarget.HOME -> current.copy(
                        currentHomeItemId = candidate.id,
                        lastSuccessEpochMillis = now,
                    )
                    WallpaperTarget.LOCK -> current.copy(
                        currentLockItemId = candidate.id,
                        lastSuccessEpochMillis = now,
                    )
                    WallpaperTarget.BOTH -> current
                }
            }
            return TargetApplyResult.Applied(candidate.id)
        }

        return TargetApplyResult.Failed(
            lastRenderError ?: "使用できる画像がありません。",
        )
    }

    private fun applyExactItem(
        item: WallpaperItem,
        target: WallpaperTarget,
    ): WallpaperOperationResult {
        val rendered = try {
            renderer.render(
                item = item,
                layout = item.layoutFor(target),
                geometry = targetSizeResolver.resolve(),
            )
        } catch (exception: WallpaperRenderer.RenderException) {
            val message = exception.message ?: "画像を処理できませんでした。"
            persistError(message)
            return WallpaperOperationResult.Failure(message)
        }

        val latest = settingsStore.load()
        if (latest.items.none { it.id == item.id }) {
            recycle(rendered)
            return WallpaperOperationResult.NoImages
        }

        try {
            applier.apply(
                bitmap = rendered.bitmap,
                visibleCropHint = rendered.visibleCropHint,
                target = target,
            )
        } catch (exception: WallpaperApplier.ApplyException) {
            val message = exception.message ?: "壁紙を適用できませんでした。"
            persistError(message)
            return WallpaperOperationResult.Failure(message)
        } finally {
            recycle(rendered)
        }

        val now = System.currentTimeMillis()
        settingsStore.update { current ->
            when (target) {
                WallpaperTarget.HOME -> current.copy(
                    currentHomeItemId = item.id,
                    lastSuccessEpochMillis = now,
                    lastError = null,
                )
                WallpaperTarget.LOCK -> current.copy(
                    currentLockItemId = item.id,
                    lastSuccessEpochMillis = now,
                    lastError = null,
                )
                WallpaperTarget.BOTH -> current
            }
        }
        return WallpaperOperationResult.Success(setOf(target))
    }

    private fun recycle(rendered: RenderedWallpaper) {
        if (!rendered.bitmap.isRecycled) {
            rendered.bitmap.recycle()
        }
    }

    private fun persistError(message: String) {
        settingsStore.update {
            it.copy(lastError = message)
        }
    }

    private fun targetLabel(target: WallpaperTarget): String =
        when (target) {
            WallpaperTarget.HOME -> "ホーム"
            WallpaperTarget.LOCK -> "ロック"
            WallpaperTarget.BOTH -> "両方"
        }

    private sealed class TargetApplyResult {
        data class Applied(val itemId: String) : TargetApplyResult()
        data class Failed(val message: String) : TargetApplyResult()
        data object Disabled : TargetApplyResult()
    }
}

sealed class WallpaperOperationResult {
    data class Success(
        val appliedTargets: Set<WallpaperTarget>,
        val warnings: List<String> = emptyList(),
    ) : WallpaperOperationResult()
    data class Failure(val message: String) : WallpaperOperationResult()
    data object Busy : WallpaperOperationResult()
    data object Disabled : WallpaperOperationResult()
    data object NoImages : WallpaperOperationResult()
}
