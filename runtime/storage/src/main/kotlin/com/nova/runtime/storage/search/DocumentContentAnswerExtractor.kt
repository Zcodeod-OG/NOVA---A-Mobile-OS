package com.nova.runtime.storage.search

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Extracts concise in-app answers from indexed document text: time-range filtering
 * for timetable rows, topic detection (lecture slots), and subject phrases for search
 * ("from timetable tell me…" → "timetable").
 */
object DocumentContentAnswerExtractor {
    data class TimeRange(
        /** Inclusive start, minutes from midnight. */
        val startMinutes: Int,
        /** Inclusive end, minutes from midnight. */
        val endMinutes: Int,
    ) {
        fun contains(minuteOfDay: Int): Boolean =
            minuteOfDay in startMinutes..endMinutes

        /** True when a slot interval overlaps this range (half-open end if end > start). */
        fun overlapsSlot(slotStart: Int, slotEnd: Int?): Boolean {
            val end = slotEnd ?: (slotStart + 1)
            return slotStart < endMinutes && end > startMinutes
        }

        fun label(): String = "${formatClock(startMinutes)}–${formatClock(endMinutes)}"
    }

    /** Parses "between 12pm and 3pm", "from 12:00 to 15:00", "12pm to 3pm". */
    fun parseTimeRange(query: String): TimeRange? {
        val lower = query.lowercase()
        BETWEEN_RANGE_REGEX.find(lower)?.let { match ->
            val start = parseClockToken(match.groupValues[1]) ?: return@let
            val end = parseClockToken(match.groupValues[2]) ?: return@let
            if (end > start) return TimeRange(start, end)
        }
        FROM_TO_RANGE_REGEX.find(lower)?.let { match ->
            val start = parseClockToken(match.groupValues[1]) ?: return@let
            val end = parseClockToken(match.groupValues[2]) ?: return@let
            if (end > start) return TimeRange(start, end)
        }
        LOOSE_RANGE_REGEX.find(lower)?.let { match ->
            val start = parseClockToken(match.groupValues[1]) ?: return@let
            val end = parseClockToken(match.groupValues[2]) ?: return@let
            if (end > start) return TimeRange(start, end)
        }
        return null
    }

