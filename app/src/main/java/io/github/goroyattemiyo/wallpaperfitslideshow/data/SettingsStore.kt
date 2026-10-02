package io.github.goroyattemiyo.wallpaperfitslideshow.data

import android.content.Context
import android.util.AtomicFile
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import io.github.goroyattemiyo.wallpaperfitslideshow.model.OrderMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperLayoutState
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

class SettingsStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun load(): AppSettings {
        if (!atomicFile.baseFile.exists()) {
            return AppSettings()
        }

        return runCatching {
            atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                decode(JSONObject(reader.readText()))
            }
        }.getOrElse {
            AppSettings(lastError = "設定ファイルを読み込めませんでした。")
        }
    }

    @Synchronized
    fun save(settings: AppSettings) {
        val bytes = encode(settings).toString().toByteArray(StandardCharsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            output.flush()
            atomicFile.finishWrite(output)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(output)
            throw throwable
        }
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(load())
        save(updated)
        return updated
    }

    private fun encode(settings: AppSettings): JSONObject {
        val items = JSONArray()
        settings.items
            .sortedBy { it.order }
            .forEach { item ->
                items.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("uri", item.uri)
                        .put("displayName", item.displayName)
                        .put("order", item.order)
                        .put("enabled", item.enabled)
                        .put(
                            "layout",
                            JSONObject()
                                .put("mode", item.layout.mode.name)
                                .put("userScale", item.layout.userScale)
                                .put("offsetXNormalized", item.layout.offsetXNormalized)
                                .put("offsetYNormalized", item.layout.offsetYNormalized)
                                .put("backgroundColor", item.layout.backgroundColor),
                        ),
                )
            }

        return JSONObject()
            .put("schemaVersion", AppSettings.CURRENT_SCHEMA_VERSION)
            .put("slideshowEnabled", settings.slideshowEnabled)
            .put(
                "intervalMinutes",
                settings.intervalMinutes.coerceAtLeast(AppSettings.MIN_INTERVAL_MINUTES),
            )
            .put("orderMode", settings.orderMode.name)
            .put("target", settings.target.name)
            .put("currentItemId", settings.currentItemId ?: JSONObject.NULL)
            .put("lastSuccessEpochMillis", settings.lastSuccessEpochMillis ?: JSONObject.NULL)
            .put("lastError", settings.lastError ?: JSONObject.NULL)
            .put("items", items)
    }

    private fun decode(root: JSONObject): AppSettings {
        val schemaVersion = root.optInt("schemaVersion", 1)
        require(schemaVersion <= AppSettings.CURRENT_SCHEMA_VERSION) {
            "Unsupported settings schema: $schemaVersion"
        }

        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until itemsArray.length()) {
                val objectValue = itemsArray.optJSONObject(index) ?: continue
                val id = objectValue.optString("id")
                val uri = objectValue.optString("uri")
                if (id.isBlank() || uri.isBlank()) {
                    continue
                }

                val layoutJson = objectValue.optJSONObject("layout") ?: JSONObject()
                val layout = WallpaperLayoutState(
                    mode = enumOrDefault(
                        layoutJson.optString("mode"),
                        LayoutMode.CONTAIN,
                    ),
                    userScale = layoutJson
                        .optDouble("userScale", 1.0)
                        .takeIf { it.isFinite() && it > 0.0 }
                        ?: 1.0,
                    offsetXNormalized = layoutJson
                        .optDouble("offsetXNormalized", 0.0)
                        .takeIf { it.isFinite() }
                        ?.coerceIn(-1.0, 1.0)
                        ?: 0.0,
                    offsetYNormalized = layoutJson
                        .optDouble("offsetYNormalized", 0.0)
                        .takeIf { it.isFinite() }
                        ?.coerceIn(-1.0, 1.0)
                        ?: 0.0,
                    backgroundColor = layoutJson.optInt(
                        "backgroundColor",
                        0xFF000000.toInt(),
                    ),
                )

                add(
                    WallpaperItem(
                        id = id,
                        uri = uri,
                        displayName = objectValue
                            .optString("displayName")
                            .takeIf { it.isNotBlank() }
                            ?: "画像 ${index + 1}",
                        order = objectValue.optInt("order", index),
                        enabled = objectValue.optBoolean("enabled", true),
                        layout = layout,
                    ),
                )
            }
        }.sortedBy { it.order }

        return AppSettings(
            schemaVersion = schemaVersion,
            slideshowEnabled = root.optBoolean("slideshowEnabled", false),
            intervalMinutes = root
                .optLong("intervalMinutes", AppSettings.DEFAULT_INTERVAL_MINUTES)
                .coerceAtLeast(AppSettings.MIN_INTERVAL_MINUTES),
            orderMode = enumOrDefault(
                root.optString("orderMode"),
                OrderMode.SEQUENTIAL,
            ),
            target = enumOrDefault(
                root.optString("target"),
                WallpaperTarget.BOTH,
            ),
            currentItemId = root.optNullableString("currentItemId"),
            lastSuccessEpochMillis = root.optNullableLong("lastSuccessEpochMillis"),
            lastError = root.optNullableString("lastError"),
            items = items,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        value: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name) || !has(name)) null else optLong(name)

    companion object {
        private const val FILE_NAME = "wallpaper-settings.json"
    }
}
