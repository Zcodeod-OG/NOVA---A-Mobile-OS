package com.nova.runtime.storage.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSummaryGeneratorTest {
    @Test
    fun generate_prefixesFilenameAndUsesHeadingsPlusLeadContent() {
        val content = """
            BOOKLY PROSPECTUS REPORT
            
            Introduction
            This prospectus covers placement outcomes and company overview for Bookly.
            
            More filler that should be truncated eventually if the lead is long enough.
        """.trimIndent()

        val summary = DocumentSummaryGenerator.generate(
            name = "BooklyProspectusReport.pdf",
            contentText = content,
        )

        assertTrue(summary.startsWith("BooklyProspectusReport.pdf:"))
        assertTrue(summary.contains("BOOKLY PROSPECTUS REPORT"))
        assertTrue(summary.contains("prospectus", ignoreCase = true))
        assertTrue(summary.length <= DocumentSummaryGenerator.MAX_SUMMARY_CHARS)
    }

    @Test
    fun generate_withoutContent_usesFilenameOnly() {
        val summary = DocumentSummaryGenerator.generate(
            name = "mess-menu.pdf",
            contentText = null,
        )
        assertEquals("mess-menu.pdf", summary)
    }

    @Test
    fun generate_capsLength() {
        val noisy = buildString {
            appendLine("WEEKLY MESS MENU")
            repeat(80) { appendLine("Line $it with assorted OCR noise and leftover tokens") }
        }
        val summary = DocumentSummaryGenerator.generate("mess-menu.pdf", noisy)
        assertTrue(summary.length <= DocumentSummaryGenerator.MAX_SUMMARY_CHARS)
        assertTrue(summary.contains("WEEKLY MESS MENU"))
    }
}
