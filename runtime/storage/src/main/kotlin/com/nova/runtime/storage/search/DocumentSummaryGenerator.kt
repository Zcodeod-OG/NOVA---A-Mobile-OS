package com.nova.runtime.storage.search

/**
 * Pure extractive (no LLM) discovery summary for Stage A file retrieval.
 * Prefers filename tokens + headings + first meaningful content lines, capped short
 * so MiniLM embeddings stay focused on distinctive file identity.
 */
object DocumentSummaryGenerator {
    const val MAX_SUMMARY_CHARS = 400
    private const val MAX_CONTENT_CHARS = 320
    private const val MAX_HEADING_LINES = 4
    private const val MAX_BODY_LINES = 6

    fun generate(name: String, contentText: String?): String {
        val trimmedName = name.trim().ifBlank { "document" }
        val contentPart = extractiveLead(contentText)
        val raw = if (contentPart.isBlank()) {
            trimmedName
        } else {
            "$trimmedName: $contentPart"
        }
        return collapseWhitespace(raw).take(MAX_SUMMARY_CHARS)
    }

    private fun extractiveLead(contentText: String?): String {
        if (contentText.isNullOrBlank()) return ""
        val lines = contentText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return ""

        val headings = lines
            .filter { isHeadingLike(it) }
            .take(MAX_HEADING_LINES)
        val body = lines
            .filterNot { isHeadingLike(it) }
            .filter { it.length >= 3 }
            .take(MAX_BODY_LINES)

        val combined = (headings + body)
            .distinct()
            .joinToString(" ")
            .ifBlank { lines.take(MAX_BODY_LINES).joinToString(" ") }

        return collapseWhitespace(combined).take(MAX_CONTENT_CHARS)
    }

    private fun isHeadingLike(line: String): Boolean {
        if (line.length > 80) return false
        val letters = line.filter { it.isLetter() }
        if (letters.length < 3) return false
        val upperRatio = letters.count { it.isUpperCase() }.toFloat() / letters.length
        if (upperRatio >= 0.7f) return true
        // Short title-like lines without sentence punctuation.
        return line.length <= 48 && !line.contains('.') && line.split(Regex("\\s+")).size <= 8
    }

    private fun collapseWhitespace(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()
}
