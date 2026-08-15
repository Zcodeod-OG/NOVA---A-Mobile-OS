package com.nova.runtime.storage.search

import com.nova.runtime.storage.entities.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DocumentContentRankerTest {
    @Test
    fun scoreDocument_prefersContentTokensOverFilenameOnly() {
        val filenameOnly = sampleDocument(
            name = "notes.pdf",
            contentText = "generic campus handbook chapter about dining",
        )
        val contentMatch = sampleDocument(
            name = "scan-042.pdf",
            contentText = "BOOKLY PROSPECTUS REPORT placement outcomes for Bookly students",
        )
        val tokens = listOf("bookly", "prospectus", "placement")

        val filenameScore = DocumentContentRanker.scoreDocument(filenameOnly, tokens)
        val contentScore = DocumentContentRanker.scoreDocument(contentMatch, tokens)

        assertTrue(contentScore > filenameScore)
    }

    @Test
    fun rankDocuments_ordersContentMatchFirst() {
        val decoy = sampleDocument(name = "bookly-prospectus.pdf", contentText = "table of contents")
        val target = sampleDocument(
            name = "random-scan.pdf",
            contentText = "BOOKLY PROSPECTUS REPORT detailed placement statistics",
        )
        val ranked = DocumentContentRanker.rankDocuments(
            documents = listOf(decoy, target),
            tokens = listOf("bookly", "placement"),
        )
        assertEquals(target.id, ranked.first().first.id)
    }

    private fun sampleDocument(
        name: String,
        contentText: String,
    ): DocumentEntity =
        DocumentEntity(
            id = UUID.randomUUID(),
            path = "/docs/$name",
            name = name,
            extension = "pdf",
            mimeType = "application/pdf",
            size = 100L,
            checksum = "",
            createdAt = 1L,
            modifiedAt = 2L,
            indexedAt = null,
            projectId = null,
            embeddingId = null,
            importance = 0,
            contentText = contentText,
            contentExtractStatus = ContentExtractStatus.SUCCESS,
        )
}
