package com.nova.runtime.ai.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentTextExtractorTest {

    @Test
    fun isExtractable_supportsPdfImageDocxAndPlainText() {
        assertTrue(DocumentTextExtractor.isExtractable("application/pdf", "pdf"))
        assertTrue(DocumentTextExtractor.isExtractable("image/png", "png"))
        assertTrue(
            DocumentTextExtractor.isExtractable(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx",
            ),
        )
        assertTrue(DocumentTextExtractor.isExtractable("text/plain", "txt"))
        assertTrue(DocumentTextExtractor.isExtractable("text/html", "html"))
    }

    @Test
    fun isExtractable_rejectsLegacyDoc() {
        assertFalse(DocumentTextExtractor.isExtractable("application/msword", "doc"))
        assertFalse(DocumentTextExtractor.isExtractable("", "doc"))
        assertFalse(DocumentTextExtractor.isExtractable("application/msword", ""))
        assertFalse("doc" in DocumentTextExtractor.OFFICE_EXTENSIONS)
        assertTrue("docx" in DocumentTextExtractor.OFFICE_EXTENSIONS)
    }
}