    /**
     * Parses a single clock token: "12pm", "12:00", "3pm", "15:00", "12:30pm".
     * Returns minutes from midnight, or null when unrecognized.
     */
    fun parseClockToken(raw: String): Int? {
        val token = raw.trim().lowercase().replace(Regex("""\s+"""), "")
        if (token.isBlank()) return null
        CLOCK_12H_REGEX.matchEntire(token)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
            val meridiem = match.groupValues[3]
            if (hour !in 1..12 || minute !in 0..59) return null
            if (meridiem == "am") {
                if (hour == 12) hour = 0
            } else {
                if (hour != 12) hour += 12
            }
            return hour * 60 + minute
        }
        CLOCK_24H_REGEX.matchEntire(token)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }
        CLOCK_BARE_HOUR_REGEX.matchEntire(token)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            if (hour !in 0..23) return null
            return hour * 60
        }
        return null
    }

    /** Detects lecture/slot topic from the query, or null when not timetable-like. */
    fun detectTopic(query: String): String? {
        val lower = query.lowercase()
        return when {
            LECTURE_TOPIC_REGEX.containsMatchIn(lower) -> "lec slots"
            TIMETABLE_SUBJECT_REGEX.containsMatchIn(lower) -> "timetable"
            else -> null
        }
    }

    /** True when the query asks about a timetable / lecture schedule. */
    fun isTimetableQuery(query: String): Boolean {
        val lower = query.lowercase()
        return TIMETABLE_SUBJECT_REGEX.containsMatchIn(lower) ||
            LECTURE_TOPIC_REGEX.containsMatchIn(lower)
    }

    /**
     * Subject phrase for document search: "from timetable tell me…" → "timetable",
     * "what is todays dinner menu" → falls through to [DocumentDateIntelligence.stripQueryNoise].
     */
    fun extractDocumentSubject(query: String): String? {
        val lower = query.lowercase().trim()
        FROM_SUBJECT_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        if (isTimetableQuery(lower)) {
            return "timetable"
        }
        return null
    }

    /**
     * Keeps lines/blocks from [section] whose mentioned times fall within [range].
     * Heuristics: "12:00-13:00 Math", "12pm DSP", "12:00 – 1:00 PM Digital Signal Processing".
     */
    fun filterByTimeRange(section: String, range: TimeRange): String? {
        if (section.isBlank()) return null
        val matched = section.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line -> lineOverlapsRange(line, range) }
        if (matched.isEmpty()) return null
        return matched.joinToString("\n")
    }

    /**
     * Formats a timetable / lecture-slot answer for the activity feed.
     */
    fun formatTimetableAnswer(
        query: String,
        snippet: String,
        today: LocalDate = LocalDate.now(),
    ): String {
        val target = DocumentDateIntelligence.resolveDateTarget(query, today)
        val range = parseTimeRange(query)
        val weekday = (target?.date ?: today).dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val dayLabel = when {
            target == null -> null
            target.date == today -> "Today"
            target.date == today.plusDays(1) -> "Tomorrow"
            target.date == today.minusDays(1) -> "Yesterday"
            else -> weekday
        }
        val dayResolved = target?.date?.let { DocumentDateIntelligence.snippetContainsTargetDay(snippet, it) } == true
        val prefix = buildString {
            when {
                dayLabel == "Today" && dayResolved ->
                    append("Today's lecture slots ($weekday)")
                dayLabel != null && dayResolved ->
                    append("$dayLabel lecture slots")
                dayLabel != null && !dayResolved ->
                    append("Schedule excerpt (couldn't isolate $weekday clearly)")
                else -> append("Schedule excerpt")
            }
            if (range != null) {
                append(", ${range.label()}")
            }
        }
        val body = snippet.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                // Drop bare day headings already reflected in the prefix.
                DayOfWeekNames.any { day -> line.equals(day, ignoreCase = true) }
            }
            .joinToString("; ")
            .take(DEFAULT_ANSWER_CHARS)
        return "$prefix: $body"
    }

    /**
     * Strips time-range / lecture / filler words so search focuses on the document subject.
     * "from timetable tell me my todays lec slots between 12pm to 3pm" → "timetable"
     */
    fun stripTimetableNoise(query: String): String {
        val stripped = query
            .let { BETWEEN_RANGE_REGEX.replace(it, " ") }
            .let { FROM_TO_RANGE_REGEX.replace(it, " ") }
            .let { LOOSE_RANGE_REGEX.replace(it, " ") }
            .let { CLOCK_TOKEN_IN_TEXT_REGEX.replace(it, " ") }
            .let { LECTURE_FILLER_REGEX.replace(it, " ") }
            .let { FROM_PREFIX_REGEX.replace(it, " ") }
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        return stripped.ifBlank { query }
    }

    private fun lineOverlapsRange(line: String, range: TimeRange): Boolean {
        val times = extractTimesFromLine(line)
        if (times.isEmpty()) return false
        // Prefer interval overlap when two+ times appear (slot start/end).
        if (times.size >= 2) {
            return range.overlapsSlot(times[0], times[1])
        }
        return range.contains(times[0]) || range.overlapsSlot(times[0], null)
    }

    /** Ordered clock times mentioned on a timetable row. */
    fun extractTimesFromLine(line: String): List<Int> {
        val found = mutableListOf<Int>()
        val lower = line.lowercase()
        for (match in CLOCK_TOKEN_IN_TEXT_REGEX.findAll(lower)) {
            parseClockToken(match.value)?.let { found += it }
        }
        for (match in OCR_DOT_TIME_REGEX.findAll(lower)) {
            parseOcrDotTime(match.value)?.let { found += it }
        }
        return found
    }

    /** Parses OCR-style dot times: "9.30", "12.00". */
    fun parseOcrDotTime(raw: String): Int? {
        val parts = raw.trim().split('.')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** Formats one or two clock times as a compact range label for OCR/grid rows. */
    fun formatClockRange(times: List<Int>): String {
        if (times.isEmpty()) return ""
        if (times.size == 1) return formatClock(times.first())
        return "${formatClock(times.first())}–${formatClock(times.last())}"
    }

    fun formatClock(minutes: Int): String {
        val h = (minutes / 60).coerceIn(0, 23)
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    private val DayOfWeekNames = listOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "mon", "tue", "wed", "thu", "fri", "sat", "sun",
    )

    private val BETWEEN_RANGE_REGEX = Regex(
        """\bbetween\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s+(?:and|to|-|–|—)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FROM_TO_RANGE_REGEX = Regex(
        """\bfrom\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s+(?:to|-|–|—)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val LOOSE_RANGE_REGEX = Regex(
        """\b(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s*(?:to|-|–|—)\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val CLOCK_12H_REGEX = Regex("""^(\d{1,2})(?::(\d{2}))?(am|pm)$""")
    private val CLOCK_24H_REGEX = Regex("""^(\d{1,2}):(\d{2})$""")
    private val CLOCK_BARE_HOUR_REGEX = Regex("""^(\d{1,2})$""")

    private val CLOCK_TOKEN_IN_TEXT_REGEX = Regex(
        """\b\d{1,2}(?::\d{2})?\s*(?:am|pm)?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val OCR_DOT_TIME_REGEX = Regex("""\b\d{1,2}\.\d{2}\b""")

    private val LECTURE_TOPIC_REGEX = Regex(
        """\b(?:lec(?:ture)?s?|slots?|classes|class)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TIMETABLE_SUBJECT_REGEX = Regex(
        """\b(?:timetable|time\s*table|schedule|class\s*schedule)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FROM_SUBJECT_REGEX = Regex(
        """\bfrom\s+([a-z][a-z0-9 _-]{1,40}?)\s+(?:tell|what|show|give)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FROM_PREFIX_REGEX = Regex(
        """\bfrom\s+[a-z][a-z0-9 _-]{1,40}?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val LECTURE_FILLER_REGEX = Regex(
        """\b(?:lec(?:ture)?s?|slots?|classes|class|my|me|please|between|and|to|the|a|an|for)\b""",
        RegexOption.IGNORE_CASE,
    )

    const val DEFAULT_ANSWER_CHARS = 400
}
