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
        var effectiveQuery = normalizedQuery

        val latencyMs = measureTimeMillis {
            val dateTarget = DocumentDateIntelligence.resolveDateTarget(normalizedQuery)
            val mealType = DocumentDateIntelligence.detectMealType(normalizedQuery)
            effectiveQuery = DocumentDateIntelligence.stripQueryNoise(normalizedQuery)
            // Prefer exact phrase; if empty, treat spaces as wildcards so
            // "mess menu" still matches names like "mess-menu.pdf".
            var entities = documentDao.searchFullText(effectiveQuery, request.limit, request.offset)
            var totalCount = documentDao.countFullText(effectiveQuery)
            if (entities.isEmpty() && effectiveQuery.contains(Regex("\\s+"))) {
                val fuzzyQuery = effectiveQuery.replace(Regex("\\s+"), "%")
                entities = documentDao.searchFullText(fuzzyQuery, request.limit, request.offset)
                totalCount = documentDao.countFullText(fuzzyQuery)
                if (entities.isNotEmpty()) {
                    effectiveQuery = fuzzyQuery
                }
            }
            // Token fallback: "what is menu" → try "menu" / "mess"
            if (entities.isEmpty()) {
                val tokens = effectiveQuery.split(Regex("\\s+"))
                    .filter { it.length > 2 && it !in DOCUMENT_QUESTION_STOP }
                for (token in tokens) {
                    entities = documentDao.searchFullText(token, request.limit, request.offset)
                    totalCount = documentDao.countFullText(token)
                    if (entities.isNotEmpty()) {
                        effectiveQuery = token
                        break
                    }
                }
            }
            page = SearchPage(
                items = rankDocumentHits(entities, effectiveQuery, normalizedQuery),
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
                "effectiveQuery" to effectiveQuery,
                "count" to page.count.toString(),
                "totalCount" to page.totalCount.toString(),
            ),
        )

        return page
    }

    private fun rankDocumentHits(
        entities: List<com.nova.runtime.storage.entities.DocumentEntity>,
        effectiveQuery: String,
        normalizedQuery: String,
    ): List<DocumentSearchHit> {
        if (entities.isEmpty()) return emptyList()
        val dateTarget = DocumentDateIntelligence.resolveDateTarget(normalizedQuery)
        val mealType = DocumentDateIntelligence.detectMealType(normalizedQuery)
        val tokens = DocumentContentRanker.tokenize(effectiveQuery.ifBlank { normalizedQuery })
        return DocumentContentRanker.rankDocuments(
            documents = entities,
            tokens = tokens,
            query = normalizedQuery,
        ).map { (entity, score) ->
            entity.toHit(dateTarget, mealType, normalizedQuery, score)
        }
    }

    private fun DocumentEntity.toHit(
        dateTarget: DocumentDateIntelligence.DateTarget?,
        mealType: String?,
        query: String,
        score: Float = 1f,
    ): DocumentSearchHit =
        DocumentSearchHit(
            id = id,
            path = path,
            name = name,
            extension = extension,
            mimeType = mimeType,
            modifiedAt = modifiedAt,
            score = score,
            contentSnippet = contentText?.let { content ->
                DocumentDateIntelligence.extractScopedSnippet(
                    content = content,
                    target = dateTarget,
                    mealType = mealType,
                    query = query,
                )
            },
            summary = summary,
            contentCharCount = contentText?.length ?: 0,
            contentExtractStatus = contentExtractStatus,
        )

    private fun emptyPage(request: SearchRequest): SearchPage<DocumentSearchHit> =
        SearchPage(
            items = emptyList(),
            totalCount = 0,
            query = request.query.trim(),
            limit = request.limit,
            offset = request.offset,
        )

    private companion object {
        private val DOCUMENT_QUESTION_STOP = setOf(
            "what", "whats", "show", "tell", "find", "search", "look", "the", "for", "and",
        )
    }
}
