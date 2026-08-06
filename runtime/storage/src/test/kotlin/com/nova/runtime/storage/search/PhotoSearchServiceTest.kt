package com.nova.runtime.storage.search

import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhotoSearchServiceTest {
    private lateinit var dao: FakePhotoDao
    private lateinit var mediaStore: FakeMediaStoreQueryPort
    private lateinit var service: PhotoSearchService

    @Before
    fun setUp() {
        dao = FakePhotoDao()
        mediaStore = FakeMediaStoreQueryPort()
        service = PhotoSearchService(dao, mediaStore, NoOpRuntimeLogger())
    }

    @Test
    fun search_matchesOcrTextInRoom() =
        runTest {
            val photo = samplePhoto(ocrText = "Invoice from Acme Corp")
            dao.records[photo.id] = photo

            val page = service.search(
                SearchRequest(query = "acme", limit = 10),
                traceId = UUID.randomUUID(),
            )

            assertEquals(1, page.count)
            assertEquals(PhotoMatchSource.ROOM_OCR, page.items.first().matchSource)
        }

    @Test
    fun search_mergesMediaStoreMatches() =
        runTest {
            mediaStore.items = listOf(
                MediaImageItem(
                    mediaId = 1L,
                    uri = "content://media/external/images/media/1",
                    displayName = "sunset_beach.jpg",
                    mimeType = "image/jpeg",
                    dateAdded = 100L,
                    size = 1024L,
                ),
            )

            val page = service.search(
                SearchRequest(query = "sunset", limit = 10),
                traceId = UUID.randomUUID(),
            )

            assertEquals(1, page.count)
            assertEquals(PhotoMatchSource.MEDIA_STORE, page.items.first().matchSource)
        }

    @Test
    fun search_emptyQueryResults_returnsEmptyPage() =
        runTest {
            val page = service.search(
                SearchRequest(query = "nonexistent", limit = 10),
                traceId = UUID.randomUUID(),
            )

            assertEquals(0, page.count)
            assertTrue(page.items.isEmpty())
        }

    private fun samplePhoto(
        id: UUID = UUID.randomUUID(),
        ocrText: String? = "sample text",
    ): PhotoEntity =
        PhotoEntity(
            id = id,
            uri = "content://media/external/images/media/$id",
            takenAt = 50L,
            width = 100,
            height = 100,
            latitude = null,
            longitude = null,
            ocrText = ocrText,
            embeddingId = null,
            favorite = false,
        )

    private class FakeMediaStoreQueryPort : MediaStoreQueryPort {
        var items: List<MediaImageItem> = emptyList()

        override suspend fun queryImages(limit: Int): MediaImageQueryResult =
            MediaImageQueryResult(items.take(limit))
    }

    private class FakePhotoDao : PhotoDao {
        val records = mutableMapOf<UUID, PhotoEntity>()

        override suspend fun insert(photo: PhotoEntity) {
            records[photo.id] = photo
        }

        override suspend fun update(photo: PhotoEntity) {
            records[photo.id] = photo
        }

        override suspend fun delete(photo: PhotoEntity) {
            records.remove(photo.id)
        }

        override suspend fun getById(id: UUID): PhotoEntity? = records[id]

        override fun observeById(id: UUID): Flow<PhotoEntity?> = emptyFlow()

        override suspend fun getByUri(uri: String): PhotoEntity? =
            records.values.firstOrNull { it.uri == uri }

        override suspend fun searchByOcr(query: String): List<PhotoEntity> =
            records.values.filter { it.ocrText?.contains(query, ignoreCase = true) == true }

        override suspend fun searchByOcrPaged(query: String, limit: Int, offset: Int): List<PhotoEntity> =
            records.values
                .filter { it.ocrText?.contains(query, ignoreCase = true) == true }
                .sortedByDescending { it.takenAt }
                .drop(offset)
                .take(limit)

        override suspend fun countByOcr(query: String): Int =
            records.values.count { it.ocrText?.contains(query, ignoreCase = true) == true }

        override suspend fun listRecent(limit: Int): List<PhotoEntity> =
            records.values.sortedByDescending { it.takenAt }.take(limit)

        override suspend fun listUnindexedWithOcr(limit: Int): List<PhotoEntity> =
            records.values.filter { it.embeddingId == null && !it.ocrText.isNullOrBlank() }.take(limit)
    }
}
