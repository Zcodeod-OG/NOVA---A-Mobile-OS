package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.LocalLlmEngine
import com.nova.runtime.ai.model.UnavailableLocalLlmEngine
import com.nova.runtime.storage.search.DocumentContentAnswerExtractor
import com.nova.runtime.storage.search.DocumentContentNormalizer
import com.nova.runtime.storage.search.DocumentDateIntelligence

/**
 * Builds user-facing document answers from retrieved content snippets only.
 * Optional on-device LLM may rewrite/structure the snippet; ungrounded output is rejected.
 */
class GroundedDocumentAnswerService(
    private val localLlmEngine: LocalLlmEngine = UnavailableLocalLlmEngine,
) {
    suspend fun answer(
        query: String,
        contentSnippet: String?,
        sourceFileName: String?,
        sourceModifiedAt: Long?,
    ): String {
        val snippet = contentSnippet?.trim()?.takeIf { it.isNotBlank() }
        if (snippet == null || !DocumentContentNormalizer.isGroundedAnswerable(snippet)) {
            return DocumentDateIntelligence.unreadableContentMessage(
                query = query,
                sourceFileName = sourceFileName,
                sourceModifiedAt = sourceModifiedAt,
                reason = if (DocumentContentAnswerExtractor.isTimetableQuery(query)) {
                    DocumentDateIntelligence.UnreadableReason.NO_DAY_SECTION
                } else {
                    DocumentDateIntelligence.UnreadableReason.CONTENT_UNREADABLE
                },
            )
        }

        val extractive = DocumentDateIntelligence.formatAnswer(
            query = query,
            snippet = snippet,
            sourceFileName = sourceFileName,
            sourceModifiedAt = sourceModifiedAt,
        )

        if (!localLlmEngine.isAvailable()) return extractive

        val prompt = buildGroundedPrompt(
            query = query,
            snippet = snippet.take(MAX_CONTEXT_CHARS),
            sourceFileName = sourceFileName,
        )
        val generated = localLlmEngine.generate(prompt)?.trim().orEmpty()
        if (generated.isBlank()) return extractive
        if (looksUngrounded(generated, snippet)) return extractive
        if (looksLikeRefusal(generated)) {
            return DocumentDateIntelligence.unreadableContentMessage(
                query = query,
                sourceFileName = sourceFileName,
                sourceModifiedAt = sourceModifiedAt,
                reason = DocumentDateIntelligence.UnreadableReason.CONTENT_UNREADABLE,
            )
        }

        val attributed = DocumentDateIntelligence.formatAnswer(
            query = query,
            snippet = generated.take(MAX_ANSWER_CHARS),
            sourceFileName = sourceFileName,
            sourceModifiedAt = sourceModifiedAt,
        )
        // If formatAnswer wraps timetable heuristics poorly around LLM prose, keep LLM + attribution.
        return if (DocumentContentNormalizer.isGroundedAnswerable(attributed)) {
            attributed
        } else {
            extractive
        }
    }

    private fun buildGroundedPrompt(
        query: String,
        snippet: String,
        sourceFileName: String?,
    ): String {
        val source = sourceFileName?.takeIf { it.isNotBlank() } ?: "document"
        return """
            You are NOVA, an on-device assistant. Answer ONLY using the document context below.
            If the context does not contain the answer, reply exactly: NOT_IN_DOCUMENT
            Do not invent times, courses, rooms, or menus. Prefer short factual bullets.

            Source file: $source
            User question: $query

            Document context:
            ---
            $snippet
            ---

            Answer:
            """.trimIndent()
    }

    private fun looksLikeRefusal(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("not_in_document") ||
            lower.contains("not in document")
    }

    private fun looksUngrounded(answer: String, snippet: String): Boolean {
        val answerTokens = tokenize(answer)
        if (answerTokens.isEmpty()) return true
        val snippetTokens = tokenize(snippet)
        if (snippetTokens.isEmpty()) return true
        val overlap = answerTokens.count { it in snippetTokens }
        val ratio = overlap.toDouble() / answerTokens.size.toDouble()
        return ratio < MIN_TOKEN_OVERLAP
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 }
            .toSet()

    companion object {
        private const val MAX_CONTEXT_CHARS = 6_000
        private const val MAX_ANSWER_CHARS = 1_200
        private const val MIN_TOKEN_OVERLAP = 0.18
    }
}
