package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.ai.native.search.GroundedDocumentAnswerService
import com.nova.runtime.ai.native.search.SemanticSearchService
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.search.ContentExtractStatus
import com.nova.runtime.storage.search.DocumentContentAnswerExtractor
import com.nova.runtime.storage.search.DocumentContentNormalizer
import com.nova.runtime.storage.search.DocumentDateIntelligence
import com.nova.runtime.storage.search.DocumentSearchService
import com.nova.runtime.storage.search.SearchResultCodec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DocumentSearchCapabilityProvider(
    private val documentSearchService: DocumentSearchService,
    private val semanticSearchService: SemanticSearchService,
    private val documentRepository: DocumentRepository,
    private val groundedAnswerService: GroundedDocumentAnswerService = GroundedDocumentAnswerService(),
) : AbstractSearchCapabilityProvider(
    providerId = PROVIDER_ID,
    capabilityType = CAPABILITY_TYPE,
    version = VERSION,
    description = "Document search with semantic vector KNN and keyword fallback",
) {
    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun executeSearch(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        val searchRequest = parseSearchRequest(request.parameters)
        val answerMode = request.parameters["answerMode"] ?: GroundedDocumentAnswerService.ANSWER_MODE_QA
        val displayMode = request.parameters["displayMode"] ?: DocumentDateIntelligence.DISPLAY_MODE_SCOPED
        val maxDisplayChars = request.parameters["maxDisplayChars"]?.toIntOrNull()
            ?: if (displayMode == DocumentDateIntelligence.DISPLAY_MODE_VERBATIM) 128_000 else DocumentDateIntelligence.DEFAULT_EXTRACT_CHARS

        val semanticPage = semanticSearchService.search(
            request = searchRequest,
            traceId = request.traceId,
            objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
        )
        if (semanticPage.items.isNotEmpty()) {
            val top = semanticPage.items.first()
            val topDoc = documentRepository.getById(top.objectId)
            val encoded = SearchResultCodec.encodeSemanticHits(semanticPage) + searchMetadata(
                searchMode = SEARCH_MODE_SEMANTIC,
            ) + matchDebug(
                name = top.title,
                summary = top.summary,
                modifiedAt = top.modifiedAt,
                contentChars = top.contentCharCount.takeIf { it > 0 }
                    ?: (topDoc?.contentText?.length ?: 0),
                extractStatus = top.contentExtractStatus ?: topDoc?.contentExtractStatus,
            )
            return CapabilityExecutionResponse.Success(
                ensureAnswerMessage(
                    encoded = encoded,
                    query = searchRequest.query,
                    contentSnippet = top.contentSnippet,
                    sourceFileName = top.title,
                    sourceModifiedAt = top.modifiedAt,
                    answerMode = answerMode,
                    displayMode = displayMode,
                    maxDisplayChars = maxDisplayChars,
                    documentContent = topDoc?.contentText,
                ),
            )
        }

        val keywordPage = documentSearchService.search(searchRequest, request.traceId)
        val topKeyword = keywordPage.items.firstOrNull()
        val keywordDoc = topKeyword?.id?.let { documentRepository.getById(it) }
        val encoded = SearchResultCodec.encodeDocumentHits(keywordPage) + searchMetadata(
            searchMode = SEARCH_MODE_KEYWORD,
        ) + matchDebug(
            name = topKeyword?.name,
            summary = topKeyword?.summary,
            modifiedAt = topKeyword?.modifiedAt,
            contentChars = topKeyword?.contentCharCount?.takeIf { it > 0 }
                ?: (keywordDoc?.contentText?.length ?: 0),
            extractStatus = topKeyword?.contentExtractStatus ?: keywordDoc?.contentExtractStatus,
        )
        return CapabilityExecutionResponse.Success(
            when {
                keywordPage.items.isEmpty() -> encoded + emptyResultMessage(searchRequest.query)
                else -> ensureAnswerMessage(
                    encoded = encoded,
                    query = searchRequest.query,
                    contentSnippet = topKeyword?.contentSnippet,
                    sourceFileName = topKeyword?.name,
                    sourceModifiedAt = topKeyword?.modifiedAt,
                    answerMode = answerMode,
                    displayMode = displayMode,
                    maxDisplayChars = maxDisplayChars,
                    documentContent = keywordDoc?.contentText,
                )
            },
        )
    }

    private suspend fun ensureAnswerMessage(
        encoded: Map<String, String>,
        query: String,
        contentSnippet: String?,
        sourceFileName: String?,
        sourceModifiedAt: Long?,
        answerMode: String = GroundedDocumentAnswerService.ANSWER_MODE_QA,
        displayMode: String = DocumentDateIntelligence.DISPLAY_MODE_SCOPED,
        maxDisplayChars: Int = DocumentDateIntelligence.DEFAULT_EXTRACT_CHARS,
        documentContent: String? = null,
    ): Map<String, String> {
        val isExtract = answerMode == GroundedDocumentAnswerService.ANSWER_MODE_EXTRACT
        var snippet = contentSnippet?.takeIf { it.isNotBlank() }
        if (isExtract && !documentContent.isNullOrBlank()) {
            snippet = DocumentDateIntelligence.extractExactSection(
                query = query,
                content = documentContent,
                maxChars = maxDisplayChars,
                displayMode = displayMode,
            ) ?: snippet
        }
        if (snippet == null) {
            val failure = if (isExtract) {
                DocumentDateIntelligence.narrowScopeFailureMessage(query) +
                    sourceFileName?.let { " — from $it" }.orEmpty()
            } else {
                unclearSectionMessage(query, sourceFileName, sourceModifiedAt)
            }
            return encoded + mapOf(
                "userMessage" to failure,
                "answerMode" to answerMode,
            )
        }
        val existing = encoded["userMessage"]
        if (!existing.isNullOrBlank() &&
            looksFabricatedRefusalNeeded(existing, snippet, query)
        ) {
            return encoded + mapOf(
                "userMessage" to unclearSectionMessage(query, sourceFileName, sourceModifiedAt),
                "answerMode" to answerMode,
            )
        }
        if (!DocumentContentNormalizer.isGroundedAnswerable(snippet)) {
            return encoded + mapOf(
                "userMessage" to unclearSectionMessage(query, sourceFileName, sourceModifiedAt),
                "answerMode" to answerMode,
            )
        }
        val answer = groundedAnswerService.answer(
            query = query,
            contentSnippet = snippet,
            sourceFileName = sourceFileName,
            sourceModifiedAt = sourceModifiedAt,
            answerMode = answerMode,
        )
        return encoded + buildMap {
            put("contentSnippet", snippet)
            put("answer", answer)
            put("userMessage", answer)
            put("answerMode", answerMode)
            sourceFileName?.let { put("sourceFileName", it) }
            sourceModifiedAt?.let { put("sourceModifiedAt", it.toString()) }
            if (isExtract) put("displayMode", displayMode)
        }
    }

    /**
     * True when an existing userMessage looks like a schedule answer without grounded
     * snippet backing — replace it with an explicit unreadable message.
     */
    private fun looksFabricatedRefusalNeeded(
        existingMessage: String,
        contentSnippet: String?,
        query: String,
    ): Boolean {
        if (!DocumentContentAnswerExtractor.isTimetableQuery(query)) return false
        if (contentSnippet.isNullOrBlank()) return true
        if (!DocumentContentNormalizer.isGroundedAnswerable(contentSnippet)) return true
        val target = DocumentDateIntelligence.resolveDateTarget(query)
        if (target != null && !target.monthScoped &&
            existingMessage.contains("Today's lecture slots", ignoreCase = true) &&
            !DocumentDateIntelligence.snippetContainsTargetDay(contentSnippet, target.date)
        ) {
            return true
        }
        return false
    }

    private fun unclearSectionMessage(
        query: String,
        sourceFileName: String?,
        sourceModifiedAt: Long?,
    ): String {
        val dateTarget = DocumentDateIntelligence.resolveDateTarget(query)
        val reason = if (dateTarget != null && !dateTarget.monthScoped) {
            DocumentDateIntelligence.UnreadableReason.NO_DAY_SECTION
        } else {
            DocumentDateIntelligence.UnreadableReason.CONTENT_UNREADABLE
        }
        val message = DocumentDateIntelligence.unreadableContentMessage(
            query = query,
            sourceFileName = sourceFileName,
            sourceModifiedAt = sourceModifiedAt,
            reason = reason,
        )
        return if (sourceFileName.isNullOrBlank()) {
            "$message — retry in a moment"
        } else {
            message
        }
    }

    private fun matchDebug(
        name: String?,
        summary: String?,
        modifiedAt: Long?,
        contentChars: Int = 0,
        extractStatus: String? = null,
    ): Map<String, String> {
        val fileName = name?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val summaryPart = summary?.takeIf { it.isNotBlank() }?.let { " (summary: ${it.take(120)})" }.orEmpty()
        val modifiedPart = modifiedAt?.takeIf { it > 0L }?.let { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            " (modified ${MATCH_DEBUG_DATE.format(date)})"
        }.orEmpty()
        val extractLabel = ContentExtractStatus.debugLabel(
            status = extractStatus,
            contentCharCount = contentChars,
        )
        val extractPart = extractLabel?.let { " ($it)" }.orEmpty()
        return mapOf("matchDebug" to "Matched: $fileName$modifiedPart$extractPart$summaryPart")
    }

    private fun emptyResultMessage(query: String): Map<String, String> {
        val lower = query.lowercase()
        val message = when {
            listOf("timetable", "time table", "schedule", "lec", "lecture", "slot", "class")
                .any { it in lower } ->
                "Couldn't find a timetable — put it in Documents or Downloads and wait for indexing"
            "menu" in lower || "mess" in lower ->
                "Couldn't find a mess menu — put the PDF in Documents or Downloads, grant All files access, then retry"
            else ->
                "No matching documents indexed yet — check Documents/Downloads and All files access, then retry"
        }
        return mapOf("userMessage" to message)
    }

    private fun searchMetadata(searchMode: String): Map<String, String> =
        mapOf(
            "providerId" to PROVIDER_ID,
            "capabilityType" to CAPABILITY_TYPE,
            "operation" to OPERATION_SEARCH,
            "searchMode" to searchMode,
        )

    companion object {
        const val PROVIDER_ID = "search-documents"
        const val CAPABILITY_TYPE = "search.documents"
        const val VERSION = "1.0.0"
        private const val SEARCH_MODE_SEMANTIC = "semantic"
        private const val SEARCH_MODE_KEYWORD = "keyword"
        private val MATCH_DEBUG_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
