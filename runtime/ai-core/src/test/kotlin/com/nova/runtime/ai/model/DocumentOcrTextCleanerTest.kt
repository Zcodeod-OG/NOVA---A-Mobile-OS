package com.nova.runtime.ai.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentOcrTextCleanerTest {

    @Test
    fun cleanScheduleText_keepsTimesDaysAndCourseCodes() {
        val raw = """
            |
            Mon
            09:00-10:20
            CSE1011
            Room 12
            .
            %%
            Lunch
            A1
        """.trimIndent()

        val cleaned = DocumentOcrTextCleaner.cleanScheduleText(raw)

        assertTrue(cleaned.contains("Mon"))
        assertTrue(cleaned.contains("09:00-10:20"))
        assertTrue(cleaned.contains("CSE1011"))
        assertTrue(cleaned.contains("Room 12"))
        assertTrue(cleaned.contains("Lunch"))
        assertTrue(cleaned.contains("A1"))
        assertFalse(cleaned.lines().any { it == "." || it == "%%" })
    }

    @Test
    fun cleanScheduleText_keepsShortGridCellsWithDigits() {
        val cleaned = DocumentOcrTextCleaner.cleanScheduleText("3\nB2\n#\nLab")
        assertTrue(cleaned.contains("3"))
        assertTrue(cleaned.contains("B2"))
        assertTrue(cleaned.contains("Lab"))
        assertFalse(cleaned.contains("#"))
    }
}
