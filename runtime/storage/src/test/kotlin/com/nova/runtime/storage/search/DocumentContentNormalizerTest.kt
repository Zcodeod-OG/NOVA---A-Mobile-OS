package com.nova.runtime.storage.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentContentNormalizerTest {
    @Test
    fun toPlainText_stripsHtmlTimetableMarkup() {
        val html = """
            <html><head><style>.x{color:red}</style></head>
            <body><h2>Semester 3 Timetable</h2>
            <table><tr><th>Time</th><th>Monday</th><th>Tuesday</th></tr>
            <tr><td>8:00 AM</td><td>Free</td><td>Lec-MEL2001</td></tr>
            </table></body></html>
        """.trimIndent()
        val plain = DocumentContentNormalizer.toPlainText(html)
        assertTrue(plain.contains("MEL2001"))
        assertTrue(plain.contains("Monday"))
        assertFalse(plain.contains("<style"))
        assertFalse(plain.contains("color:red"))
    }

    @Test
    fun isGroundedAnswerable_rejectsEmptyAndTinyGarbage() {
        assertFalse(DocumentContentNormalizer.isGroundedAnswerable(null))
        assertFalse(DocumentContentNormalizer.isGroundedAnswerable(""))
        assertFalse(DocumentContentNormalizer.isGroundedAnswerable(":::"))
        assertFalse(DocumentContentNormalizer.isGroundedAnswerable("hi"))
    }

    @Test
    fun timetableStructureScore_prefersRichGridOverToyStub() {
        val toy = """
            WEEKLY TIMETABLE
            FRIDAY
            09:00-10:00 Physics
            SATURDAY
            12:00-13:00 DSP
        """.trimIndent()
        val rich = """
            Semester 3 Timetable
            Time | Monday | Tuesday | Wednesday | Thursday | Friday
            8:00 AM - 9:00 AM | Free | Lec-MEL2001 | Lec-MEL2001 | Free | Lec-MEL2001
            9:00 AM - 10:00 AM | Lec-MLL1001 | Lec-MEL2024 | Lec-MEL2024 | Lec-MLL1001 | Lec-MEL2024
            10:00 AM - 11:00 AM | Free | Lec-MEL2002 | Lec-MEL2002 | Free | Lec-MEL2002
            11:00 AM - 12:00 PM | Free | Lec-MEL2021 | Free | Lec-MEL2021 | Lec-MEL2021
            5:00 PM - 6:30 PM | Lec-HSL2605 | Free | Free | Lec-HSL2605 | Free
        """.trimIndent()
        assertTrue(
            DocumentContentNormalizer.timetableStructureScore(rich) >
                DocumentContentNormalizer.timetableStructureScore(toy) + 10f,
        )
        assertTrue(DocumentContentNormalizer.looksLikeScheduleContent(rich))
    }

    @Test
    fun formatAnswer_emptySnippet_doesNotInventSchedule() {
        val answer = DocumentDateIntelligence.formatAnswer(
            query = "todays lecture slots",
            snippet = "   ",
            sourceFileName = "weekly_timetable.txt",
        )
        assertTrue(answer.contains("couldn't read", ignoreCase = true) ||
            answer.contains("couldn't read its content", ignoreCase = true) ||
            answer.contains("Found weekly_timetable.txt", ignoreCase = true))
        assertFalse(answer.contains("Physics"))
        assertFalse(answer.contains("Today's lecture slots"))
    }
}
