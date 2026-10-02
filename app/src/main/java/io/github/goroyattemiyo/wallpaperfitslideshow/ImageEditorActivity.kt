package io.github.goroyattemiyo.wallpaperfitslideshow

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.ui.WallpaperPreviewView
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.SourceBitmapLoader
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperTargetSizeResolver
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageEditorActivity : Activity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var preview: WallpaperPreviewView
    private lateinit var titleText: TextView
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var itemId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        settingsStore = SettingsStore(applicationContext)
        preview = findViewById(R.id.wallpaper_preview)
        titleText = findViewById(R.id.editor_title)

        itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        val item = settingsStore.load().items.firstOrNull { it.id == itemId }
        if (item == null) {
            Toast.makeText(this, "画像設定が見つかりません。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        titleText.text = item.displayName
        preview.setLayoutState(item.layout)
        val targetSize = WallpaperTargetSizeResolver(applicationContext).resolve()
        preview.setTargetSize(targetSize.width, targetSize.height)

        findViewById<Button>(R.id.contain_button).setOnClickListener {
            preview.setMode(LayoutMode.CONTAIN)
        }
        findViewById<Button>(R.id.crop_button).setOnClickListener {
            preview.setMode(LayoutMode.CROP)
        }
        findViewById<Button>(R.id.background_button).setOnClickListener {
            showBackgroundColorDialog()
        }
        findViewById<Button>(R.id.reset_button).setOnClickListener {
            preview.resetCurrentMode()
        }
        findViewById<Button>(R.id.save_button).setOnClickListener {
            saveAndFinish()
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

    private fun saveAndFinish() {
        val id = itemId ?: return
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
        Toast.makeText(this, "表示設定を保存しました。", Toast.LENGTH_SHORT).show()
        finish()
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

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        private const val PREVIEW_MAX_PIXELS = 2_000_000L
    }
}
