package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.ImageEmbeddingGenerator
import com.nova.runtime.ai.native.indexing.EmbeddingMetadata
import com.nova.runtime.ai.native.ingestion.FullDeviceIndexer
import com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.ContentExtractStatus
import com.nova.runtime.storage.search.DocumentContentAnswerExtractor
import com.nova.runtime.storage.search.DocumentContentNormalizer
import com.nova.runtime.storage.search.DocumentContentRanker
import com.nova.runtime.storage.search.DocumentDateIntelligence
import com.nova.runtime.storage.search.SearchPage
import com.nova.runtime.storage.search.SearchRequest
import com.nova.runtime.storage.search.SemanticSearchHit
import com.nova.runtime.storage.vector.VectorSearchRequest
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Semantic KNN search over indexed photo and document embeddings (DPS §7).
 *
 * Document retrieval is two-stage:
 * - Stage A: rank by hybrid score on (name + summary) discovery vectors + keyword/filename boosts
 * - Stage B: snippets/answers extracted from full [com.nova.runtime.storage.entities.DocumentEntity.contentText]
 */
class SemanticSearchService(
    private val embeddingGenerator: EmbeddingGenerator,
    private val imageEmbeddingGenerator: ImageEmbeddingGenerator,
    private val vectorIndex: CosineVectorIndex,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val documentDao: DocumentDao,
    private val searchIndexPipeline: SearchIndexPipeline,
    private val mediaStoreIngestionService: MediaStoreIngestionService,
    private val fullDeviceIndexer: FullDeviceIndexer? = null,
    private val logger: NovaLogger,
) {
    suspend fun search(
        request: SearchRequest,
        traceId: UUID,
        objectTypes: Set<String> = DEFAULT_OBJECT_TYPES,
    ): SearchPage<SemanticSearchHit> {
        val normalizedQuery = request.query.trim()
        // Date-intelligent retrieval: "todays mess menu" / "this month's mess menu"
        // resolve to a target; date words are stripped for embedding so matching
        // focuses on the subject ("mess menu").
        val dateTarget = DocumentDateIntelligence.resolveDateTarget(normalizedQuery)
        val mealType = DocumentDateIntelligence.detectMealType(normalizedQuery)
        val embeddableQuery = DocumentDateIntelligence.stripQueryNoise(normalizedQuery)
        val wantsRecency = dateTarget != null ||
            DocumentDateIntelligence.wantsRecencyPreference(normalizedQuery)
        var page: SearchPage<SemanticSearchHit> = emptyPage(request)

        val latencyMs = measureTimeMillis {
            if (request.indexOnQuery) {
                fullDeviceIndexer?.runPrioritySync() ?: mediaStoreIngestionService.ensureSynced()
                searchIndexPipeline.ensureIndexed(
                    SearchIndexPipeline.IndexRequest(
                        indexPhotos = OBJECT_TYPE_PHOTO in objectTypes,
                        indexDocuments = OBJECT_TYPE_DOCUMENT in objectTypes,
                        documentLimit = PRIORITY_INDEX_DOCUMENT_LIMIT,
                    ),
                )
            }

            val fetchK = (request.offset + request.limit).coerceAtMost(SearchRequest.MAX_LIMIT)
            val rawHits = searchIndexedObjects(embeddableQuery, traceId, objectTypes, fetchK)
            forceExtractTopDocumentHits(rawHits)
            val rankedHits = applyHybridDocumentRanking(
                hits = if (wantsRecency) {
                    preferDocumentsForRecency(rawHits, dateTarget)
                } else {
                    rawHits
                },
                query = normalizedQuery,
                embeddableQuery = embeddableQuery,
                preferRecency = wantsRecency,
            )
            val candidateWindow = (request.limit + DOCUMENT_ANSWER_CANDIDATE_EXTRA)
                .coerceAtMost(SearchRequest.MAX_LIMIT)
            val resolved = rankedHits
                .drop(request.offset)
                .take(candidateWindow)
                .mapNotNull { hit ->
                    if (hit.objectType == OBJECT_TYPE_DOCUMENT) {
                        documentRepository.getById(hit.objectId)?.let { doc ->
                            if (ContentExtractStatus.needsForceExtraction(
                                    doc.contentExtractStatus,
                                    doc.contentText,
                                )
                            ) {
                                searchIndexPipeline.indexDocument(doc, forceExtract = true)
                            }
                        }
                    }
                    resolveHit(hit.objectId, hit.objectType, hit.score, dateTarget, mealType, normalizedQuery)
                }
            val hits = preferGroundedDocumentAnswers(resolved, normalizedQuery)
                .take(request.limit)

            page = SearchPage(
                items = hits,
                totalCount = rawHits.size,
                query = normalizedQuery,
                limit = request.limit,
                offset = request.offset,
            )
        }

        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "Semantic search completed",
            traceId = traceId,
            durationMs = latencyMs,
            metadata = mapOf(
                "query" to normalizedQuery,
                "effectiveQuery" to embeddableQuery,
                "count" to page.count.toString(),
                "totalCount" to page.totalCount.toString(),
                "objectTypes" to objectTypes.joinToString(","),
                "wantsRecency" to wantsRecency.toString(),
            ),
        )

        return page
    }

    private suspend fun searchIndexedObjects(
        query: String,
        traceId: UUID,
        objectTypes: Set<String>,
        fetchK: Int,
    ): List<RankedObjectHit> {
        if (query.isBlank()) return emptyList()

        val hits = mutableListOf<RankedObjectHit>()
        val k = fetchK.coerceAtLeast(1)

        if (OBJECT_TYPE_DOCUMENT in objectTypes) {
            embedTextQuery(query, traceId)?.let { queryVector ->
                // Prefer Stage A summary embeddings; fall back to untyped document vectors
                // so pre-migration indexes still resolve until backfill completes.
                val summaryHits = vectorIndex.search(
                    VectorSearchRequest(
                        queryVector = queryVector,
                        k = k,
                        metadataFilter = mapOf(
                            EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_DOCUMENT,
                            EmbeddingMetadata.EMBEDDING_KIND to EmbeddingMetadata.KIND_SUMMARY,
                        ),
                    ),
                )
                val documentHits = summaryHits.ifEmpty {
                    vectorIndex.search(
                        VectorSearchRequest(
                            queryVector = queryVector,
                            k = k,
                            metadataFilter = mapOf(EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_DOCUMENT),
                        ),
                    )
                }
                hits += documentHits.map { RankedObjectHit(it.objectId, it.objectType, it.score) }
            }
            hits += fetchContentFtsHits(query, k)
        }

        if (OBJECT_TYPE_PHOTO in objectTypes) {
            embedImageQuery(query, traceId)?.let { queryVector ->
                hits += vectorIndex.search(
                    VectorSearchRequest(
                        queryVector = queryVector,
                        k = k,
                        metadataFilter = mapOf(
                            EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_PHOTO,
                            EmbeddingMetadata.EMBEDDING_KIND to EmbeddingMetadata.KIND_IMAGE,
                        ),
                    ),
                ).map { RankedObjectHit(it.objectId, it.objectType, it.score) }
            }

            embedTextQuery(query, traceId)?.let { queryVector ->
                hits += vectorIndex.search(
                    VectorSearchRequest(
                        queryVector = queryVector,
                        k = k,
                        metadataFilter = mapOf(
                            EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_PHOTO,
                            EmbeddingMetadata.EMBEDDING_KIND to EmbeddingMetadata.KIND_OCR,
                        ),
                    ),
                ).map { RankedObjectHit(it.objectId, it.objectType, it.score) }
            }
        }

        return hits
            .groupBy { it.objectId to it.objectType }
            .map { (_, grouped) -> grouped.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(k)
    }

    /** Merges strong FTS hits over indexed [contentText] so body keywords can beat filename-only docs. */
    private suspend fun fetchContentFtsHits(query: String, k: Int): List<RankedObjectHit> {
        val tokens = DocumentContentRanker.tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val seen = mutableSetOf<UUID>()
        val hits = mutableListOf<RankedObjectHit>()
        for (token in tokens) {
            documentDao.searchFullText(token, limit = k, offset = 0).forEach { document ->
                if (!seen.add(document.id)) return@forEach
                val contentMatches = tokens.count { document.contentText.orEmpty().lowercase().contains(it) }
                if (contentMatches == 0) return@forEach
                val score = DocumentContentRanker.scoreDocument(
                    document = document,
                    tokens = tokens,
                    semanticScore = FTS_BASE_SCORE + contentMatches * FTS_CONTENT_HIT_WEIGHT,
                    query = query,
                )
                hits += RankedObjectHit(
                    objectId = document.id,
                    objectType = OBJECT_TYPE_DOCUMENT,
                    score = score,
                    modifiedAt = document.modifiedAt,
                    hasContent = !document.contentText.isNullOrBlank(),
                )
            }
        }
        return hits.sortedByDescending { it.score }.take(k)
    }

    /** Force re-extract on the top semantic/FTS document hits before hybrid re-ranking. */
    private suspend fun forceExtractTopDocumentHits(hits: List<RankedObjectHit>) {
        hits.filter { it.objectType == OBJECT_TYPE_DOCUMENT }
            .take(FORCE_EXTRACT_TOP_K)
            .forEach { hit ->
                val doc = documentRepository.getById(hit.objectId) ?: return@forEach
                if (ContentExtractStatus.needsForceExtraction(doc.contentExtractStatus, doc.contentText)) {
                    searchIndexPipeline.indexDocument(doc, forceExtract = true)
                }
            }
    }

    private suspend fun embedTextQuery(query: String, traceId: UUID): FloatArray? =
        when (val result = embeddingGenerator.embed(query)) {
            is EmbeddingResult.Success -> result.vector
            is EmbeddingResult.Failure -> {
                logger.warn(
                    module = RuntimeModule.STORAGE.name,
                    message = "Query embedding failed: ${result.message}",
                    traceId = traceId,
                )
                null
            }
        }

    private suspend fun embedImageQuery(query: String, traceId: UUID): FloatArray? =
        when (val result = imageEmbeddingGenerator.embedQuery(query)) {
            is EmbeddingResult.Success -> result.vector
            is EmbeddingResult.Failure -> {
                logger.warn(
                    module = RuntimeModule.STORAGE.name,
                    message = "Image query embedding failed: ${result.message}",
                    traceId = traceId,
                )
                null
            }
        }

    /**
     * Hybrid Stage A ranking: semantic score + filename token boost + summary/content keyword boost
     * + subject preferences (menu / timetable) + content presence. When [preferRecency] is set,
     * filename subject boosts are softened so an old well-named file cannot bury a newer one.
     */
    private suspend fun applyHybridDocumentRanking(
        hits: List<RankedObjectHit>,
        query: String,
        embeddableQuery: String,
        preferRecency: Boolean,
    ): List<RankedObjectHit> {
        if (hits.isEmpty()) return hits
        val lowerQuery = "$query $embeddableQuery".lowercase()
        val tokens = tokenizeQuery(embeddableQuery.ifBlank { query })
        val wantsMenu = "menu" in lowerQuery || "mess" in lowerQuery
        val wantsTimetable = DocumentDateIntelligence.stripQueryNoise(lowerQuery).let { subject ->
            "timetable" in lowerQuery || "timetable" in subject ||
                listOf("lecture", "lec", "slots", "schedule").any { it in lowerQuery }
        }
        val nameStrongBoost = if (preferRecency) MENU_NAME_STRONG_BOOST_RECENCY else MENU_NAME_STRONG_BOOST
        val nameBoost = if (preferRecency) MENU_NAME_BOOST_RECENCY else MENU_NAME_BOOST
        return hits
            .map { hit ->
                if (hit.objectType != OBJECT_TYPE_DOCUMENT) return@map hit
                val document = documentRepository.getById(hit.objectId) ?: return@map hit
                val name = document.name.lowercase()
                val nameTokens = normalizeFilenameTokens(document.name)
                val summary = document.summary.orEmpty().lowercase()
                val content = document.contentText.orEmpty().lowercase()
                var boost = 0f

                for (token in tokens) {
                    if (token in nameTokens || name.contains(token)) {
                        boost += DocumentContentRanker.FILENAME_TOKEN_WEIGHT
                    } else if (summary.contains(token)) {
                        boost += DocumentContentRanker.SUMMARY_TOKEN_WEIGHT
                    } else if (content.contains(token)) {
                        boost += DocumentContentRanker.CONTENT_TOKEN_WEIGHT
                    }
                }

                if (wantsMenu) {
                    if ("mess" in name && "menu" in name) boost += nameStrongBoost
                    else if ("menu" in name || "mess" in name) boost += nameBoost
                    if ("mess" in summary && "menu" in summary) boost += MENU_CONTENT_BOOST
                    else if ("mess" in content && "menu" in content) boost += MENU_CONTENT_BOOST
                    if (listOf("breakfast", "lunch", "dinner").count { it in content } >= 2) {
                        boost += MEAL_STRUCTURE_BOOST
                    }
                }
                if (wantsTimetable) {
                    if ("timetable" in name || "schedule" in name) boost += nameStrongBoost
                    else if (listOf("timetable", "lecture", "class").any { it in name }) {
                        boost += nameBoost
                    }
                    if ("timetable" in summary || "timetable" in content ||
                        listOf("monday", "tuesday", "wednesday").count { it in content } >= 2
                    ) {
                        boost += MENU_CONTENT_BOOST
                    }
                    // Prefer a real semester grid (course codes, many day/time rows) over a
                    // tiny demo stub that merely has "WEEKLY TIMETABLE" in the filename.
                    val structure = DocumentContentNormalizer.timetableStructureScore(
                        document.contentText,
                    )
                    boost += (structure * TIMETABLE_STRUCTURE_BOOST_SCALE)
                        .coerceAtMost(TIMETABLE_STRUCTURE_BOOST_CAP)
                    // Soft-demote toy stubs: short plain schedules that win only on recency.
                    if (structure < TIMETABLE_STRUCTURE_TOY_THRESHOLD &&
                        document.contentText.orEmpty().length < TIMETABLE_TOY_MAX_CHARS
                    ) {
                        boost -= TIMETABLE_TOY_PENALTY
                    }
                }
                // Prefer docs with real extracted body over empty/stale summary-only rows.
                if (content.isNotBlank()) {
                    boost += DocumentContentRanker.CONTENT_PRESENT_BOOST
                    if (tokens.size >= 2 && tokens.all { content.contains(it) }) {
                        boost += DocumentContentRanker.CONTENT_PHRASE_BOOST
                    }
                }
                hit.copy(
                    score = hit.score + boost,
                    modifiedAt = document.modifiedAt,
                    hasContent = content.isNotBlank(),
                    structureScore = if (wantsTimetable) {
                        DocumentContentNormalizer.timetableStructureScore(document.contentText)
                    } else {
                        0f
                    },
                )
            }
            .sortedWith(
                compareByDescending<RankedObjectHit> { it.score }
                    .thenByDescending { it.structureScore }
                    .thenByDescending { it.modifiedAt }
                    .thenByDescending { it.hasContent },
            )
    }

    /**
     * Boosts documents by recency and optional month-name match. Used for day/month
     * date targets and for "latest/current/timetable" style queries with no date word.
     */
    private suspend fun preferDocumentsForRecency(
        hits: List<RankedObjectHit>,
        dateTarget: DocumentDateIntelligence.DateTarget?,
    ): List<RankedObjectHit> {
        if (hits.isEmpty()) return hits
        val now = System.currentTimeMillis()
        val monthStartMillis = dateTarget?.yearMonth
            ?.atDay(1)
            ?.atStartOfDay(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
        val monthName = dateTarget?.date?.month
            ?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            ?.lowercase()
        return hits
            .map { hit ->
                if (hit.objectType != OBJECT_TYPE_DOCUMENT) return@map hit
                val document = documentRepository.getById(hit.objectId) ?: return@map hit
                val boost = when {
                    dateTarget?.monthScoped == true && monthStartMillis != null -> {
                        var monthBoost = 0f
                        if (document.modifiedAt >= monthStartMillis) {
                            monthBoost += RECENCY_BOOST_THIS_MONTH
                        }
                        val haystack = listOfNotNull(document.name, document.summary, document.contentText)
                            .joinToString(" ")
                            .lowercase()
                        if (monthName != null && monthName in haystack) {
                            monthBoost += MONTH_NAME_CONTENT_BOOST
                        }
                        monthBoost
                    }
                    else -> recencyBoost(now - document.modifiedAt)
                }
                hit.copy(
                    score = hit.score + boost,
                    modifiedAt = document.modifiedAt,
                    hasContent = !document.contentText.isNullOrBlank(),
                )
            }
            .sortedWith(
                compareByDescending<RankedObjectHit> { it.score }
                    .thenByDescending { it.modifiedAt },
            )
    }

    private fun recencyBoost(ageMillis: Long): Float {
        val ageDays = ageMillis.coerceAtLeast(0L) / MILLIS_PER_DAY
        return when {
            ageDays <= 1 -> RECENCY_BOOST_VERY_FRESH
            ageDays <= 7 -> RECENCY_BOOST_FRESH
            ageDays <= 30 -> RECENCY_BOOST_THIS_MONTH_AGE
            ageDays <= 90 -> RECENCY_BOOST_QUARTER
            else -> 0f
        }
    }

    private suspend fun resolveHit(
        objectId: UUID,
        objectType: String,
        score: Float,
        dateTarget: DocumentDateIntelligence.DateTarget? = null,
        mealType: String? = null,
        query: String? = null,
    ): SemanticSearchHit? =
        when (objectType) {
            OBJECT_TYPE_PHOTO -> {
                val photo = photoRepository.getById(objectId) ?: return null
                SemanticSearchHit(
                    objectId = objectId,
                    objectType = objectType,
                    score = score,
                    title = photo.uri.substringAfterLast('/'),
                    snippet = photo.ocrText?.take(120),
                )
            }
            OBJECT_TYPE_DOCUMENT -> {
                val document = documentRepository.getById(objectId) ?: return null
                SemanticSearchHit(
                    objectId = objectId,
                    objectType = objectType,
                    score = score,
                    title = document.name,
                    snippet = document.path,
                    // Stage B: always extract answers from full contentText, never the summary.
                    contentSnippet = document.contentText?.let { content ->
                        DocumentDateIntelligence.extractScopedSnippet(
                            content = content,
                            target = dateTarget,
                            mealType = mealType,
                            query = query,
                        )
                    },
                    summary = document.summary,
                    modifiedAt = document.modifiedAt,
                    contentCharCount = document.contentText?.length ?: 0,
                    contentExtractStatus = document.contentExtractStatus,
                )
            }
            else -> null
        }

    /**
     * Among near-top document hits, surface one with a grounded extractive snippet first.
     * Does not invent text — only reorders hits that already have real contentSnippet.
     * Never promotes a tiny demo stub when the top hit is a richer file that simply
     * lacks today's column (honest "couldn't extract" beats fabricated Physics rows).
     */
    private fun preferGroundedDocumentAnswers(
        hits: List<SemanticSearchHit>,
        query: String,
    ): List<SemanticSearchHit> {
        if (hits.size <= 1) return hits
        val wantsTimetable = DocumentContentAnswerExtractor.isTimetableQuery(query)
        if (!wantsTimetable) return hits
        val top = hits.first()
        if (!top.contentSnippet.isNullOrBlank() &&
            DocumentContentNormalizer.isGroundedAnswerable(top.contentSnippet)
        ) {
            return hits
        }
        // Promote only a substantial schedule excerpt (course-code / multi-day grid),
        // never a short toy "WEEKLY TIMETABLE" stub.
        val candidate = hits.firstOrNull { hit ->
            val snippet = hit.contentSnippet
            !snippet.isNullOrBlank() &&
                DocumentContentNormalizer.isGroundedAnswerable(snippet) &&
                DocumentContentNormalizer.timetableStructureScore(snippet) >=
                TIMETABLE_PROMOTE_MIN_STRUCTURE
        } ?: return hits
        if (candidate.objectId == top.objectId) return hits
        return listOf(candidate) + hits.filterNot { it.objectId == candidate.objectId }
    }

    private fun emptyPage(request: SearchRequest): SearchPage<SemanticSearchHit> =
        SearchPage(
            items = emptyList(),
            totalCount = 0,
            query = request.query.trim(),
            limit = request.limit,
            offset = request.offset,
        )

    private data class RankedObjectHit(
        val objectId: UUID,
        val objectType: String,
        val score: Float,
        val modifiedAt: Long = 0L,
        val hasContent: Boolean = false,
        val structureScore: Float = 0f,
    )

    companion object {
        const val OBJECT_TYPE_PHOTO = SearchIndexPipeline.OBJECT_TYPE_PHOTO
        const val OBJECT_TYPE_DOCUMENT = SearchIndexPipeline.OBJECT_TYPE_DOCUMENT
        val DEFAULT_OBJECT_TYPES = setOf(OBJECT_TYPE_PHOTO, OBJECT_TYPE_DOCUMENT)

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        /** Strong enough to beat a softened filename boost on stale files. */
        private const val RECENCY_BOOST_VERY_FRESH = 1.8f
        private const val RECENCY_BOOST_FRESH = 1.2f
        private const val RECENCY_BOOST_THIS_MONTH_AGE = 0.6f
        private const val RECENCY_BOOST_QUARTER = 0.25f
        private const val RECENCY_BOOST_THIS_MONTH = 0.25f
        private const val MONTH_NAME_CONTENT_BOOST = 0.15f
        private const val MENU_NAME_STRONG_BOOST = 1.5f
        private const val MENU_NAME_STRONG_BOOST_RECENCY = 0.7f
        private const val MENU_NAME_BOOST = 0.8f
        private const val MENU_NAME_BOOST_RECENCY = 0.4f
        private const val MENU_CONTENT_BOOST = 0.4f
        private const val MEAL_STRUCTURE_BOOST = 0.3f
        private const val FTS_BASE_SCORE = 0.85f
        private const val FTS_CONTENT_HIT_WEIGHT = 0.35f
        /** On-query indexing batch — larger than background default so Downloads/PDFs appear quickly. */
        private const val PRIORITY_INDEX_DOCUMENT_LIMIT = 75
        private const val FORCE_EXTRACT_TOP_K = 8
        /** Scales [DocumentContentNormalizer.timetableStructureScore] into ranking space. */
        private const val TIMETABLE_STRUCTURE_BOOST_SCALE = 0.08f
        private const val TIMETABLE_STRUCTURE_BOOST_CAP = 3.5f
        private const val TIMETABLE_STRUCTURE_TOY_THRESHOLD = 12f
        private const val TIMETABLE_TOY_MAX_CHARS = 400
        private const val TIMETABLE_TOY_PENALTY = 2.2f
        /** Extra near-top docs inspected so grounded snippets can surface over empty OCR. */
        private const val DOCUMENT_ANSWER_CANDIDATE_EXTRA = 8
        /** Min structure score before a later hit may replace an empty top answer. */
        private const val TIMETABLE_PROMOTE_MIN_STRUCTURE = 18f

        private val QUERY_STOPWORDS = setOf(
            "what", "whats", "what's", "show", "tell", "find", "search", "look", "the", "for",
            "and", "from", "me", "my", "a", "an", "is", "are", "to", "of", "on", "in", "between",
            "please", "send", "share", "document", "file", "pdf", "today", "todays", "today's",
            "this", "month", "months", "month's", "latest", "current", "recent", "newest", "new",
        )

        fun tokenizeQuery(query: String): List<String> =
            query.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 2 && it !in QUERY_STOPWORDS }

        fun normalizeFilenameTokens(name: String): Set<String> =
            name.lowercase()
                .substringBeforeLast('.')
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 1 }
                .toSet()
    }
}
