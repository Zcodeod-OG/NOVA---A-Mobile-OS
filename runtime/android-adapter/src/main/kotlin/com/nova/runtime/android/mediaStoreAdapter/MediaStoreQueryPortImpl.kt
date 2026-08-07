package com.nova.runtime.android.mediaStoreAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.storage.search.MediaImageItem
import com.nova.runtime.storage.search.MediaImageQueryResult
import com.nova.runtime.storage.search.MediaStoreQueryPort
import java.util.UUID

/** Bridges [MediaStoreAdapter] to the storage-layer [MediaStoreQueryPort]. */
class MediaStoreQueryPortImpl(
    private val mediaStoreAdapter: MediaStoreAdapter,
) : MediaStoreQueryPort {
    override suspend fun queryImages(limit: Int, offset: Int): MediaImageQueryResult =
        queryMedia(MediaStoreOperations.QUERY_IMAGES, limit, offset)

    override suspend fun queryVideos(limit: Int, offset: Int): MediaImageQueryResult =
        queryMedia(MediaStoreOperations.QUERY_VIDEOS, limit, offset)

    override suspend fun queryAudio(limit: Int, offset: Int): MediaImageQueryResult =
        queryMedia(MediaStoreOperations.QUERY_AUDIO, limit, offset)

    private suspend fun queryMedia(
        operation: String,
        limit: Int,
        offset: Int,
    ): MediaImageQueryResult {
        val result =
            mediaStoreAdapter.execute(
                operation = operation,
                parameters =
                    mapOf(
                        "limit" to limit.coerceAtLeast(1).toString(),
                        "offset" to offset.coerceAtLeast(0).toString(),
                    ),
                traceId = UUID.randomUUID(),
            )
        return when (result) {
            is CapabilityResult.Success -> parseItems(result.output)
            is CapabilityResult.Failure -> MediaImageQueryResult(emptyList())
        }
    }

    private fun parseItems(output: Map<String, String>): MediaImageQueryResult {
        val rawItems = output["items"].orEmpty()
        if (rawItems.isBlank()) return MediaImageQueryResult(emptyList())

        val items =
            rawItems.split("|").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size < 6) return@mapNotNull null
                MediaImageItem(
                    mediaId = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    uri = parts[1],
                    displayName = parts[2].ifBlank { null },
                    mimeType = parts[3].ifBlank { null },
                    dateAdded = parts[4].toLongOrNull() ?: 0L,
                    size = parts[5].toLongOrNull() ?: 0L,
                )
            }
        return MediaImageQueryResult(items)
    }
}
