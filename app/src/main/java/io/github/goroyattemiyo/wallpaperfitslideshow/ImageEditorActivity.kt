package io.github.goroyattemiyo.wallpaperfitslideshow

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.BackgroundMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import io.github.goroyattemiyo.wallpaperfitslideshow.ui.WallpaperPreviewView
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.SourceBitmapLoader
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationResult
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperTargetSizeResolver
import io.github.goroyattemiyo.wallpaperfitslideshow.widget.WallpaperControlWidgetProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageEditorActivity : Activity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var preview: WallpaperPreviewView
    private lateinit var titleText: TextView
    private lateinit var gestureStatusText: TextView
    private lateinit var applyButton: Button
    private lateinit var operationService: WallpaperOperationService

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var itemId: String? = null
    private var suppressBackSave = false
    private var homeLayoutDraft = WallpaperLayoutState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        settingsStore = SettingsStore(applicationContext)
        operationService = WallpaperOperationService(applicationContext)
        preview = findViewById(R.id.wallpaper_preview)
        titleText = findViewById(R.id.editor_title)
        gestureStatusText = findViewById(R.id.gesture_status)
        applyButton = findViewById(R.id.apply_button)

        itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        val item = settingsStore.load().items.firstOrNull { it.id == itemId }
        if (item == null) {
            Toast.makeText(this, "画像設定が見つかりません。", Toast.LENGTH_LONG).show()
            suppressBackSave = true
            finish()
            return
        }

        titleText.text = item.displayName
        homeLayoutDraft = item.homeLayout

        val geometry = WallpaperTargetSizeResolver(applicationContext).resolve()
        preview.setTargetSize(
            geometry.visibleWidth,
            geometry.visibleHeight,
        )
        preview.onLayoutStateChanged = { state ->
            homeLayoutDraft = state
            updateGestureStatus(state)
        }
        preview.setLayoutState(homeLayoutDraft)
        preview.setWallpaperBlurRadius(
            settingsStore.load().homeWallpaperBlurRadius,
        )
        updateGestureStatus(homeLayoutDraft)

        findViewById<Button>(R.id.back_button).setOnClickListener {
            saveAndFinish(showToast = false)
        }
        findViewById<Button>(R.id.contain_button).setOnClickListener {
            preview.showWholeImage()
        }
        findViewById<Button>(R.id.background_button).setOnClickListener {
            showBackgroundSettingsDialog()
        }
        findViewById<Button>(R.id.reset_button).setOnClickListener {
            preview.resetCurrentMode()
        }
        findViewById<Button>(R.id.save_button).setOnClickListener {
            saveAndFinish(showToast = true)
        }
        applyButton.setOnClickListener {
            saveAndApply()
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

    override fun onStop() {
        if (!suppressBackSave) {
            saveLayout()
        }
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        preview.release()
        super.onDestroy()
    }



    private fun updateGestureStatus(state: WallpaperLayoutState) {
        gestureStatusText.text = buildString {
            append(
                if (state.mode == LayoutMode.CROP) {
                    "調整中  "
                } else {
                    "全体表示  "
                },
            )
            if (state.mode == LayoutMode.CROP) {
                append((state.userScale * 100).toInt())
                append("%  /  ")
            }
            append("指で移動・ピンチで縮小/拡大")
        }
    }

    private fun saveLayout(): String? {
        val id = itemId ?: return null
        settingsStore.update { settings ->
            settings.copy(
                items = settings.items.map { item ->
                    if (item.id == id) {
                        item.copy(homeLayout = homeLayoutDraft)
                    } else {
                        item
                    }
                },
            )
        }
        WallpaperControlWidgetProvider.updateAll(applicationContext)
        return id
    }

    private fun saveAndFinish(showToast: Boolean) {
        if (suppressBackSave || saveLayout() == null) {
            finish()
            return
        }

        if (showToast) {
            Toast.makeText(this, "表示設定を保存しました。", Toast.LENGTH_SHORT).show()
        }
        suppressBackSave = true
        finish()
    }

    private fun saveAndApply() {
        val id = saveLayout() ?: return
        Toast.makeText(
            this,
            "ホーム画面へ適用しています…",
            Toast.LENGTH_SHORT,
        ).show()

        executor.execute {
            val result = operationService.applyItem(
                id,
                WallpaperTarget.HOME,
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                val message = when (result) {
                    is WallpaperOperationResult.Success ->
                        "この画像をホーム画面へ適用しました。"
                    is WallpaperOperationResult.Failure -> result.message
                    WallpaperOperationResult.Busy -> "別の壁紙変更処理が実行中です。"
                    WallpaperOperationResult.Disabled -> "壁紙変更が無効です。"
                    WallpaperOperationResult.NoImages -> "画像設定が見つかりません。"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showBackgroundSettingsDialog() {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_background_settings,
            null,
        )
        val modeSpinner = dialogView.findViewById<Spinner>(
            R.id.background_mode_spinner,
        )
        val colorButton = dialogView.findViewById<Button>(
            R.id.background_color_dialog_button,
        )
        val blurLabel = dialogView.findViewById<TextView>(
            R.id.dialog_blur_label,
        )
        val blurSeek = dialogView.findViewById<SeekBar>(
            R.id.dialog_blur_seek,
        )
        val transparencyLabel = dialogView.findViewById<TextView>(
            R.id.dialog_transparency_label,
        )
        val transparencySeek = dialogView.findViewById<SeekBar>(
            R.id.dialog_transparency_seek,
        )

        modeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("単色", "同じ画像をぼかす"),
        ).also {
            it.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item,
            )
        }
        modeSpinner.setSelection(
            if (preview.layoutState.backgroundMode == BackgroundMode.BLUR) 1 else 0,
            false,
        )

        blurSeek.max = WallpaperLayoutState.MAX_BLUR_RADIUS
        blurSeek.progress = preview.layoutState.blurRadius
        transparencySeek.max = 100
        transparencySeek.progress = backgroundTransparencyPercent()

        fun refreshBackgroundLabels() {
            val blurEnabled = preview.layoutState.backgroundMode == BackgroundMode.BLUR
            blurSeek.isEnabled = blurEnabled
            transparencySeek.isEnabled = blurEnabled
            blurLabel.text = "ぼかし: ${preview.layoutState.blurRadius}"
            transparencyLabel.text =
                "背景画像の透明度: ${backgroundTransparencyPercent()}%"
        }

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                preview.setBackgroundMode(
                    if (position == 1) BackgroundMode.BLUR else BackgroundMode.SOLID,
                )
                refreshBackgroundLabels()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        colorButton.setOnClickListener {
            showBackgroundColorDialog()
        }

        blurSeek.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                preview.setBlurRadius(progress)
                refreshBackgroundLabels()
            },
        )

        transparencySeek.setOnSeekBarChangeListener(
            SimpleSeekListener { progress ->
                val alpha = (
                    255.0 * (100 - progress).toDouble() / 100.0
                ).toInt()
                preview.setBackgroundImageAlpha(alpha)
                refreshBackgroundLabels()
            },
        )

        refreshBackgroundLabels()

        AlertDialog.Builder(this)
            .setTitle("背景設定")
            .setView(dialogView)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun backgroundTransparencyPercent(): Int =
        (
            100.0 -
                preview.layoutState.backgroundImageAlpha.toDouble() * 100.0 / 255.0
        ).toInt().coerceIn(0, 100)

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
    }
}
