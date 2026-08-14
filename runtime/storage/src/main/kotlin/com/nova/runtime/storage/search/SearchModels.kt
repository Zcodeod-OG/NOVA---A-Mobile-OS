package com.nova.runtime.storage.search

import java.util.UUID

/** Paginated search request shared across photo, document, and semantic search. */
data class SearchRequest(
    val query: String,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
    val indexOnQuery: Boolean = false,
    /** "qa" (default) or "extract" for verbatim content display. */
    val answerMode: String? = null,
    val maxDisplayChars: Int? = null,
    /** "scoped" (date/meal/topic) or "verbatim" (broader excerpt). */
    val displayMode: String? = null,
) {
    init {
        require(query.isNotBlank()) { "Search query must not be blank" }
        require(limit in 1..MAX_LIMIT) { "Limit must be between 1 and $MAX_LIMIT" }
        require(offset >= 0) { "Offset must be non-negative" }
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}

/** Paginated search response. */
data class SearchPage<T>(
    val items: List<T>,
    val totalCount: Int,
    val query: String,
    val limit: Int,
    val offset: Int,
) {
    val count: Int get() = items.size
    val hasMore: Boolean get() = offset + count < totalCount
}

data class PhotoSearchHit(
    val id: UUID,
    val uri: String,
    val takenAt: Long,
    val ocrText: String?,
    val matchSource: PhotoMatchSource,
    val score: Float = 1f,
)

enum class PhotoMatchSource {
    ROOM_OCR,
    MEDIA_STORE,
    BOTH,
}

data class DocumentSearchHit(
    val id: UUID,
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val modifiedAt: Long,
    val score: Float = 1f,
    /** Plain-text excerpt of the document content relevant to the query (e.g. today's menu section). */
    val contentSnippet: String? = null,
    /** Stage A discovery summary (not used for answers). */
    val summary: String? = null,
    /** Full extracted body length (not snippet length) for debug/status. */
    val contentCharCount: Int = 0,
    /** See [ContentExtractStatus]. */
    val contentExtractStatus: String? = null,
)

data class SemanticSearchHit(
    val objectId: UUID,
    val objectType: String,
    val score: Float,
    val title: String,
    val snippet: String?,
    /** Plain-text excerpt of the document content relevant to the query (e.g. today's menu section). */
    val contentSnippet: String? = null,
    /** Stage A discovery summary (not used for answers). */
    val summary: String? = null,
    /** Document last-modified millis when [objectType] is a document; used for source attribution. */
    val modifiedAt: Long? = null,
    /** Full extracted body length (not snippet length) for debug/status. */
    val contentCharCount: Int = 0,
    /** See [ContentExtractStatus]. */
    val contentExtractStatus: String? = null,
)
