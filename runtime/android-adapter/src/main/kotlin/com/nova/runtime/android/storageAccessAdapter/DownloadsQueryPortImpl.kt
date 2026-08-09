package com.nova.runtime.android.storageAccessAdapter

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.nova.runtime.storage.search.DownloadItem
import com.nova.runtime.storage.search.DownloadQueryResult
import com.nova.runtime.storage.search.DownloadsQueryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Queries MediaStore Downloads and returns structured items with content URIs. */
class DownloadsQueryPortImpl(
    private val context: Context,
) : DownloadsQueryPort {
    override suspend fun queryDownloads(limit: Int, offset: Int): DownloadQueryResult =
        withContext(Dispatchers.IO) {
            val mediaItems = queryMediaStoreDownloads(limit, offset)
            if (mediaItems.size >= limit.coerceAtLeast(1)) {
                return@withContext DownloadQueryResult(mediaItems)
            }
            // Merge local scan (includes Documents + Downloads) when MediaStore Downloads
            // is empty or sparse — so Documents-folder files are not skipped entirely
            // if the DOCUMENTS index category hasn't run yet.
            val seenUris = mediaItems.map { it.uri.lowercase() }.toHashSet()
            val seenNames = mediaItems.mapNotNull { it.displayName?.lowercase() }.toHashSet()
            val localNeeded = (limit - mediaItems.size).coerceAtLeast(1)
            val localItems = LocalDocumentScan.scan(context, localNeeded + offset + 50, 0)
                .filter { item ->
                    item.uri.lowercase() !in seenUris &&
                        item.displayName?.lowercase() !in seenNames
                }
                .drop(if (mediaItems.isEmpty()) offset else 0)
                .take(localNeeded)
            DownloadQueryResult(mediaItems + localItems)
        }

    private fun queryMediaStoreDownloads(limit: Int, offset: Int): List<DownloadItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection =
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.MIME_TYPE,
                MediaStore.Downloads.DATE_ADDED,
                MediaStore.Downloads.SIZE,
            )
        val items = mutableListOf<DownloadItem>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            var count = 0
            var skipped = 0
            while (cursor.moveToNext()) {
                if (skipped < offset) {
                    skipped++
                    continue
                }
                if (count >= limit.coerceAtLeast(1)) break
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
                count++
            }
        }
        return items
    }
}
