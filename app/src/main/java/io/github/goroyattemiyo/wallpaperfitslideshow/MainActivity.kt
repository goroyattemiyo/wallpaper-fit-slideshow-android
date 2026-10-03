package io.github.goroyattemiyo.wallpaperfitslideshow

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import io.github.goroyattemiyo.wallpaperfitslideshow.data.FolderImageScanner
import io.github.goroyattemiyo.wallpaperfitslideshow.data.ImportedImageSource
import io.github.goroyattemiyo.wallpaperfitslideshow.data.SettingsStore
import io.github.goroyattemiyo.wallpaperfitslideshow.data.ZipImageImporter
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.BackgroundMode
import io.github.goroyattemiyo.wallpaperfitslideshow.ui.WallpaperItemAdapter
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import io.github.goroyattemiyo.wallpaperfitslideshow.poc.LockPinchWallpaperService
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationResult
import io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper.WallpaperOperationService
import io.github.goroyattemiyo.wallpaperfitslideshow.work.SlideshowScheduler
import io.github.goroyattemiyo.wallpaperfitslideshow.widget.WallpaperControlWidgetProvider
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var scheduler: SlideshowScheduler
    private lateinit var operationService: WallpaperOperationService
    private lateinit var imageList: ListView
    private lateinit var statusText: TextView
    private lateinit var settingsSummaryText: TextView
    private lateinit var detailCard: View
    private lateinit var detailTitle: TextView
    private lateinit var detailState: TextView
    private lateinit var itemAdapter: WallpaperItemAdapter
    private lateinit var startButton: Button
    private lateinit var nextButton: Button

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var settings: AppSettings = AppSettings()
    private var selectedItemId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsStore = SettingsStore(applicationContext)
        scheduler = SlideshowScheduler(applicationContext)
        operationService = WallpaperOperationService(applicationContext)

        imageList = findViewById(R.id.image_list)
        statusText = findViewById(R.id.status_text)
        settingsSummaryText = findViewById(R.id.slideshow_settings_summary)
        detailCard = findViewById(R.id.detail_card)
        detailTitle = findViewById(R.id.detail_title)
        detailState = findViewById(R.id.detail_state)
        startButton = findViewById(R.id.start_button)
        nextButton = findViewById(R.id.next_button)
        itemAdapter = WallpaperItemAdapter(
            context = this,
            onTargetChanged = ::setItemTargetEnabled,
        )
        imageList.adapter = itemAdapter

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
        if (::itemAdapter.isInitialized) {
            itemAdapter.release()
        }
        super.onDestroy()
    }

    private fun configureList() {
        imageList.choiceMode = ListView.CHOICE_MODE_SINGLE
        imageList.setOnItemClickListener { _, _, position, _ ->
            val item = itemAdapter.getItem(position)
            selectedItemId = item.id
            imageList.setItemChecked(position, true)
            refreshUi()
        }
    }

    private fun configureButtons() {
        findViewById<Button>(R.id.add_images_button).setOnClickListener {
            openImagePicker()
        }
        findViewById<Button>(R.id.add_folder_button).setOnClickListener {
            openFolderPicker()
        }
        findViewById<Button>(R.id.add_zip_button).setOnClickListener {
            openZipPicker()
        }
        findViewById<Button>(R.id.edit_image_button).setOnClickListener {
            editSelected()
        }
        findViewById<Button>(R.id.remove_image_button).setOnClickListener {
            removeSelected()
        }
        findViewById<Button>(R.id.move_up_button).setOnClickListener {
            moveSelected(-1)
        }
        findViewById<Button>(R.id.move_down_button).setOnClickListener {
            moveSelected(1)
        }
        findViewById<Button>(R.id.select_all_button).setOnClickListener {
            setAllItemsEnabled(true)
        }
        findViewById<Button>(R.id.clear_all_button).setOnClickListener {
            setAllItemsEnabled(false)
        }
        findViewById<Button>(R.id.slideshow_settings_button).setOnClickListener {
            showSlideshowSettingsDialog()
        }
        findViewById<Button>(R.id.lock_pinch_poc_button).setOnClickListener {
            launchLockPinchPoc()
        }
        startButton.setOnClickListener {
            startSlideshow()
        }
        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopSlideshow()
        }
        nextButton.setOnClickListener {
            applyNextNow()
        }
    }

    private fun launchLockPinchPoc() {
        settings = settingsStore.load()
        if (settings.slideshowEnabled) {
            toast("PoC検証中に静的壁紙で上書きされるため、先にスライドショーを停止してください。")
            return
        }

        val hasLockImage = settings.currentLockItemId != null ||
            settings.items.any { it.lockEnabled }
        if (!hasLockImage) {
            toast("ロック対象の画像を1枚以上選んでください。")
            return
        }

        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(
                    this@MainActivity,
                    LockPinchWallpaperService::class.java,
                ),
            )
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            startActivity(
                Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
            )
        }
    }

    private fun showSlideshowSettingsDialog() {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_slideshow_settings,
            null,
        )
        val orderSpinner = dialogView.findViewById<android.widget.Spinner>(
            R.id.dialog_order_spinner,
        )
        val intervalSpinner = dialogView.findViewById<android.widget.Spinner>(
            R.id.dialog_interval_spinner,
        )
        val homeBlurLabel = dialogView.findViewById<TextView>(
            R.id.dialog_home_blur_label,
        )
        val homeBlurSeek = dialogView.findViewById<SeekBar>(
            R.id.dialog_home_blur_seek,
        )
        val lockBlurLabel = dialogView.findViewById<TextView>(
            R.id.dialog_lock_blur_label,
        )
        val lockBlurSeek = dialogView.findViewById<SeekBar>(
            R.id.dialog_lock_blur_seek,
        )

        orderSpinner.adapter = simpleSpinnerAdapter(
            listOf("順番", "ランダム"),
        )
        intervalSpinner.adapter = simpleSpinnerAdapter(
            INTERVALS.map { it.label },
        )

        orderSpinner.setSelection(
            if (settings.orderMode == OrderMode.RANDOM) 1 else 0,
        )
        intervalSpinner.setSelection(
            INTERVALS.indexOfFirst {
                it.seconds == settings.intervalSeconds
            }.takeIf { it >= 0 } ?: DEFAULT_INTERVAL_INDEX,
        )

        bindBlurSeekBar(
            seekBar = homeBlurSeek,
            label = homeBlurLabel,
            prefix = "ホーム画面ぼかし",
            initialValue = settings.homeWallpaperBlurRadius,
        )
        bindBlurSeekBar(
            seekBar = lockBlurSeek,
            label = lockBlurLabel,
            prefix = "ロック画面ぼかし",
            initialValue = settings.lockWallpaperBlurRadius,
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("スライドショー設定")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val orderMode = if (orderSpinner.selectedItemPosition == 1) {
                    OrderMode.RANDOM
                } else {
                    OrderMode.SEQUENTIAL
                }
                val intervalSeconds = INTERVALS
                    .getOrElse(intervalSpinner.selectedItemPosition) {
                        INTERVALS[DEFAULT_INTERVAL_INDEX]
                    }
                    .seconds

                settings = settingsStore.update {
                    it.copy(
                        orderMode = orderMode,
                        intervalSeconds = intervalSeconds,
                        homeWallpaperBlurRadius = homeBlurSeek.progress,
                        lockWallpaperBlurRadius = lockBlurSeek.progress,
                    )
                }

                if (settings.slideshowEnabled) {
                    scheduler.schedule(intervalSeconds)
                }
                refreshUi()
                reapplyCurrentWallpapers()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun bindBlurSeekBar(
        seekBar: SeekBar,
        label: TextView,
        prefix: String,
        initialValue: Int,
    ) {
        seekBar.max = AppSettings.MAX_WALLPAPER_BLUR_RADIUS
        seekBar.progress = initialValue.coerceIn(
            0,
            AppSettings.MAX_WALLPAPER_BLUR_RADIUS,
        )

        fun updateLabel(value: Int) {
            label.text = if (value == 0) {
                "$prefix: OFF"
            } else {
                "$prefix: $value"
            }
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    updateLabel(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            },
        )
        updateLabel(seekBar.progress)
    }

    private fun reapplyCurrentWallpapers() {
        val snapshot = settingsStore.load()
        val targets = buildList {
            snapshot.currentHomeItemId?.let { add(it to WallpaperTarget.HOME) }
            snapshot.currentLockItemId?.let { add(it to WallpaperTarget.LOCK) }
        }
        if (targets.isEmpty()) {
            return
        }

        executor.execute {
            targets.forEach { (itemId, target) ->
                operationService.applyItem(itemId, target)
            }
            runOnUiThread {
                if (!isDestroyed) {
                    refreshUi()
                }
            }
        }
    }

    private fun simpleSpinnerAdapter(
        values: List<String>,
    ): ArrayAdapter<String> =
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            values,
        ).also {
            it.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item,
            )
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

    @Suppress("DEPRECATION")
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_FOLDER)
    }

    @Suppress("DEPRECATION")
    private fun openZipPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                ),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_ZIP)
    }

    @Deprecated("Uses platform result API intentionally to avoid an additional Activity dependency.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            return
        }

        when (requestCode) {
            REQUEST_OPEN_IMAGES -> handlePickedImages(data)
            REQUEST_OPEN_FOLDER -> handlePickedFolder(data)
            REQUEST_OPEN_ZIP -> handlePickedZip(data)
        }
    }

    private fun handlePickedImages(data: Intent) {
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

    private fun handlePickedFolder(data: Intent) {
        val treeUri = data.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            toast("フォルダの継続アクセス権を取得できませんでした。")
            return
        }

        statusText.text = "フォルダを読み込んでいます…"
        executor.execute {
            val result = runCatching {
                FolderImageScanner(applicationContext).scan(treeUri)
            }
            runOnUiThread {
                if (isDestroyed) {
                    return@runOnUiThread
                }
                result.onSuccess { sources ->
                    addImportedSources(sources)
                    if (sources.size >= FolderImageScanner.MAX_IMAGES) {
                        toast("フォルダから最大${FolderImageScanner.MAX_IMAGES}枚まで追加しました。")
                    }
                }.onFailure {
                    toast(it.message ?: "フォルダを読み込めませんでした。")
                    refreshUi()
                }
            }
        }
    }

    private fun handlePickedZip(data: Intent) {
        val zipUri = data.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(
                zipUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        statusText.text = "ZIPから画像を取り込んでいます…"
        executor.execute {
            val result = runCatching {
                ZipImageImporter(applicationContext).import(zipUri)
            }
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    zipUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            runOnUiThread {
                if (isDestroyed) {
                    return@runOnUiThread
                }
                result.onSuccess { sources ->
                    addImportedSources(sources)
                    if (sources.isEmpty()) {
                        toast("ZIP内に対応画像がありませんでした。")
                    }
                }.onFailure {
                    toast(it.message ?: "ZIPを取り込めませんでした。")
                    refreshUi()
                }
            }
        }
    }

    private fun addImportedSources(sources: List<ImportedImageSource>) {
        if (sources.isEmpty()) {
            refreshUi()
            return
        }

        val existingUris = settingsStore.load().items
            .mapTo(mutableSetOf()) { it.uri }
        val additions = sources.filter { it.uri.toString() !in existingUris }

        if (additions.isNotEmpty()) {
            settings = settingsStore.update { current ->
                val startOrder = current.items.size
                val newItems = additions.mapIndexed { index, source ->
                    WallpaperItem(
                        id = UUID.randomUUID().toString(),
                        uri = source.uri.toString(),
                        displayName = source.displayName,
                        order = startOrder + index,
                    )
                }
                current.copy(items = current.items + newItems)
            }
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

    private fun setItemTargetEnabled(
        itemId: String,
        target: WallpaperTarget,
        enabled: Boolean,
    ) {
        settings = settingsStore.update { current ->
            val updatedItems = current.items.map { item ->
                if (item.id != itemId) {
                    item
                } else {
                    when (target) {
                        WallpaperTarget.HOME -> item.copy(homeEnabled = enabled)
                        WallpaperTarget.LOCK -> item.copy(lockEnabled = enabled)
                        WallpaperTarget.BOTH -> item
                    }
                }
            }
            val canRun = updatedItems.count { it.homeEnabled } >= 2 ||
                updatedItems.count { it.lockEnabled } >= 2
            current.copy(
                items = updatedItems,
                slideshowEnabled = current.slideshowEnabled && canRun,
            )
        }

        if (!settings.slideshowEnabled) {
            scheduler.cancel()
        }
        refreshUi()
    }

    private fun setAllItemsEnabled(enabled: Boolean) {
        settings = settingsStore.update { current ->
            val updatedItems = current.items.map {
                it.copy(
                    homeEnabled = enabled,
                    lockEnabled = enabled,
                )
            }
            current.copy(
                items = updatedItems,
                slideshowEnabled = current.slideshowEnabled &&
                    enabled &&
                    updatedItems.size >= 2,
            )
        }

        if (!settings.slideshowEnabled) {
            scheduler.cancel()
        }
        refreshUi()
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

    private fun moveSelected(delta: Int) {
        val id = selectedItemId ?: run {
            toast("移動する画像を選択してください。")
            return
        }

        settings = settingsStore.update { current ->
            val ordered = current.items.sortedBy { it.order }.toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            if (from < 0) {
                return@update current
            }

            val to = (from + delta).coerceIn(0, ordered.lastIndex)
            if (to == from) {
                return@update current
            }

            val moved = ordered.removeAt(from)
            ordered.add(to, moved)
            current.copy(
                items = ordered.mapIndexed { index, item ->
                    item.copy(order = index)
                },
            )
        }
        refreshUi()
    }

    private fun removeSelected() {
        val id = selectedItemId ?: run {
            toast("削除する画像を選択してください。")
            return
        }

        val item = settings.items.firstOrNull { it.id == id } ?: return
        val itemUri = Uri.parse(item.uri)
        if (itemUri.scheme == "file") {
            cleanupManagedFile(itemUri)
        } else {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    itemUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        settings = settingsStore.update { current ->
            val remaining = current.items
                .filterNot { it.id == id }
                .mapIndexed { index, value -> value.copy(order = index) }

            val canRun = remaining.count { it.homeEnabled } >= 2 ||
                remaining.count { it.lockEnabled } >= 2
            current.copy(
                items = remaining,
                slideshowEnabled = current.slideshowEnabled && canRun,
                currentHomeItemId = current.currentHomeItemId.takeUnless { it == id },
                currentLockItemId = current.currentLockItemId.takeUnless { it == id },
            )
        }
        if (!settings.slideshowEnabled) {
            scheduler.cancel()
        }
        selectedItemId = null
        refreshUi()
    }

    private fun cleanupManagedFile(uri: Uri) {
        val path = uri.path ?: return
        val file = java.io.File(path)
        val managedRoot = java.io.File(filesDir, "imported-zips")

        runCatching {
            val canonical = file.canonicalFile
            val rootCanonical = managedRoot.canonicalFile
            if (canonical.path.startsWith(rootCanonical.path + java.io.File.separator)) {
                canonical.delete()
                canonical.parentFile?.let { parent ->
                    if (parent.isDirectory && parent.list()?.isEmpty() == true) {
                        parent.delete()
                    }
                }
            }
        }
    }

    private fun startSlideshow() {
        settings = settingsStore.load()
        val homeCount = settings.items.count { it.homeEnabled }
        val lockCount = settings.items.count { it.lockEnabled }
        if (homeCount < 2 && lockCount < 2) {
            toast("ホームまたはロックに2枚以上チェックしてください。")
            return
        }

        settings = settingsStore.update {
            it.copy(slideshowEnabled = true, lastError = null)
        }
        scheduler.schedule(settings.intervalSeconds)
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
                    is WallpaperOperationResult.Success -> {
                        if (result.warnings.isEmpty()) {
                            toast("壁紙を変更しました。")
                        } else {
                            toast("一部変更できませんでした: " + result.warnings.joinToString(" / "))
                        }
                    }
                    is WallpaperOperationResult.Failure -> toast(result.message)
                    WallpaperOperationResult.Busy -> toast("壁紙変更処理が実行中です。")
                    WallpaperOperationResult.Disabled -> Unit
                    WallpaperOperationResult.NoImages -> toast("スライドショー対象の画像がありません。")
                }
                refreshUi()
            }
        }
    }

    private fun refreshUi() {
        settings = settingsStore.load()
        if (selectedItemId != null && settings.items.none { it.id == selectedItemId }) {
            selectedItemId = null
        }
        settingsSummaryText.text = buildString {
            append(orderLabel(settings.orderMode))
            append(" / ")
            append(formatInterval(settings.intervalSeconds))
            append(" / ぼかし H")
            append(settings.homeWallpaperBlurRadius)
            append(" L")
            append(settings.lockWallpaperBlurRadius)
            if (settings.intervalSeconds < AppSettings.WORK_MANAGER_MIN_INTERVAL_SECONDS) {
                append("（高速）")
            }
        }

        itemAdapter.update(
            items = settings.items,
            selectedItemId = selectedItemId,
        )

        val selectedIndex = itemAdapter.indexOfItemId(selectedItemId)
        if (selectedIndex >= 0) {
            imageList.setItemChecked(selectedIndex, true)
        } else {
            imageList.clearChoices()
        }

        val homeCount = settings.items.count { it.homeEnabled }
        val lockCount = settings.items.count { it.lockEnabled }
        startButton.text = "開始（H${homeCount}/L${lockCount}）"
        nextButton.text = "次へ"
        updateDetailCard()

        statusText.text = buildString {
            append(
                if (settings.slideshowEnabled) {
                    if (settings.intervalSeconds < AppSettings.WORK_MANAGER_MIN_INTERVAL_SECONDS) {
                        "自動切替: ON（高速モード）"
                    } else {
                        "自動切替: ON"
                    }
                } else {
                    "自動切替: OFF"
                },
            )
            append(" / H ")
            append(homeCount)
            append(" / L ")
            append(lockCount)
            append(" / 全")
            append(settings.items.size)
            append("枚")
            settings.lastError?.let {
                append("\nエラー: ")
                append(it)
            }
        }
        WallpaperControlWidgetProvider.updateAll(applicationContext)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun orderLabel(mode: OrderMode): String =
        if (mode == OrderMode.RANDOM) "ランダム" else "順番"

    private fun updateDetailCard() {
        val item = settings.items.firstOrNull { it.id == selectedItemId }
        if (item == null) {
            detailCard.visibility = View.GONE
            return
        }

        detailCard.visibility = View.VISIBLE
        detailTitle.text = item.displayName
        detailState.text = buildString {
            append("ホーム: ")
            append(if (item.homeEnabled) "ON" else "OFF")
            append(" / ")
            append(layoutLabel(item.homeLayout))
            if (item.id == settings.currentHomeItemId) {
                append(" / 現在")
            }
            append("\nロック: ")
            append(if (item.lockEnabled) "ON" else "OFF")
            append(" / ")
            append(layoutLabel(item.lockLayout))
            if (item.id == settings.currentLockItemId) {
                append(" / 現在")
            }
        }
    }

    private fun layoutLabel(
        layout: io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState,
    ): String = buildString {
        append(
            if (layout.mode == LayoutMode.CONTAIN) {
                "全体表示"
            } else {
                "調整済み"
            },
        )
        if (layout.backgroundMode == BackgroundMode.BLUR) {
            append(" / 背景ぼかし")
        }
    }

    private fun formatInterval(seconds: Long): String =
        when {
            seconds < 60L -> "${seconds}秒"
            seconds % 3600L == 0L -> "${seconds / 3600L}時間"
            seconds % 60L == 0L -> "${seconds / 60L}分"
            else -> "${seconds}秒"
        }

    private data class IntervalOption(
        val seconds: Long,
        val label: String,
    )

    companion object {
        private const val REQUEST_OPEN_IMAGES = 1001
        private const val REQUEST_OPEN_FOLDER = 1002
        private const val REQUEST_OPEN_ZIP = 1003
        private const val DEFAULT_INTERVAL_INDEX = 6

        private val INTERVALS = listOf(
            IntervalOption(10, "10秒（高速）"),
            IntervalOption(30, "30秒（高速）"),
            IntervalOption(60, "1分（高速）"),
            IntervalOption(300, "5分（高速）"),
            IntervalOption(900, "15分"),
            IntervalOption(1800, "30分"),
            IntervalOption(3600, "1時間"),
            IntervalOption(10800, "3時間"),
            IntervalOption(21600, "6時間"),
            IntervalOption(43200, "12時間"),
            IntervalOption(86400, "24時間"),
        )
    }
}
