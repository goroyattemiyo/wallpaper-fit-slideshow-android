package io.github.goroyattemiyo.wallpaperfitslideshow.data

import android.content.Context
import android.util.AtomicFile
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.AppSettings
import io.github.goroyattemiyo.wallpaperfitslideshow.model.BackgroundMode
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

    fun load(): AppSettings = synchronized(FILE_LOCK) {
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

    fun save(settings: AppSettings) = synchronized(FILE_LOCK) {
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

    fun update(transform: (AppSettings) -> AppSettings): AppSettings =
        synchronized(FILE_LOCK) {
            val updated = transform(load())
            save(updated)
            updated
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
                        .put("homeEnabled", item.homeEnabled)
                        .put("lockEnabled", item.lockEnabled)
                        .put("homeLayout", encodeLayout(item.homeLayout))
                        .put("lockLayout", encodeLayout(item.lockLayout)),
                )
            }

        return JSONObject()
            .put("schemaVersion", AppSettings.CURRENT_SCHEMA_VERSION)
            .put("slideshowEnabled", settings.slideshowEnabled)
            .put(
                "intervalSeconds",
                settings.intervalSeconds.coerceAtLeast(AppSettings.MIN_INTERVAL_SECONDS),
            )
            .put("orderMode", settings.orderMode.name)
            .put("currentHomeItemId", settings.currentHomeItemId ?: JSONObject.NULL)
            .put("currentLockItemId", settings.currentLockItemId ?: JSONObject.NULL)
            .put("lastSuccessEpochMillis", settings.lastSuccessEpochMillis ?: JSONObject.NULL)
            .put("lastError", settings.lastError ?: JSONObject.NULL)
            .put("items", items)
    }

    private fun encodeLayout(layout: WallpaperLayoutState): JSONObject =
        JSONObject()
            .put("mode", layout.mode.name)
            .put("userScale", layout.userScale)
            .put("offsetXNormalized", layout.offsetXNormalized)
            .put("offsetYNormalized", layout.offsetYNormalized)
            .put("backgroundColor", layout.backgroundColor)
            .put("backgroundMode", layout.backgroundMode.name)
            .put("blurRadius", layout.blurRadius)
            .put("backgroundImageAlpha", layout.backgroundImageAlpha)

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

                val legacyEnabled = objectValue.optBoolean("enabled", true)
                val legacyLayout = objectValue.optJSONObject("layout")
                val homeLayoutJson = objectValue.optJSONObject("homeLayout")
                    ?: legacyLayout
                    ?: JSONObject()
                val lockLayoutJson = objectValue.optJSONObject("lockLayout")
                    ?: legacyLayout
                    ?: JSONObject()

                add(
                    WallpaperItem(
                        id = id,
                        uri = uri,
                        displayName = objectValue
                            .optString("displayName")
                            .takeIf { it.isNotBlank() }
                            ?: "画像 ${index + 1}",
                        order = objectValue.optInt("order", index),
                        homeEnabled = if (objectValue.has("homeEnabled")) {
                            objectValue.optBoolean("homeEnabled", true)
                        } else {
                            legacyEnabled
                        },
                        lockEnabled = if (objectValue.has("lockEnabled")) {
                            objectValue.optBoolean("lockEnabled", true)
                        } else {
                            legacyEnabled
                        },
                        homeLayout = decodeLayout(homeLayoutJson),
                        lockLayout = decodeLayout(lockLayoutJson),
                    ),
                )
            }
        }.sortedBy { it.order }

        val legacyCurrentItemId = root.optNullableString("currentItemId")
        val legacyTarget = enumOrDefault(
            root.optString("target"),
            WallpaperTarget.BOTH,
        )

        val currentHomeItemId = if (root.has("currentHomeItemId")) {
            root.optNullableString("currentHomeItemId")
        } else {
            legacyCurrentItemId.takeIf {
                legacyTarget == WallpaperTarget.HOME ||
                    legacyTarget == WallpaperTarget.BOTH
            }
        }
        val currentLockItemId = if (root.has("currentLockItemId")) {
            root.optNullableString("currentLockItemId")
        } else {
            legacyCurrentItemId.takeIf {
                legacyTarget == WallpaperTarget.LOCK ||
                    legacyTarget == WallpaperTarget.BOTH
            }
        }

        return AppSettings(
            schemaVersion = AppSettings.CURRENT_SCHEMA_VERSION,
            slideshowEnabled = root.optBoolean("slideshowEnabled", false),
            intervalSeconds = (
                if (root.has("intervalSeconds")) {
                    root.optLong(
                        "intervalSeconds",
                        AppSettings.DEFAULT_INTERVAL_SECONDS,
                    )
                } else {
                    root.optLong("intervalMinutes", 60L) * 60L
                }
            ).coerceAtLeast(AppSettings.MIN_INTERVAL_SECONDS),
            orderMode = enumOrDefault(
                root.optString("orderMode"),
                OrderMode.SEQUENTIAL,
            ),
            currentHomeItemId = currentHomeItemId,
            currentLockItemId = currentLockItemId,
            lastSuccessEpochMillis = root.optNullableLong("lastSuccessEpochMillis"),
            lastError = root.optNullableString("lastError"),
            items = items,
        )
    }

    private fun decodeLayout(layoutJson: JSONObject): WallpaperLayoutState =
        WallpaperLayoutState(
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
            backgroundMode = enumOrDefault(
                layoutJson.optString("backgroundMode"),
                BackgroundMode.SOLID,
            ),
            blurRadius = layoutJson
                .optInt(
                    "blurRadius",
                    WallpaperLayoutState.DEFAULT_BLUR_RADIUS,
                )
                .coerceIn(0, WallpaperLayoutState.MAX_BLUR_RADIUS),
            backgroundImageAlpha = layoutJson
                .optInt("backgroundImageAlpha", 255)
                .coerceIn(0, 255),
        )

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
        private val FILE_LOCK = Any()
    }
}
