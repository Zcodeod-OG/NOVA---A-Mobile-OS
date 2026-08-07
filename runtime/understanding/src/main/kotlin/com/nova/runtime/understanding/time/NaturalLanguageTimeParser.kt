package com.nova.runtime.understanding.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class ParsedCalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
)

/** Parses common time phrases from user commands (e.g. "7am", "tomorrow at 3pm"). */
object NaturalLanguageTimeParser {
    private val TIME_REGEX = Regex(
        """(?<!\d)(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val DAY_NAMES = DayOfWeek.entries.flatMap { day ->
        listOf(day.name.lowercase(), day.name.lowercase().take(3))
    }

    fun parseAlarmTriggerMillis(payload: String, now: Instant = Instant.now()): Long? {
        val zone = ZoneId.systemDefault()
        val zonedNow = now.atZone(zone)
        val time = parseTime(payload) ?: return null
        var target = ZonedDateTime.of(resolveDate(payload, zonedNow.toLocalDate()), time, zone)
        if (target.toInstant().isBefore(now) && !payload.contains("tomorrow")) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    fun parseCalendarEvent(payload: String, now: Instant = Instant.now()): ParsedCalendarEvent? {
        val zone = ZoneId.systemDefault()
        val zonedNow = now.atZone(zone)
        val title = extractEventTitle(payload)
        val time = parseTime(payload) ?: LocalTime.of(9, 0)
        var start = ZonedDateTime.of(resolveDate(payload, zonedNow.toLocalDate()), time, zone)
        if (start.toInstant().isBefore(now) && !payload.contains("tomorrow")) {
            start = start.plusDays(1)
        }
        val end = start.plusHours(1)
        return ParsedCalendarEvent(
            title = title,
            startTime = start.toInstant().toEpochMilli(),
            endTime = end.toInstant().toEpochMilli(),
        )
    }

    internal fun parseTime(payload: String): LocalTime? {
        val lower = payload.lowercase()
        when {
            "noon" in lower -> return LocalTime.NOON
            "midnight" in lower -> return LocalTime.MIDNIGHT
        }

        val match = TIME_REGEX.find(lower) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3].lowercase().replace(".", "")

        when {
            meridiem.startsWith("p") && hour in 1..11 -> hour += 12
            meridiem.startsWith("a") && hour == 12 -> hour = 0
            meridiem.isBlank() && hour in 1..11 && ("pm" in lower || "p.m" in lower) -> hour += 12
            meridiem.isBlank() && hour == 12 && ("am" in lower || "a.m" in lower) -> hour = 0
        }

        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    internal fun resolveDate(payload: String, base: LocalDate): LocalDate {
        val lower = payload.lowercase()
        when {
            "tomorrow" in lower -> return base.plusDays(1)
            "today" in lower -> return base
        }

        for (day in DayOfWeek.entries) {
            val full = day.name.lowercase()
            val short = full.take(3)
            if (Regex("\\b$full\\b").containsMatchIn(lower) || Regex("\\b$short\\b").containsMatchIn(lower)) {
                return base.with(TemporalAdjusters.nextOrSame(day))
            }
        }
        return base
    }

    internal fun extractEventTitle(payload: String): String {
        val lower = payload.lowercase()
        TITLE_AFTER_KEYWORD_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.let { candidate ->
            val cleaned = stripSchedulingTokens(candidate)
            if (cleaned.isNotBlank()) return cleaned.replaceFirstChar { it.titlecase() }
        }

        val withoutTime = TIME_REGEX.replace(lower, " ").trim()
        val tokens = withoutTime.split(Regex("\\s+"))
            .filter { token ->
                token.isNotBlank() &&
                    token !in CALENDAR_STOP_WORDS &&
                    token.length > 1
            }
        return tokens.take(3).joinToString(" ").replaceFirstChar { it.titlecase() }.ifBlank { "Event" }
    }

    private fun stripSchedulingTokens(value: String): String =
        value.split(Regex("\\s+"))
            .filterNot { token -> token in CALENDAR_STOP_WORDS || TIME_REGEX.matches(token) }
            .joinToString(" ")
            .trim()

    private val TITLE_AFTER_KEYWORD_REGEX = Regex(
        """(?:schedule|add|create|set)\s+(?:a\s+)?(?:calendar\s+)?(?:event\s+)?(.+?)(?:\s+(?:at|on|for|tomorrow|today|\d)|$)""",
        RegexOption.IGNORE_CASE,
    )

    private val CALENDAR_STOP_WORDS = setOf(
        "a", "an", "the", "calendar", "schedule", "add", "create", "set",
        "tomorrow", "today", "at", "on", "for", "pm", "am", "p.m", "a.m",
    )
}
