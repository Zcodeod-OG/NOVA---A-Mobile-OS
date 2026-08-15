package com.nova.runtime.storage.search

import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Photo search combining Room OCR cache and optional MediaStore queries (DPS §7, AIS §4.3).
 */
class PhotoSearchService(
    private val photoDao: PhotoDao,
    private val mediaStoreQuery: MediaStoreQueryPort,
    private val logger: NovaLogger,
) {
    suspend fun search(request: SearchRequest, traceId: UUID): SearchPage<PhotoSearchHit> {
        val normalizedQuery = request.query.trim().lowercase()
        var page: SearchPage<PhotoSearchHit> = emptyPage(request)

        val latencyMs = measureTimeMillis {
            val roomHits = photoDao.searchByOcrPaged(normalizedQuery, request.limit, request.offset)
                .map { it.toHit(PhotoMatchSource.ROOM_OCR) }

            val mediaHits = queryMediaStoreMatches(normalizedQuery)
            val merged = mergeHits(roomHits, mediaHits)
                .sortedByDescending { it.takenAt }
                .drop(request.offset)
                .take(request.limit)

            val totalCount = (photoDao.countByOcr(normalizedQuery) + mediaHits.size)
                .coerceAtLeast(merged.size)

            page = SearchPage(
                items = merged,
                totalCount = totalCount,
                query = request.query.trim(),
                limit = request.limit,
                offset = request.offset,
            )
        }

        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "Photo search completed",
            traceId = traceId,
            durationMs = latencyMs,
            metadata = mapOf(
                "query" to request.query.trim(),
                "count" to page.count.toString(),
                "totalCount" to page.totalCount.toString(),
            ),
        )

        return page
    }

    private suspend fun queryMediaStoreMatches(query: String): List<PhotoSearchHit> {
        val mediaResult = mediaStoreQuery.queryImages(limit = SearchRequest.MAX_LIMIT)
        return mediaResult.items
            .filter { item ->
                item.displayName?.lowercase()?.contains(query) == true ||
                    item.uri.lowercase().contains(query)
            }
            .map { item ->
                PhotoSearchHit(
                    id = stablePhotoId(item.uri),
                    uri = item.uri,
                    takenAt = item.dateAdded,
                    ocrText = null,
                    matchSource = PhotoMatchSource.MEDIA_STORE,
                )
            }
    }

    private fun mergeHits(
        roomHits: List<PhotoSearchHit>,
        mediaHits: List<PhotoSearchHit>,
    ): List<PhotoSearchHit> {
        val byUri = linkedMapOf<String, PhotoSearchHit>()
        roomHits.forEach { hit -> byUri[hit.uri] = hit }
        mediaHits.forEach { mediaHit ->
            val existing = byUri[mediaHit.uri]
            byUri[mediaHit.uri] = if (existing != null) {
                existing.copy(matchSource = PhotoMatchSource.BOTH)
            } else {
                mediaHit
            }
        }
        return byUri.values.toList()
    }

    private fun PhotoEntity.toHit(source: PhotoMatchSource): PhotoSearchHit =
        PhotoSearchHit(
            id = id,
            uri = uri,
            takenAt = takenAt,
            ocrText = ocrText,
            matchSource = source,
        )

    private fun stablePhotoId(uri: String): UUID =
        UUID.nameUUIDFromBytes(uri.toByteArray())

    private fun emptyPage(request: SearchRequest): SearchPage<PhotoSearchHit> =
        SearchPage(
            items = emptyList(),
            totalCount = 0,
            query = request.query.trim(),
            limit = request.limit,
            offset = request.offset,
        )
}
