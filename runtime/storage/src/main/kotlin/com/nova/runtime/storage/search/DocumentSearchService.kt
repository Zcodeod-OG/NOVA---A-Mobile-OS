package com.nova.runtime.storage.search

import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlin.system.measureTimeMillis

/** Full-text and metadata search over indexed documents (DPS §7). */
class DocumentSearchService(
    private val documentDao: DocumentDao,
    private val logger: NovaLogger,
) {
    suspend fun search(request: SearchRequest, traceId: UUID): SearchPage<DocumentSearchHit> {
        val normalizedQuery = request.query.trim()
        var page: SearchPage<DocumentSearchHit> = emptyPage(request)

        val latencyMs = measureTimeMillis {
            val entities = documentDao.searchFullText(normalizedQuery, request.limit, request.offset)
            val totalCount = documentDao.countFullText(normalizedQuery)
            page = SearchPage(
                items = entities.map { it.toHit() },
                totalCount = totalCount,
                query = normalizedQuery,
                limit = request.limit,
                offset = request.offset,
            )
        }

        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "Document search completed",
            traceId = traceId,
            durationMs = latencyMs,
            metadata = mapOf(
                "query" to normalizedQuery,
                "count" to page.count.toString(),
                "totalCount" to page.totalCount.toString(),
            ),
        )

        return page
    }

    private fun DocumentEntity.toHit(): DocumentSearchHit =
        DocumentSearchHit(
            id = id,
            path = path,
            name = name,
            extension = extension,
            mimeType = mimeType,
            modifiedAt = modifiedAt,
        )

    private fun emptyPage(request: SearchRequest): SearchPage<DocumentSearchHit> =
        SearchPage(
            items = emptyList(),
            totalCount = 0,
            query = request.query.trim(),
            limit = request.limit,
            offset = request.offset,
        )
}
