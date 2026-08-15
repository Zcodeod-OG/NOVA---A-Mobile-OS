package com.nova.runtime.storage.search

/**
 * Pure extractive normalizer for indexed document bodies.
 * Strips HTML/CSS/JS noise and scores whether text is usable for grounded Q&A.
 * Never invents content — only cleans what was extracted (OCR / file read).
 */
object DocumentContentNormalizer {
    private val COURSE_CODE_REGEX = Regex("""\b[A-Z]{2,4}\d{3,4}\b""")
    private val TIME_REGEX = Regex(
        """\b\d{1,2}(?::\d{2})?\s*(?:am|pm)?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val DAY_REGEX = Regex(
        """\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val SCRIPT_STYLE_REGEX = Regex(
        """(?is)<(script|style)\b[^>]*>.*?</\1>""",
    )
    private val TAG_REGEX = Regex("""(?is)<[^>]+>""")
    private val HTML_ENTITY_REGEX = Regex("""&(?:amp|lt|gt|quot|nbsp|#\d+|#x[0-9a-f]+);""", RegexOption.IGNORE_CASE)

    /**
     * Converts stored extraction (possibly raw HTML) into plain text suitable for
     * day/snippet extraction. Idempotent for already-plain content.
     */
    fun toPlainText(raw: String): String {
        if (raw.isBlank()) return ""
        val looksHtml = raw.contains('<') && (
            raw.contains("</", ignoreCase = true) ||
                raw.contains("<!doctype", ignoreCase = true) ||
                raw.contains("<html", ignoreCase = true) ||
                raw.contains("<table", ignoreCase = true)
            )
        val stripped = if (looksHtml) stripHtml(raw) else raw
        return normalizeWhitespace(stripped)
    }

    /** True when [content] has enough real extractive signal to answer from (not empty/garbage). */
    fun isGroundedAnswerable(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val plain = toPlainText(content)
        if (plain.length < MIN_ANSWERABLE_CHARS) return false
        // Reject pure markup leftovers / CSS variable soup.
        val letterDigits = plain.count { it.isLetterOrDigit() }
        if (letterDigits < MIN_ANSWERABLE_CHARS / 2) return false
        return true
    }

    /**
     * Relative richness of timetable-like structure. Used to prefer a real semester
     * grid over a tiny demo stub when both match "timetable" by filename.
     */
    fun timetableStructureScore(content: String?): Float {
        if (content.isNullOrBlank()) return 0f
        val plain = toPlainText(content)
        if (plain.isBlank()) return 0f
        val days = DAY_REGEX.findAll(plain).map { it.value.lowercase() }.distinct().count()
        val times = TIME_REGEX.findAll(plain).count().coerceAtMost(40)
        val courseCodes = COURSE_CODE_REGEX.findAll(plain).map { it.value }.distinct().count()
        val lengthBoost = when {
            plain.length >= 2_000 -> 8f
            plain.length >= 800 -> 5f
            plain.length >= 300 -> 2f
            else -> 0f
        }
        return days * 3f + times * 0.35f + courseCodes * 4f + lengthBoost
    }

    /** True when text looks like a usable schedule (days/times/course codes), not random OCR noise. */
    fun looksLikeScheduleContent(content: String?): Boolean {
        if (!isGroundedAnswerable(content)) return false
        val plain = toPlainText(content.orEmpty())
        val days = DAY_REGEX.findAll(plain).map { it.value.lowercase() }.distinct().count()
        val times = TIME_REGEX.findAll(plain).count()
        val courseCodes = COURSE_CODE_REGEX.findAll(plain).count()
        return (days >= 2 && times >= 2) || courseCodes >= 2 || (times >= 3 && days >= 1)
    }

    private fun stripHtml(raw: String): String {
        var text = SCRIPT_STYLE_REGEX.replace(raw, " ")
        // Preserve row breaks roughly: close of tr/p/div/br/li → newline.
        text = text.replace(Regex("""(?i)</(?:tr|p|div|li|h[1-6]|br)\s*>"""), "\n")
        text = text.replace(Regex("""(?i)<br\s*/?>"""), "\n")
        text = text.replace(Regex("""(?i)</td\s*>"""), " | ")
        text = TAG_REGEX.replace(text, " ")
        text = decodeEntities(text)
        return text
    }

    private fun decodeEntities(value: String): String =
        HTML_ENTITY_REGEX.replace(value) { match ->
            when (match.value.lowercase()) {
                "&amp;" -> "&"
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&nbsp;" -> " "
                else -> " "
            }
        }

    private fun normalizeWhitespace(value: String): String =
        value.lines()
            .map { it.replace(Regex("""[ \t\u00a0]+"""), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    const val MIN_ANSWERABLE_CHARS = 24
}
