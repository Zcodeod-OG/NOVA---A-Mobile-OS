package com.nova.runtime.ai.model

/**
 * Lightweight OCR post-filter for timetable / mess-menu photos and scanned PDFs.
 * Keeps schedule signal (times, days, course codes, short room cells); drops
 * ultra-short glyph noise without inventing content.
 */
object DocumentOcrTextCleaner {
    private val TIME_SIGNAL = Regex(
        """\b\d{1,2}(?::\d{2})(?:\s*[-–—]\s*\d{1,2}(?::\d{2}))?\s*(?:am|pm)?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val DAY_SIGNAL = Regex(
        """\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val COURSE_CODE = Regex("""\b[A-Za-z]{2,4}\d{3,4}\b""")
    private val MEAL_SIGNAL = Regex(
        """\b(?:breakfast|lunch|dinner|supper|brunch|snack|mess|menu|timetable|schedule|lecture|lab|tutorial|room)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ROOM_OR_SLOT = Regex(
        """\b(?:r(?:oom)?\s*\d+[A-Za-z]?|lt[- ]?\d+|hall\s*\d+)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun cleanScheduleText(raw: String): String =
        raw.lines()
            .map { it.trim() }
            .filter(::keepLine)
            .joinToString("\n")

    private fun keepLine(line: String): Boolean {
        if (line.isEmpty()) return false
        if (hasScheduleSignal(line)) return true
        // Keep modest alphanumeric rows (grid cells like "A1", "B2", "3A").
        if (!line.any { it.isLetterOrDigit() }) return false
        if (line.length >= 2) return true
        // Single-char cells that are digits or letters often appear in schedule grids.
        return line.any { it.isDigit() } || line.any { it.isLetter() }
    }

    private fun hasScheduleSignal(line: String): Boolean =
        TIME_SIGNAL.containsMatchIn(line) ||
            DAY_SIGNAL.containsMatchIn(line) ||
            COURSE_CODE.containsMatchIn(line) ||
            MEAL_SIGNAL.containsMatchIn(line) ||
            ROOM_OR_SLOT.containsMatchIn(line) ||
            line.contains('|') ||
            line.count { it.isDigit() } >= 2
}
