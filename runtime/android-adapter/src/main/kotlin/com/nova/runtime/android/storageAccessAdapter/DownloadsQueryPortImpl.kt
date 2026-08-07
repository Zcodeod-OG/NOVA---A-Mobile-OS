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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext DownloadQueryResult(emptyList())
            }

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
            DownloadQueryResult(items)
        }
}
