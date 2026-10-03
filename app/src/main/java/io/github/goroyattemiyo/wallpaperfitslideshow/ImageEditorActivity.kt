package io.github.goroyattemiyo.wallpaperfitslideshow

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.BackgroundMode
import io.github.goroyattemiyo.wallpaperfitslideshow.ui.WallpaperPreviewView
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.SourceBitmapLoader
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationResult
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperTargetSizeResolver
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageEditorActivity : Activity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var preview: WallpaperPreviewView
    private lateinit var titleText: TextView
    private lateinit var operationService: WallpaperOperationService
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var verticalSeekBar: SeekBar
    private lateinit var blurSeekBar: SeekBar
    private lateinit var transparencySeekBar: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var verticalLabel: TextView
    private lateinit var blurLabel: TextView
    private lateinit var transparencyLabel: TextView
    private var bindingEditorControls = false

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var itemId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        settingsStore = SettingsStore(applicationContext)
        operationService = WallpaperOperationService(applicationContext)
        preview = findViewById(R.id.wallpaper_preview)
        titleText = findViewById(R.id.editor_title)
        zoomSeekBar = findViewById(R.id.zoom_seek)
        verticalSeekBar = findViewById(R.id.vertical_seek)
        blurSeekBar = findViewById(R.id.blur_seek)
        transparencySeekBar = findViewById(R.id.transparency_seek)
        zoomLabel = findViewById(R.id.zoom_label)
        verticalLabel = findViewById(R.id.vertical_label)
        blurLabel = findViewById(R.id.blur_label)
        transparencyLabel = findViewById(R.id.transparency_label)

        itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        val item = settingsStore.load().items.firstOrNull { it.id == itemId }
        if (item == null) {
            Toast.makeText(this, "画像設定が見つかりません。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        titleText.text = item.displayName
        preview.setLayoutState(item.layout)
        configureAdjustmentControls()
        syncAdjustmentControls(preview.layoutState)

        val geometry = WallpaperTargetSizeResolver(applicationContext).resolve()
        preview.setTargetSize(
            geometry.visibleWidth,
            geometry.visibleHeight,
        )

        findViewById<Button>(R.id.contain_button).setOnClickListener {
            preview.setMode(LayoutMode.CONTAIN)
        }
        findViewById<Button>(R.id.crop_button).setOnClickListener {
            preview.setMode(LayoutMode.CROP)
        }
        findViewById<Button>(R.id.background_button).setOnClickListener {
            showBackgroundStyleDialog()
        }
        findViewById<Button>(R.id.background_color_button).setOnClickListener {
            showBackgroundColorDialog()
        }
        findViewById<Button>(R.id.reset_button).setOnClickListener {
            preview.resetCurrentMode()
        }
        findViewById<Button>(R.id.save_button).setOnClickListener {
            saveAndFinish()
        }
        findViewById<Button>(R.id.apply_button).setOnClickListener {
            saveAndApply()
        }
        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            finish()
        }

        executor.execute {
            val result = runCatching {
                SourceBitmapLoader(applicationContext).load(
                    uriString = item.uri,
                    maxDecodePixels = PREVIEW_MAX_PIXELS,
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    result.getOrNull()?.bitmap?.let { bitmap ->
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                    return@runOnUiThread
                }
                result.onSuccess { loaded ->
                    preview.setBitmap(
                        value = loaded.bitmap,
                        sourceWidth = loaded.info.logicalWidth,
                        sourceHeight = loaded.info.logicalHeight,
                    )
                }.onFailure {
                        Toast.makeText(
                            this,
                            it.message ?: "画像を読み込めませんでした。",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        preview.release()
        super.onDestroy()
    }

    private fun configureAdjustmentControls() {
        zoomSeekBar.max = ZOOM_PROGRESS_MAX
        verticalSeekBar.max = 200
        blurSeekBar.max = io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState.MAX_BLUR_RADIUS
        transparencySeekBar.max = 100

        preview.onLayoutStateChanged = ::syncAdjustmentControls

        zoomSeekBar.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                if (!bindingEditorControls) {
                    preview.setUserScale(
                        ZOOM_MIN + progress.toDouble() / 100.0,
                    )
                }
            },
        )
        verticalSeekBar.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                if (!bindingEditorControls) {
                    preview.setVerticalOffset(
                        (progress - 100).toDouble() / 100.0,
                    )
                }
            },
        )
        blurSeekBar.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                if (!bindingEditorControls) {
                    preview.setBlurRadius(progress)
                }
            },
        )
        transparencySeekBar.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                if (!bindingEditorControls) {
                    val alpha = (
                        255.0 * (100 - progress).toDouble() / 100.0
                    ).toInt()
                    preview.setBackgroundImageAlpha(alpha)
                }
            },
        )
    }

    private fun syncAdjustmentControls(
        state: io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState,
    ) {
        bindingEditorControls = true
        try {
            zoomSeekBar.progress = (
                (state.userScale - ZOOM_MIN) * 100.0
            ).toInt().coerceIn(0, ZOOM_PROGRESS_MAX)
            verticalSeekBar.progress = (
                state.offsetYNormalized * 100.0 + 100.0
            ).toInt().coerceIn(0, 200)
            blurSeekBar.progress = state.blurRadius
            transparencySeekBar.progress = (
                100.0 - state.backgroundImageAlpha.toDouble() * 100.0 / 255.0
            ).toInt().coerceIn(0, 100)

            zoomLabel.text = "倍率: ${(state.userScale * 100).toInt()}%"
            verticalLabel.text = "上下位置: ${verticalPositionLabel(state.offsetYNormalized)}"
            blurLabel.text = "ぼかし: ${state.blurRadius}"
            transparencyLabel.text =
                "背景画像の透明度: ${transparencySeekBar.progress}%"
        } finally {
            bindingEditorControls = false
        }
    }

    private fun verticalPositionLabel(value: Double): String =
        when {
            value <= -0.95 -> "上端"
            value >= 0.95 -> "下端"
            kotlin.math.abs(value) < 0.05 -> "中央"
            value < 0.0 -> "上 ${(-value * 100).toInt()}%"
            else -> "下 ${(value * 100).toInt()}%"
        }

    private fun showBackgroundStyleDialog() {
        val labels = arrayOf(
            "単色",
            "同じ画像をぼかして背景にする",
        )
        AlertDialog.Builder(this)
            .setTitle("背景スタイル")
            .setItems(labels) { _, which ->
                preview.setBackgroundMode(
                    if (which == 1) BackgroundMode.BLUR else BackgroundMode.SOLID,
                )
            }
            .show()
    }

    private fun saveLayout(): String? {
        val id = itemId ?: return null
        settingsStore.update { settings ->
            settings.copy(
                items = settings.items.map { item ->
                    if (item.id == id) {
                        item.copy(layout = preview.layoutState)
                    } else {
                        item
                    }
                },
            )
        }
        return id
    }

    private fun saveAndFinish() {
        if (saveLayout() == null) {
            return
        }
        Toast.makeText(this, "表示設定を保存しました。", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveAndApply() {
        val id = saveLayout() ?: return
        Toast.makeText(this, "壁紙へ適用しています…", Toast.LENGTH_SHORT).show()

        executor.execute {
            val result = operationService.applyItem(id)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                val message = when (result) {
                    is WallpaperOperationResult.Success -> "この画像を壁紙へ適用しました。"
                    is WallpaperOperationResult.Failure -> result.message
                    WallpaperOperationResult.Busy -> "別の壁紙変更処理が実行中です。"
                    WallpaperOperationResult.Disabled -> "壁紙変更が無効です。"
                    WallpaperOperationResult.NoImages -> "画像設定が見つかりません。"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showBackgroundColorDialog() {
        val names = arrayOf(
            "黒",
            "白",
            "ダークグレー",
            "ライトグレー",
            "好きな色をHEX入力",
        )
        val colors = intArrayOf(
            Color.BLACK,
            Color.WHITE,
            Color.rgb(48, 48, 48),
            Color.rgb(224, 224, 224),
        )

        AlertDialog.Builder(this)
            .setTitle("背景色")
            .setItems(names) { _, which ->
                if (which < colors.size) {
                    preview.setBackgroundColorValue(colors[which])
                } else {
                    showHexColorDialog()
                }
            }
            .show()
    }

    private fun showHexColorDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(
                String.format(
                    "#%06X",
                    0xFFFFFF and preview.layoutState.backgroundColor,
                ),
            )
        }

        AlertDialog.Builder(this)
            .setTitle("背景色 #RRGGBB")
            .setView(input)
            .setPositiveButton("適用") { _, _ ->
                val value = input.text.toString().trim()
                runCatching { Color.parseColor(value) }
                    .onSuccess(preview::setBackgroundColorValue)
                    .onFailure {
                        Toast.makeText(
                            this,
                            "色は #RRGGBB 形式で入力してください。",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private class SimpleSeekListener(
        private val onProgress: (Int) -> Unit,
    ) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar?,
            progress: Int,
            fromUser: Boolean,
        ) {
            if (fromUser) {
                onProgress(progress)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        private const val PREVIEW_MAX_PIXELS = 2_000_000L
        private const val ZOOM_MIN = 0.2
        private const val ZOOM_PROGRESS_MAX = 480
    }
}
