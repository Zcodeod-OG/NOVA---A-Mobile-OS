package com.nova.runtime.storage.search

import java.util.UUID
import org.junit.Assert.assertEquals
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
}
