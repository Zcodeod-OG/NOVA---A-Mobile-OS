package com.nova.runtime.storage.search

import com.nova.runtime.storage.entities.DocumentEntity
import java.util.UUID

/**
 * Hybrid content-aware ranking over indexed document metadata + [DocumentEntity.contentText].
 * Used by keyword and semantic search to prefer body matches over filename-only hits.
 */
object DocumentContentRanker {
    const val FILENAME_TOKEN_WEIGHT = 0.45f
    const val SUMMARY_TOKEN_WEIGHT = 0.35f
    const val CONTENT_TOKEN_WEIGHT = 0.75f
    const val CONTENT_PHRASE_BOOST = 1.2f
    const val CONTENT_PRESENT_BOOST = 0.35f

    fun tokenize(query: String): List<String> =
        query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in QUERY_STOPWORDS }

    fun scoreDocument(
        document: DocumentEntity,
        tokens: List<String>,
        semanticScore: Float = 0f,
        query: String = "",
    ): Float {
        if (tokens.isEmpty()) return semanticScore
        val name = document.name.lowercase()
        val nameTokens = name.substringBeforeLast('.')
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 }
            .toSet()
        val summary = document.summary.orEmpty().lowercase()
        val content = document.contentText.orEmpty().lowercase()
        val lowerQuery = query.lowercase()
        var boost = semanticScore

        for (token in tokens) {
            when {
                token in nameTokens || name.contains(token) ->
                    boost += FILENAME_TOKEN_WEIGHT
                summary.contains(token) ->
                    boost += SUMMARY_TOKEN_WEIGHT
                content.contains(token) ->
                    boost += CONTENT_TOKEN_WEIGHT
            }
        }

        if (content.isNotBlank()) {
            boost += CONTENT_PRESENT_BOOST
            if (tokens.size >= 2 && tokens.all { content.contains(it) }) {
                boost += CONTENT_PHRASE_BOOST
            }
            if (lowerQuery.isNotBlank() && content.contains(lowerQuery)) {
                boost += CONTENT_PHRASE_BOOST
            }
        }

        return boost
    }

    fun rankDocuments(
        documents: List<DocumentEntity>,
        tokens: List<String>,
        semanticScores: Map<UUID, Float> = emptyMap(),
        query: String = "",
    ): List<Pair<DocumentEntity, Float>> =
        documents
            .map { doc ->
                doc to scoreDocument(
                    document = doc,
                    tokens = tokens,
                    semanticScore = semanticScores[doc.id] ?: 0f,
                    query = query,
                )
            }
            .sortedWith(
                compareByDescending<Pair<DocumentEntity, Float>> { it.second }
                    .thenByDescending { it.first.modifiedAt }
                    .thenByDescending { !it.first.contentText.isNullOrBlank() },
            )

    private val QUERY_STOPWORDS = setOf(
        "what", "whats", "what's", "show", "tell", "find", "search", "look", "the", "for",
        "and", "from", "me", "my", "a", "an", "is", "are", "to", "of", "on", "in", "between",
        "please", "send", "share", "document", "file", "pdf", "today", "todays", "today's",
        "this", "month", "months", "month's", "latest", "current", "recent", "newest", "new",
    )
}
