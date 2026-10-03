package io.github.goroyattemiyo.wallpaperfitslideshow.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

data class ImportedImageSource(
    val uri: Uri,
    val displayName: String,
)

class FolderImageScanner(
    context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    fun scan(
        treeUri: Uri,
        maxImages: Int = MAX_IMAGES,
    ): List<ImportedImageSource> {
        val result = mutableListOf<ImportedImageSource>()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<String>()
        queue.add(rootDocumentId)

        while (queue.isNotEmpty() && result.size < maxImages) {
            val parentDocumentId = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId,
            )

            contentResolver.query(
                childrenUri,
                PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )

                while (cursor.moveToNext() && result.size < maxImages) {
                    val documentId = cursor.getString(idIndex)
                    val displayName = cursor.getString(nameIndex).orEmpty()
                    val mimeType = cursor.getString(mimeIndex).orEmpty()

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(documentId)
                    } else if (mimeType.startsWith("image/")) {
                        result += ImportedImageSource(
                            uri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                documentId,
                            ),
                            displayName = displayName.ifBlank { "画像" },
                        )
                    }
                }
            }
        }

        return result
    }

    companion object {
        const val MAX_IMAGES = 1000

        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
