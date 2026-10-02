package io.github.goroyattemiyo.wallpaperfitslideshow

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationResult
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import io.github.goroyattemiyo.wallpaperfitslideshow.work.SlideshowScheduler
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var scheduler: SlideshowScheduler
    private lateinit var operationService: WallpaperOperationService
    private lateinit var imageList: ListView
    private lateinit var statusText: TextView
    private lateinit var orderSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var settings: AppSettings = AppSettings()
    private var selectedItemId: String? = null
    private var bindingControls = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsStore = SettingsStore(applicationContext)
        scheduler = SlideshowScheduler(applicationContext)
        operationService = WallpaperOperationService(applicationContext)

        imageList = findViewById(R.id.image_list)
        statusText = findViewById(R.id.status_text)
        orderSpinner = findViewById(R.id.order_spinner)
        targetSpinner = findViewById(R.id.target_spinner)
        intervalSpinner = findViewById(R.id.interval_spinner)

        configureSpinners()
        configureButtons()
        configureList()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (::settingsStore.isInitialized) {
            refreshUi()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun configureList() {
        imageList.choiceMode = ListView.CHOICE_MODE_SINGLE
        imageList.setOnItemClickListener { _, _, position, _ ->
            selectedItemId = settings.items.getOrNull(position)?.id
            imageList.setItemChecked(position, true)
        }
    }

    private fun configureButtons() {
        findViewById<Button>(R.id.add_images_button).setOnClickListener {
            openImagePicker()
        }
        findViewById<Button>(R.id.edit_image_button).setOnClickListener {
            editSelected()
        }
        findViewById<Button>(R.id.remove_image_button).setOnClickListener {
            removeSelected()
        }
        findViewById<Button>(R.id.start_button).setOnClickListener {
            startSlideshow()
        }
        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopSlideshow()
        }
        findViewById<Button>(R.id.next_button).setOnClickListener {
            applyNextNow()
        }
    }

    private fun configureSpinners() {
        orderSpinner.adapter = simpleSpinnerAdapter(
            listOf("順番", "ランダム"),
        )
        targetSpinner.adapter = simpleSpinnerAdapter(
            listOf("ホーム", "ロック", "両方"),
        )
        intervalSpinner.adapter = simpleSpinnerAdapter(
            INTERVALS.map { it.label },
        )

        orderSpinner.onItemSelectedListener = object : SimpleItemSelectedListener() {
            override fun onSelected(position: Int) {
                if (bindingControls) return
                val value = if (position == 1) OrderMode.RANDOM else OrderMode.SEQUENTIAL
                settings = settingsStore.update { it.copy(orderMode = value) }
            }
        }

        targetSpinner.onItemSelectedListener = object : SimpleItemSelectedListener() {
            override fun onSelected(position: Int) {
                if (bindingControls) return
                val value = when (position) {
                    0 -> WallpaperTarget.HOME
                    1 -> WallpaperTarget.LOCK
                    else -> WallpaperTarget.BOTH
                }
                settings = settingsStore.update { it.copy(target = value) }
            }
        }

        intervalSpinner.onItemSelectedListener = object : SimpleItemSelectedListener() {
            override fun onSelected(position: Int) {
                if (bindingControls) return
                val interval = INTERVALS.getOrElse(position) { INTERVALS[2] }.minutes
                settings = settingsStore.update {
                    it.copy(intervalMinutes = interval)
                }
                if (settings.slideshowEnabled) {
                    scheduler.schedule(interval)
                }
            }
        }
    }

    private fun simpleSpinnerAdapter(values: List<String>): ArrayAdapter<String> =
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            values,
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    @Suppress("DEPRECATION")
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_IMAGES)
    }

    @Deprecated("Uses platform result API intentionally to avoid an additional Activity dependency.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_IMAGES || resultCode != RESULT_OK || data == null) {
            return
        }

        val uris = extractUris(data)
        if (uris.isEmpty()) {
            return
        }

        val existingUris = settingsStore.load().items.mapTo(mutableSetOf()) { it.uri }
        val additions = mutableListOf<Pair<Uri, String>>()
        var rejected = 0

        for (uri in uris.distinct()) {
            if (uri.toString() in existingUris) {
                continue
            }

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                additions += uri to queryDisplayName(uri)
                existingUris += uri.toString()
            } catch (_: SecurityException) {
                rejected += 1
            }
        }

        if (additions.isNotEmpty()) {
            settings = settingsStore.update { current ->
                val startOrder = current.items.size
                val newItems = additions.mapIndexed { index, (uri, displayName) ->
                    WallpaperItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        displayName = displayName,
                        order = startOrder + index,
                    )
                }
                current.copy(items = current.items + newItems)
            }
        }

        if (rejected > 0) {
            Toast.makeText(
                this,
                "${rejected}件は継続アクセス権を取得できなかったため追加しませんでした。",
                Toast.LENGTH_LONG,
            ).show()
        }
        refreshUi()
    }

    private fun extractUris(data: Intent): List<Uri> {
        val result = mutableListOf<Uri>()
        data.data?.let(result::add)

        val clipData = data.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                result += clipData.getItemAt(index).uri
            }
        }
        return result
    }

    private fun queryDisplayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )
            val index = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            if (index >= 0 && cursor?.moveToFirst() == true) {
                cursor?.getString(index).orEmpty().ifBlank {
                    uri.lastPathSegment ?: "画像"
                }
            } else {
                uri.lastPathSegment ?: "画像"
            }
        } catch (_: Exception) {
            uri.lastPathSegment ?: "画像"
        } finally {
            cursor?.close()
        }
    }

    private fun editSelected() {
        val id = selectedItemId ?: run {
            toast("編集する画像を選択してください。")
            return
        }

        startActivity(
            Intent(this, ImageEditorActivity::class.java)
                .putExtra(ImageEditorActivity.EXTRA_ITEM_ID, id),
        )
    }

    private fun removeSelected() {
        val id = selectedItemId ?: run {
            toast("削除する画像を選択してください。")
            return
        }

        val item = settings.items.firstOrNull { it.id == id } ?: return
        runCatching {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(item.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        settings = settingsStore.update { current ->
            val remaining = current.items
                .filterNot { it.id == id }
                .mapIndexed { index, value -> value.copy(order = index) }

            current.copy(
                items = remaining,
                slideshowEnabled = current.slideshowEnabled && remaining.count { it.enabled } >= 2,
                currentItemId = current.currentItemId.takeUnless { it == id },
            )
        }
        if (!settings.slideshowEnabled) {
            scheduler.cancel()
        }
        selectedItemId = null
        refreshUi()
    }

    private fun startSlideshow() {
        settings = settingsStore.load()
        if (settings.items.count { it.enabled } < 2) {
            toast("自動切替には画像を2枚以上追加してください。")
            return
        }

        settings = settingsStore.update {
            it.copy(slideshowEnabled = true, lastError = null)
        }
        scheduler.schedule(settings.intervalMinutes)
        refreshUi()
        applyNextNow()
    }

    private fun stopSlideshow() {
        scheduler.cancel()
        settings = settingsStore.update {
            it.copy(slideshowEnabled = false)
        }
        refreshUi()
    }

    private fun applyNextNow() {
        statusText.text = "壁紙を変更しています…"
        executor.execute {
            val result = operationService.applyNext(requireSlideshowEnabled = false)
            runOnUiThread {
                if (isDestroyed) {
                    return@runOnUiThread
                }
                when (result) {
                    is WallpaperOperationResult.Success -> toast("壁紙を変更しました。")
                    is WallpaperOperationResult.Failure -> toast(result.message)
                    WallpaperOperationResult.Busy -> toast("壁紙変更処理が実行中です。")
                    WallpaperOperationResult.Disabled -> Unit
                    WallpaperOperationResult.NoImages -> toast("有効な画像がありません。")
                }
                refreshUi()
            }
        }
    }

    private fun refreshUi() {
        settings = settingsStore.load()
        bindingControls = true
        try {
            orderSpinner.setSelection(
                if (settings.orderMode == OrderMode.RANDOM) 1 else 0,
                false,
            )
            targetSpinner.setSelection(
                when (settings.target) {
                    WallpaperTarget.HOME -> 0
                    WallpaperTarget.LOCK -> 1
                    WallpaperTarget.BOTH -> 2
                },
                false,
            )
            intervalSpinner.setSelection(
                INTERVALS.indexOfFirst { it.minutes == settings.intervalMinutes }
                    .takeIf { it >= 0 }
                    ?: 2,
                false,
            )
        } finally {
            bindingControls = false
        }

        val labels = settings.items.map { item ->
            buildString {
                if (item.id == settings.currentItemId) append("● ")
                append(item.displayName)
                append(
                    when (item.layout.mode) {
                        io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode.CONTAIN ->
                            "  [全体]"
                        io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode.CROP ->
                            "  [Crop]"
                    },
                )
            }
        }
        imageList.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            labels,
        )

        val selectedIndex = settings.items.indexOfFirst { it.id == selectedItemId }
        if (selectedIndex >= 0) {
            imageList.setItemChecked(selectedIndex, true)
        }

        statusText.text = buildString {
            append(if (settings.slideshowEnabled) "自動切替: ON" else "自動切替: OFF")
            append(" / ")
            append(settings.items.size)
            append("枚")
            settings.lastError?.let {
                append("\nエラー: ")
                append(it)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private abstract class SimpleItemSelectedListener : AdapterView.OnItemSelectedListener {
        final override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            onSelected(position)
        }

        final override fun onNothingSelected(parent: AdapterView<*>?) = Unit

        abstract fun onSelected(position: Int)
    }

    private data class IntervalOption(
        val minutes: Long,
        val label: String,
    )

    companion object {
        private const val REQUEST_OPEN_IMAGES = 1001

        private val INTERVALS = listOf(
            IntervalOption(15, "15分"),
            IntervalOption(30, "30分"),
            IntervalOption(60, "1時間"),
            IntervalOption(180, "3時間"),
            IntervalOption(360, "6時間"),
            IntervalOption(720, "12時間"),
            IntervalOption(1440, "24時間"),
        )
    }
}
