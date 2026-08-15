package com.nova.runtime.storage.search

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentContentAnswerExtractorTest {
    /** Saturday, 8 August 2026 — matches the device smoke day. */
    private val saturday = LocalDate.of(2026, 8, 8)

    private val timetable = """
        WEEKLY TIMETABLE
        FRIDAY
        09:00-10:00 Physics
        12:00-13:00 Chemistry
        14:00-15:00 Biology
        SATURDAY
        09:00-10:00 Math
        10:00-11:00 English
        12:00-13:00 DSP
        13:00-14:00 Data Structures
        14:30-15:30 Operating Systems
        16:00-17:00 Lab
        SUNDAY
        11:00-12:00 Rest
    """.trimIndent()

    @Test
    fun parseClockToken_amPmAnd24h() {
        assertEquals(12 * 60, DocumentContentAnswerExtractor.parseClockToken("12pm"))
        assertEquals(12 * 60, DocumentContentAnswerExtractor.parseClockToken("12:00"))
        assertEquals(15 * 60, DocumentContentAnswerExtractor.parseClockToken("3pm"))
        assertEquals(15 * 60, DocumentContentAnswerExtractor.parseClockToken("15:00"))
        assertEquals(0, DocumentContentAnswerExtractor.parseClockToken("12am"))
        assertEquals(12 * 60 + 30, DocumentContentAnswerExtractor.parseClockToken("12:30pm"))
    }

    @Test
    fun parseTimeRange_between12pmAnd3pm() {
        val range = DocumentContentAnswerExtractor.parseTimeRange(
            "from timetable tell me my todays lec slots between 12pm to 3pm",
        )
        assertNotNull(range)
        assertEquals(12 * 60, range!!.startMinutes)
        assertEquals(15 * 60, range.endMinutes)
    }

    @Test
    fun filterByTimeRange_keepsOnlyOverlappingSaturdaySlots() {
        val day = DocumentDateIntelligence.extractDateSnippet(timetable, saturday)
        assertNotNull(day)
        val range = DocumentContentAnswerExtractor.TimeRange(12 * 60, 15 * 60)
        val filtered = DocumentContentAnswerExtractor.filterByTimeRange(day!!, range)
        assertNotNull(filtered)
        assertTrue(filtered!!.contains("DSP"))
        assertTrue(filtered.contains("Data Structures"))
        assertTrue(filtered.contains("Operating Systems"))
        assertFalse(filtered.contains("Math"))
        assertFalse(filtered.contains("English"))
        assertFalse(filtered.contains("Lab"))
        assertFalse(filtered.contains("FRIDAY"))
        assertFalse(filtered.contains("Chemistry"))
    }

    @Test
    fun extractScopedSnippet_todayPlusTimeRange_filtersLectureRows() {
        val query = "from timetable tell me my todays lec slots between 12pm to 3pm"
        val target = DocumentDateIntelligence.resolveDateTarget(query, saturday)
        assertEquals(saturday, target!!.date)
        val snippet = DocumentDateIntelligence.extractScopedSnippet(
            content = timetable,
            target = target,
            query = query,
        )
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("DSP"))
        assertTrue(snippet.contains("Data Structures"))
        assertFalse(snippet.contains("Math"))
        assertFalse(snippet.contains("Lab"))
    }

    @Test
    fun extractScopedSnippet_todayPlusTimeRange_noMatchingSlots_returnsNull() {
        val query = "todays lec slots between 6am to 7am"
        val target = DocumentDateIntelligence.resolveDateTarget(query, saturday)
        assertNull(
            DocumentDateIntelligence.extractScopedSnippet(
                content = timetable,
                target = target,
                query = query,
            ),
        )
    }

    @Test
    fun formatAnswer_includesSourceAttribution() {
        val zone = java.time.ZoneId.systemDefault()
        val millis = saturday.atStartOfDay(zone).toInstant().toEpochMilli()
        val answer = DocumentDateIntelligence.formatAnswer(
            query = "todays lec slots between 12pm to 3pm",
            snippet = "SATURDAY\n12:00-13:00 DSP\n13:00-14:00 Data Structures",
            today = saturday,
            sourceFileName = "weekly-timetable.pdf",
            sourceModifiedAt = millis,
        )
        assertTrue(answer.startsWith("Today's lecture slots (Saturday)"))
        assertTrue(answer.contains("— from weekly-timetable.pdf"))
        assertTrue(answer.contains("modified 2026-08-08"))
    }

    @Test
    fun formatTimetableAnswer_includesDayAndRange() {
        val answer = DocumentContentAnswerExtractor.formatTimetableAnswer(
            query = "todays lec slots between 12pm to 3pm",
            snippet = "SATURDAY\n12:00-13:00 DSP\n13:00-14:00 Data Structures",
            today = saturday,
        )
        assertTrue(answer.startsWith("Today's lecture slots (Saturday)"))
        assertTrue(answer.contains("12:00–15:00"))
        assertTrue(answer.contains("DSP"))
    }

    @Test
    fun stripQueryNoise_timetableQuestion_keepsTimetableSubject() {
        assertEquals(
            "timetable",
            DocumentDateIntelligence.stripQueryNoise(
                "from timetable tell me my todays lec slots between 12pm to 3pm",
            ),
        )
    }

    @Test
    fun filterByTimeRange_12pmBareHourRow() {
        val section = """
            SATURDAY
            12pm DSP
            3pm Seminar
            4pm Lab
        """.trimIndent()
        val filtered = DocumentContentAnswerExtractor.filterByTimeRange(
            section,
            DocumentContentAnswerExtractor.TimeRange(12 * 60, 15 * 60),
        )
        assertNotNull(filtered)
        assertTrue(filtered!!.contains("DSP"))
        assertTrue(filtered.contains("Seminar") || filtered.contains("3pm"))
        assertFalse(filtered.contains("Lab"))
    }

    @Test
    fun parseTimeRange_missingRange_returnsNull() {
        assertNull(DocumentContentAnswerExtractor.parseTimeRange("what is todays dinner menu"))
    }

    @Test
    fun extractGridDaySnippet_mondayColumn_returnsCourseRows() {
        val grid = """
            Semester 3 Timetable
            Time | Monday | Tuesday | Wednesday | Thursday | Friday
            8:00 AM - 9:00 AM | Free | Lec-MEL2001 | Lec-MEL2001 | Free | Lec-MEL2001
            9:30 AM - 11:00 AM | Lec-MLL1001 | Lec-MEL2024 | Lec-MEL2024 | Lec-MLL1001 | Lec-MEL2024
            5:00 PM - 6:30 PM | Lec-HSL2605 | Free | Free | Lec-HSL2605 | Free
        """.trimIndent()
        val monday = LocalDate.of(2026, 8, 10) // Monday
        val snippet = DocumentDateIntelligence.extractGridDaySnippet(grid, monday)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("MLL1001"))
        assertTrue(snippet.contains("HSL2605"))
        assertFalse(snippet.contains("MEL2001")) // Tuesday slot only
    }

    @Test
    fun formatTimetableAnswer_missingDaySection_usesNeutralPrefix() {
        val answer = DocumentContentAnswerExtractor.formatTimetableAnswer(
            query = "todays lec slots",
            snippet = "09:00-10:00 MLL1001 LH325\n10:00-11:00 MEL2001 LH308",
            today = saturday,
        )
        assertTrue(answer.startsWith("Schedule excerpt"))
        assertFalse(answer.startsWith("Today's lecture slots"))
        assertTrue(answer.contains("MLL1001"))
    }

    @Test
    fun extractScopedSnippet_ocrNoiseGrid_twoDayHeader_extractsSaturday() {
        val ocrGrid = """
            Time  Mon  Tue  Wed  Thu  Fri  Sat
            9:00  Free Free Free Free Free MLL1001
            12:00 Free Free Free Free Free DSP LH401
        """.trimIndent()
        val snippet = DocumentDateIntelligence.extractScopedSnippet(
            content = ocrGrid,
            target = DocumentDateIntelligence.DateTarget(saturday),
            query = "saturday lecture slots",
        )
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("MLL1001") || snippet.contains("DSP"))
    }

    @Test
    fun formatAnswer_withRealTokens_keepsGroundedContent() {
        val answer = DocumentDateIntelligence.formatAnswer(
            query = "monday lecture slots",
            snippet = "Monday\n9:30 AM — Lec-MLL1001\n5:00 PM — Lec-HSL2605",
            today = saturday,
            sourceFileName = "timetable_iitd.html",
        )
        assertTrue(answer.contains("MLL1001"))
        assertTrue(answer.contains("HSL2605"))
        assertTrue(answer.contains("timetable_iitd.html"))
        assertFalse(answer.contains("Physics"))
    }
}
