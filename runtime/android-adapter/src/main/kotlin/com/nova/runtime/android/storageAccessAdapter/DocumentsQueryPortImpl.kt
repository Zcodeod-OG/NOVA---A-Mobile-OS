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

/** Queries MediaStore Files for non-media documents (PDFs, office files, etc.). */
class DocumentsQueryPortImpl(
    private val context: Context,
) : DocumentsQueryPort {
    override suspend fun queryDocuments(limit: Int, offset: Int): DownloadQueryResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext DownloadQueryResult(emptyList())
            }

            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection =
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.SIZE,
                )
            val selection =
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val selectionArgs =
                arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString())
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
