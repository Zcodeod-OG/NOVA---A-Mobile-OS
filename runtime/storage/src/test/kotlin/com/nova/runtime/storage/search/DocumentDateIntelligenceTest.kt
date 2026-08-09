package com.nova.runtime.storage.search

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDateIntelligenceTest {
    /** Friday, 7 August 2026. */
    private val friday = LocalDate.of(2026, 8, 7)
    private val menu = """
        WEEKLY MESS MENU
        MONDAY
        Breakfast: Poha, Tea
        Lunch: Rajma Chawal
        THURSDAY
        Breakfast: Upma
        Lunch: Kadhi
        FRIDAY
        Breakfast: Aloo Paratha, Curd
        Lunch: Chole Bhature
        Dinner: Veg Biryani
        SATURDAY
        Breakfast: Idli Sambar
    """.trimIndent()

    private val monthlyMenu = """
        MESS MENU 2026
        JULY
        Breakfast: Idli
        Lunch: Sambar Rice
        AUGUST
        Breakfast: Aloo Paratha
        Lunch: Chole Bhature
        Dinner: Veg Biryani
        SEPTEMBER
        Breakfast: Poha
        Lunch: Paneer
    """.trimIndent()

    @Test
    fun resolveTargetDate_today() {
        assertEquals(friday, DocumentDateIntelligence.resolveTargetDate("send todays mess menu", friday))
        assertEquals(friday, DocumentDateIntelligence.resolveTargetDate("today's menu", friday))
    }

    @Test
    fun resolveTargetDate_tomorrowAndYesterday() {
        assertEquals(friday.plusDays(1), DocumentDateIntelligence.resolveTargetDate("tomorrow's menu", friday))
        assertEquals(friday.minusDays(1), DocumentDateIntelligence.resolveTargetDate("yesterday menu", friday))
    }

    @Test
    fun resolveTargetDate_weekdayName_resolvesToNextOrSame() {
        val resolved = DocumentDateIntelligence.resolveTargetDate("menu for monday", friday)
        assertNotNull(resolved)
        assertEquals(DayOfWeek.MONDAY, resolved!!.dayOfWeek)
        assertTrue(!resolved.isBefore(friday))
    }

    @Test
    fun resolveTargetDate_explicitDate() {
        assertEquals(LocalDate.of(2026, 8, 9), DocumentDateIntelligence.resolveTargetDate("menu for 9 august", friday))
        assertEquals(LocalDate.of(2026, 8, 9), DocumentDateIntelligence.resolveTargetDate("menu for aug 9", friday))
    }

    @Test
    fun resolveTargetDate_noDateReference_returnsNull() {
        assertNull(DocumentDateIntelligence.resolveTargetDate("send mess menu to atharv", friday))
    }

    @Test
    fun wantsRecencyPreference_latestCurrentRecentAndBareTimetable() {
        assertTrue(DocumentDateIntelligence.wantsRecencyPreference("latest timetable"))
        assertTrue(DocumentDateIntelligence.wantsRecencyPreference("current schedule"))
        assertTrue(DocumentDateIntelligence.wantsRecencyPreference("recent mess menu"))
        assertTrue(DocumentDateIntelligence.wantsRecencyPreference("show timetable"))
        assertTrue(DocumentDateIntelligence.wantsRecencyPreference("mess menu"))
        // Explicit calendar day already has a date target; recency words still count.
        assertTrue(DocumentDateIntelligence.wantsRecencyPreference("latest todays timetable"))
        assertFalse(DocumentDateIntelligence.wantsRecencyPreference("send mtl100 notes to atharv"))
    }

    @Test
    fun extractScopedSnippet_dayScoped_fallsBackToScheduleParagraph_notWrongDayLead() {
        val target = DocumentDateIntelligence.DateTarget(date = friday)
        val content = """
            WEEKLY TIMETABLE
            MONDAY
            09:00-10:00 MLL1001 Physics LH325
            TUESDAY
            10:00-11:00 MEL2001 Chemistry LH308
        """.trimIndent()
        val snippet = DocumentDateIntelligence.extractScopedSnippet(
            content = content,
            target = target,
            query = "todays lec slots",
        )
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("MLL1001") || snippet.contains("MEL2001"))
        assertFalse(snippet.contains("FRIDAY", ignoreCase = true))
        val answer = DocumentDateIntelligence.formatAnswer(
            query = "todays lec slots",
            snippet = snippet,
            today = friday,
            sourceFileName = "timetable_ocr.png",
        )
        assertFalse(answer.startsWith("Today's lecture slots"))
        assertTrue(answer.contains("Schedule excerpt") || answer.contains("couldn't isolate"))
    }

    @Test
    fun extractLooseDaySection_ocrMessySpacing_returnsFridayBlock() {
        val content = """
            SEMESTER TIMETABLE OCR
            THURSDAY
            12:00-13:00  DSP  LH401
            14:00-15:00  OS   LH302
            FRIDAY
            9:30-11:00 MLL1001 LH325
            5:00-6:30 HSL2605 LH416
            SATURDAY
            11:00-12:00 Free
        """.trimIndent()
        val snippet = DocumentDateIntelligence.extractLooseDaySection(content, friday)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("MLL1001"))
        assertTrue(snippet.contains("HSL2605"))
        assertFalse(snippet.contains("DSP"))
    }

    @Test
    fun extractOcrInlineDayLines_courseCodesWithoutHeader_returnsMatchingRows() {
        val content = """
            Mon Tue Wed Thu Fri
            9.30-11.00 MLL1001 MEL2001 MEL2001 Free MEL2001
            Fri 12.00-13.00 DSP LH401
            Fri 17.00-18.30 HSL2605 LH416
        """.trimIndent()
        val snippet = DocumentDateIntelligence.extractOcrInlineDayLines(content, friday)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("DSP"))
        assertTrue(snippet.contains("HSL2605"))
    }

    @Test
    fun extractGridDaySnippet_ocrTabSeparated_returnsMondayColumn() {
        val grid = """
            Time	Mon	Tue	Wed
            8:00-9:00	Free	MEL2001	MEL2001
            9:30-11:00	MLL1001	MEL2024	MEL2024
        """.trimIndent()
        val monday = LocalDate.of(2026, 8, 10)
        val snippet = DocumentDateIntelligence.extractGridDaySnippet(grid, monday)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("MLL1001"))
    }

    @Test
    fun withSourceAttribution_appendsFilenameAndDate() {
        val millis = friday.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val attributed = DocumentDateIntelligence.withSourceAttribution(
            answer = "Today's lecture slots (Friday): DSP",
            fileName = "timetable.pdf",
            modifiedAtMillis = millis,
            zoneId = java.time.ZoneOffset.UTC,
        )
        assertTrue(attributed.contains("— from timetable.pdf"))
        assertTrue(attributed.contains("modified 2026-08-07"))
    }

    @Test
    fun resolveDateTarget_thisMonth_isMonthScoped() {
        val target = DocumentDateIntelligence.resolveDateTarget("send this month's mess menu", friday)
        assertNotNull(target)
        assertEquals(friday, target!!.date)
        assertTrue(target.monthScoped)
        assertEquals(YearMonth.of(2026, 8), target.yearMonth)
    }

    @Test
    fun resolveDateTarget_thisMonths_withoutApostrophe() {
        val target = DocumentDateIntelligence.resolveDateTarget(
            "send this months mess menu to atharv on whatsapp",
            friday,
        )
        assertNotNull(target)
        assertTrue(target!!.monthScoped)
        assertTrue(DocumentDateIntelligence.isMonthScoped("this months mess menu"))
    }

    @Test
    fun stripDateWords_removesDateTokensOnly() {
        assertEquals("mess menu", DocumentDateIntelligence.stripDateWords("todays mess menu"))
        assertEquals("mess menu for", DocumentDateIntelligence.stripDateWords("mess menu for friday"))
    }

    @Test
    fun stripDateWords_removesThisMonthPhrases_keepsMessMenu() {
        assertEquals("mess menu", DocumentDateIntelligence.stripDateWords("this month's mess menu"))
        assertEquals("mess menu", DocumentDateIntelligence.stripDateWords("this months mess menu"))
        assertEquals("mess menu", DocumentDateIntelligence.stripDateWords("this month mess menu"))
        assertEquals("mess menu", DocumentDateIntelligence.stripDateWords("month's mess menu"))
    }

    @Test
    fun stripDateWords_neverReturnsBlank() {
        assertEquals("today", DocumentDateIntelligence.stripDateWords("today"))
        assertEquals("this month", DocumentDateIntelligence.stripDateWords("this month"))
    }

    @Test
    fun extractDateSnippet_returnsOnlyTargetDaySection() {
        val snippet = DocumentDateIntelligence.extractDateSnippet(menu, friday)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("FRIDAY"))
        assertTrue(snippet.contains("Chole Bhature"))
        assertTrue(snippet.contains("Veg Biryani"))
        assertFalse(snippet.contains("Idli Sambar"))
        assertFalse(snippet.contains("Poha"))
    }

    @Test
    fun extractDateSnippet_noMatchingSection_returnsNull() {
        val snippet = DocumentDateIntelligence.extractDateSnippet("just some prose without days", friday)
        assertNull(snippet)
    }

    @Test
    fun extractMonthSnippet_returnsCurrentMonthSection() {
        val snippet = DocumentDateIntelligence.extractMonthSnippet(monthlyMenu, YearMonth.of(2026, 8))
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("AUGUST"))
        assertTrue(snippet.contains("Chole Bhature"))
        assertFalse(snippet.contains("Idli"))
        assertFalse(snippet.contains("Paneer"))
    }

    @Test
    fun extractScopedSnippet_monthScoped_fallsBackToLeadWhenNoMonthHeading() {
        val target = DocumentDateIntelligence.DateTarget(date = friday, monthScoped = true)
        val snippet = DocumentDateIntelligence.extractScopedSnippet(menu, target)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("WEEKLY MESS MENU"))
    }

    @Test
    fun extractLeadSnippet_normalizesWhitespace() {
        val lead = DocumentDateIntelligence.extractLeadSnippet("  line one  \n\n\n  line two  ")
        assertEquals("line one\nline two", lead)
    }

    @Test
    fun detectMealType_dinner() {
        assertEquals("dinner", DocumentDateIntelligence.detectMealType("what is todays dinner menu"))
        assertEquals("lunch", DocumentDateIntelligence.detectMealType("show me today's lunch"))
        assertNull(DocumentDateIntelligence.detectMealType("todays mess menu"))
    }

    @Test
    fun extractScopedSnippet_dinner_pinpointsMealOnly() {
        val target = DocumentDateIntelligence.DateTarget(date = friday)
        val snippet = DocumentDateIntelligence.extractScopedSnippet(
            content = menu,
            target = target,
            query = "what is todays dinner menu",
        )
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("Dinner", ignoreCase = true) || snippet.contains("Veg Biryani"))
        assertTrue(snippet.contains("Veg Biryani"))
        assertFalse(snippet.contains("Chole Bhature"))
        assertFalse(snippet.contains("Aloo Paratha"))
    }

    @Test
    fun formatAnswer_todayDinner() {
        val answer = DocumentDateIntelligence.formatAnswer(
            query = "what is todays dinner menu",
            snippet = "Dinner: Dal Fry, Rice, Roti",
            today = friday,
        )
        assertTrue(answer.startsWith("Today's Dinner (Friday)"))
        assertTrue(answer.contains("Dal Fry"))
    }

    @Test
    fun stripQueryNoise_keepsMenuSubject() {
        assertEquals("mess menu", DocumentDateIntelligence.stripQueryNoise("todays dinner mess menu"))
        assertEquals("mess menu", DocumentDateIntelligence.stripQueryNoise("what is todays dinner menu"))
        assertEquals("mess menu", DocumentDateIntelligence.stripQueryNoise("todays dinner menu"))
    }
}
