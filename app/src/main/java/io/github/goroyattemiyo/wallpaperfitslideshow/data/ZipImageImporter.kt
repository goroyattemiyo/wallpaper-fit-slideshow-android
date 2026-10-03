package io.github.goroyattemiyo.wallpaperfitslideshow.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

class ZipImageImporter(
    private val context: Context,
) {
    fun import(
        zipUri: Uri,
        maxImages: Int = MAX_IMAGES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
        maxEntryBytes: Long = MAX_ENTRY_BYTES,
    ): List<ImportedImageSource> {
        val root = File(
            context.filesDir,
            "imported-zips/${UUID.randomUUID()}",
        )
        if (!root.mkdirs() && !root.isDirectory) {
            throw IOException("ZIP展開先を作成できませんでした。")
        }

        val imported = mutableListOf<ImportedImageSource>()
        var totalBytes = 0L

        try {
            context.contentResolver.openInputStream(zipUri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && imported.size < maxImages) {
                        if (!entry.isDirectory && isSupportedImage(entry.name)) {
                            val extension = entry.name
                                .substringAfterLast('.', "img")
                                .lowercase()
                            val output = File(
                                root,
                                "${imported.size.toString().padStart(4, '0')}-${UUID.randomUUID()}.$extension",
                            )

                            val written = copyEntryWithLimit(
                                zip = zip,
                                output = output,
                                entryLimit = maxEntryBytes,
                                remainingTotal = maxTotalBytes - totalBytes,
                            )
                            totalBytes += written

                            imported += ImportedImageSource(
                                uri = Uri.fromFile(output),
                                displayName = entry.name
                                    .substringAfterLast('/')
                                    .ifBlank { "ZIP画像 ${imported.size + 1}" },
                            )
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw IOException("ZIPファイルを開けませんでした。")

            if (imported.isEmpty()) {
                root.deleteRecursively()
            }
            return imported
        } catch (throwable: Throwable) {
            root.deleteRecursively()
            throw throwable
        }
    }

    private fun copyEntryWithLimit(
        zip: ZipInputStream,
        output: File,
        entryLimit: Long,
        remainingTotal: Long,
    ): Long {
        if (remainingTotal <= 0L) {
            throw IOException("ZIP展開サイズが上限を超えました。")
        }

        val allowed = minOf(entryLimit, remainingTotal)
        var written = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        FileOutputStream(output).buffered().use { out ->
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break

                written += read
                if (written > allowed) {
                    throw IOException("ZIP内の画像サイズが上限を超えました。")
                }
                out.write(buffer, 0, read)
            }
        }
        return written
    }

    private fun isSupportedImage(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_EXTENSIONS
    }

    companion object {
        const val MAX_IMAGES = 500
        const val MAX_ENTRY_BYTES = 50L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 500L * 1024L * 1024L

        private val SUPPORTED_EXTENSIONS = setOf(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "gif",
            "bmp",
            "heic",
            "heif",
        )
    }
}
