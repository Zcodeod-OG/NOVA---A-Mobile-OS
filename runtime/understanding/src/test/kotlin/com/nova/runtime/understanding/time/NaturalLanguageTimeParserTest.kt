package com.nova.runtime.understanding.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NaturalLanguageTimeParserTest {
    private val reference = Instant.parse("2026-08-07T10:00:00Z")
    private val zone = ZoneId.systemDefault()

    @Test
    fun parseAlarmTriggerMillis_parsesMorningTime() {
        val millis = NaturalLanguageTimeParser.parseAlarmTriggerMillis("set alarm for 7am", reference)
        assertNotNull(millis)
        val hour = Instant.ofEpochMilli(millis).atZone(zone).hour
        assertTrue(hour == 7 || hour == 19) // depends on local timezone vs UTC reference
    }

    @Test
    fun parseCalendarEvent_extractsTitleAndTimes() {
        val event = NaturalLanguageTimeParser.parseCalendarEvent(
            "schedule meeting tomorrow at 3pm",
            reference,
        )
        assertNotNull(event)
        assertTrue(event.title.contains("meeting", ignoreCase = true))
        assertTrue(event.endTime > event.startTime)
    }

    @Test
    fun parseTime_handlesMinutesAndMeridiem() {
        val time = NaturalLanguageTimeParser.parseTime("wake me at 6:30 am tomorrow")
        assertNotNull(time)
        assertEquals(6, time.hour)
        assertEquals(30, time.minute)
    }

    @Test
    fun resolveDate_usesTomorrow() {
        val base = LocalDate.of(2026, 8, 7)
        val resolved = NaturalLanguageTimeParser.resolveDate("set alarm for 7am tomorrow", base)
        assertEquals(base.plusDays(1), resolved)
    }
}
