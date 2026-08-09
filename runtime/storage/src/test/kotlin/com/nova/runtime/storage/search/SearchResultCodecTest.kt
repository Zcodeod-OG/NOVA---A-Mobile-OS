package com.nova.runtime.storage.search

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultCodecTest {
    @Test
    fun encodePhotoHits_serializesItems() {
        val id = UUID.randomUUID()
        val page = SearchPage(
            items = listOf(
                PhotoSearchHit(
                    id = id,
                    uri = "content://photo/1",
                    takenAt = 100L,
                    ocrText = "invoice",
                    matchSource = PhotoMatchSource.ROOM_OCR,
                ),
            ),
            totalCount = 1,
            query = "invoice",
            limit = 20,
            offset = 0,
        )

        val encoded = SearchResultCodec.encodePhotoHits(page)

        assertEquals("1", encoded["count"])
        assertTrue(encoded["items"]!!.contains(id.toString()))
        assertTrue(encoded["items"]!!.contains("ROOM_OCR"))
    }

    @Test
    fun encodeDocumentHits_decodesJavaFileUriForm() {
        // java.io.File.toURI() yields "file:/path" (one slash), not "file:///path".
        val javaFileUri = "file:/storage/emulated/0/Download/BooklyProspectusReport.pdf"
        val page = SearchPage(
            items = listOf(
                DocumentSearchHit(
                    id = UUID.randomUUID(),
                    path = javaFileUri,
                    name = "BooklyProspectusReport.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    modifiedAt = 100L,
                ),
            ),
            totalCount = 1,
            query = "bookly prospectus report",
            limit = 20,
            offset = 0,
        )

        val decoded = checkNotNull(
            SearchResultCodec.decodeFirstShareableDocument(
                SearchResultCodec.encodeDocumentHits(page)["items"].orEmpty(),
            ),
        )
        assertEquals(
            "file:///storage/emulated/0/Download/BooklyProspectusReport.pdf",
            decoded.uri,
        )
        assertEquals("application/pdf", decoded.mimeType)
    }

    @Test
    fun encodeDocumentHits_decodesAbsoluteFilePathAsFileUri() {
        val path = "/storage/emulated/0/Download/BooklyProspectusReport.pdf"
        val page = SearchPage(
            items = listOf(
                DocumentSearchHit(
                    id = UUID.randomUUID(),
                    path = path,
                    name = "BooklyProspectusReport.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    modifiedAt = 100L,
                ),
            ),
            totalCount = 1,
            query = "bookly prospectus report",
            limit = 20,
            offset = 0,
        )

        val decoded = checkNotNull(
            SearchResultCodec.decodeFirstShareableDocument(
                SearchResultCodec.encodeDocumentHits(page)["items"].orEmpty(),
            ),
        )
        assertEquals("file://$path", decoded.uri)
        assertEquals("application/pdf", decoded.mimeType)
        assertEquals("BooklyProspectusReport.pdf", decoded.name)
    }

    @Test
    fun encodeDocumentHits_preservesContentUri() {
        val id = UUID.randomUUID()
        val uri = "content://media/external/downloads/10"
        val page = SearchPage(
            items = listOf(
                DocumentSearchHit(
                    id = id,
                    path = uri,
                    name = "invoice.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    modifiedAt = 100L,
                ),
            ),
            totalCount = 1,
            query = "invoice",
            limit = 20,
            offset = 0,
        )

        val encoded = SearchResultCodec.encodeDocumentHits(page)
        val decoded = checkNotNull(SearchResultCodec.decodeFirstShareableDocument(encoded["items"].orEmpty()))
        assertEquals(uri, decoded.uri)
        assertEquals("application/pdf", decoded.mimeType)
    }

    @Test
    fun encodeDocumentHits_roundTripsContentSnippet() {
        val uri = "content://media/external/downloads/11"
        val page = SearchPage(
            items = listOf(
                DocumentSearchHit(
                    id = UUID.randomUUID(),
                    path = uri,
                    name = "mess-menu.pdf",
                    extension = "pdf",
                    mimeType = "application/pdf",
                    modifiedAt = 100L,
                    contentSnippet = "FRIDAY\nLunch: Chole Bhature",
                ),
            ),
            totalCount = 1,
            query = "mess menu",
            limit = 20,
            offset = 0,
        )

        val decoded = checkNotNull(
            SearchResultCodec.decodeFirstShareableDocument(
                SearchResultCodec.encodeDocumentHits(page)["items"].orEmpty(),
            ),
        )
        assertEquals(uri, decoded.uri)
        assertEquals("FRIDAY\nLunch: Chole Bhature", decoded.contentSnippet)
        val encoded = SearchResultCodec.encodeDocumentHits(page)
        assertTrue(!encoded["userMessage"].isNullOrBlank())
        assertTrue(!encoded["answer"].isNullOrBlank())
    }

    @Test
    fun encodeSemanticHits_roundTripsContentSnippet() {
        val uri = "content://media/external/downloads/12"
        val page = SearchPage(
            items = listOf(
                SemanticSearchHit(
                    objectId = UUID.randomUUID(),
                    objectType = "document",
                    score = 0.9f,
                    title = "mess-menu.pdf",
                    snippet = uri,
                    contentSnippet = "FRIDAY\nDinner: Veg Biryani",
                ),
            ),
            totalCount = 1,
            query = "todays mess menu",
            limit = 20,
            offset = 0,
        )

        val decoded = checkNotNull(
            SearchResultCodec.decodeFirstShareableDocument(
                SearchResultCodec.encodeSemanticHits(page)["items"].orEmpty(),
            ),
        )
        assertEquals(uri, decoded.uri)
        assertEquals("mess-menu.pdf", decoded.name)
        assertEquals("FRIDAY\nDinner: Veg Biryani", decoded.contentSnippet)
    }

    @Test
    fun encodeSemanticHits_sanitizesSeparatorCharsInSnippet() {
        val uri = "content://media/external/downloads/13"
        val page = SearchPage(
            items = listOf(
                SemanticSearchHit(
                    objectId = UUID.randomUUID(),
                    objectType = "document",
                    score = 0.9f,
                    title = "menu.pdf",
                    snippet = uri,
                    contentSnippet = "Lunch | Dinner",
                ),
            ),
            totalCount = 1,
            query = "menu",
            limit = 20,
            offset = 0,
        )

        val decoded = checkNotNull(
            SearchResultCodec.decodeFirstShareableDocument(
                SearchResultCodec.encodeSemanticHits(page)["items"].orEmpty(),
            ),
        )
        assertEquals(uri, decoded.uri)
        assertEquals("Lunch / Dinner", decoded.contentSnippet)
    }
}
