package com.nova.runtime.android.storageAccessAdapter

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.nova.runtime.storage.search.DownloadItem
import com.nova.runtime.storage.search.DownloadQueryResult
import com.nova.runtime.storage.search.DocumentsQueryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries MediaStore Files (with an explicit Documents/ path pass) and merges a
 * local filesystem scan so the public Documents folder is not Downloads-only.
 */
class DocumentsQueryPortImpl(
    private val context: Context,
) : DocumentsQueryPort {
    override suspend fun queryDocuments(limit: Int, offset: Int): DownloadQueryResult =
        withContext(Dispatchers.IO) {
            val fetch = (limit + offset).coerceAtLeast(limit)
            val mediaItems =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fromDocumentsFolder = queryMediaStoreByRelativePath(
                        maxItems = fetch,
                        relativePathPrefixes = listOf("Documents/", "Document/", "My Documents/"),
                    )
                    val general = queryMediaStore(fetch)
                    mergeUnique(fromDocumentsFolder, general)
                } else {
                    emptyList()
                }
            val localItems = LocalDocumentScan.scan(context, fetch, 0)
            // Local scan first so Documents-folder files win over same-named Downloads.
            val merged = mergeUnique(localItems, mediaItems)
            DownloadQueryResult(
                merged
                    .drop(offset.coerceAtLeast(0))
                    .take(limit.coerceAtLeast(1)),
            )
        }

    private fun queryMediaStoreByRelativePath(
        maxItems: Int,
        relativePathPrefixes: List<String>,
    ): List<DownloadItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || relativePathPrefixes.isEmpty()) {
            return emptyList()
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
            )
        val pathClause = relativePathPrefixes.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        }
        val selection = "($pathClause)"
        val selectionArgs = relativePathPrefixes.map { "$it%" }.toTypedArray()
        return readMediaStore(collection, projection, selection, selectionArgs, maxItems)
    }

    private fun queryMediaStore(maxItems: Int): List<DownloadItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.SIZE,
            )
        val selection = buildString {
            append("(")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
            append(" OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append(" OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
            }
            append(")")
        }
        val selectionArgs = buildList {
            add("application/pdf")
            add("application/msword")
            add("application/vnd.%")
            add("text/%")
            add("image/%")
            add(MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT.toString())
            }
        }.toTypedArray()
        return readMediaStore(collection, projection, selection, selectionArgs, maxItems)
    }

    private fun readMediaStore(
        collection: android.net.Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        maxItems: Int,
    ): List<DownloadItem> {
        val items = mutableListOf<DownloadItem>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext() && items.size < maxItems.coerceAtLeast(1)) {
                val id = cursor.getLong(idIndex)
                items.add(
                    DownloadItem(
                        downloadId = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = cursor.getString(nameIndex),
                        mimeType = cursor.getString(mimeIndex),
                        dateAdded = cursor.getLong(dateIndex),
                        size = cursor.getLong(sizeIndex),
                    ),
                )
            }
        }
        return items
    }

    private fun mergeUnique(
        primary: List<DownloadItem>,
        secondary: List<DownloadItem>,
    ): List<DownloadItem> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<DownloadItem>()
        fun key(item: DownloadItem): String {
            // Prefer URI so Documents/foo.pdf and Download/foo.pdf both survive.
            val uri = item.uri.lowercase()
            val name = item.displayName?.lowercase().orEmpty()
            return "$uri|$name"
        }
        for (item in primary) {
            if (seen.add(key(item))) out += item
        }
        for (item in secondary) {
            if (seen.add(key(item))) out += item
        }
        return out.sortedByDescending { it.dateAdded }
    }
}
