package com.nova.runtime.storage.dao

import com.nova.runtime.storage.StorageRobolectricTest
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DocumentDaoTest : StorageRobolectricTest() {
    private lateinit var database: com.nova.runtime.storage.database.NovaDatabase
    private lateinit var documentDao: DocumentDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        documentDao = database.documentDao()
    }

    @After
    fun tearDown() {
        // in-memory database is discarded when references are released
    }

    @Test
    fun insertAndGetById_persistsDocument() =
        runTest {
            val document = sampleDocument()

            documentDao.insert(document)

            val loaded = documentDao.getById(document.id)
            assertNotNull(loaded)
            assertEquals(document.name, loaded?.name)
            assertEquals(document.path, loaded?.path)
        }

    @Test
    fun searchByName_returnsMatchingDocuments() =
        runTest {
            val target =
                sampleDocument(name = "Quarterly Report", path = "/docs/report.pdf")
            documentDao.insert(target)
            documentDao.insert(sampleDocument(name = "Other File", path = "/docs/other.txt"))

            val results = documentDao.searchByName("Quarterly")

            assertEquals(1, results.size)
            assertEquals(target.id, results.first().id)
        }

    @Test
    fun delete_removesDocument() =
        runTest {
            val document = sampleDocument()
            documentDao.insert(document)

            documentDao.delete(document)

            assertNull(documentDao.getById(document.id))
        }

    @Test
    fun searchFullText_matchesPathAndExtension() =
        runTest {
            val target = sampleDocument(name = "readme", path = "/docs/quarterly/readme.txt", extension = "txt")
            documentDao.insert(target)
            documentDao.insert(sampleDocument(name = "other", path = "/other.pdf", extension = "pdf"))

            val byPath = documentDao.searchFullText("quarterly", limit = 10, offset = 0)
            val byExtension = documentDao.searchFullText("pdf", limit = 10, offset = 0)

            assertEquals(1, byPath.size)
            assertEquals(target.id, byPath.first().id)
            assertEquals(1, byExtension.size)
        }

    @Test
    fun listUnindexed_returnsDocumentsWithoutEmbedding() =
        runTest {
            val unindexed = sampleDocument(name = "pending")
            val embeddingId = UUID.randomUUID()
            val indexed = sampleDocument(name = "done").copy(embeddingId = embeddingId)
            database.embeddingDao().insert(
                EmbeddingEntity(
                    embeddingId = embeddingId,
                    objectType = "document",
                    objectId = indexed.id,
                    modelVersion = "test-v1",
                    dimension = 384,
                    createdAt = 1L,
                ),
            )
            documentDao.insert(unindexed)
            documentDao.insert(indexed)

            val results = documentDao.listUnindexed(limit = 10)

            assertEquals(1, results.size)
            assertEquals(unindexed.id, results.first().id)
        }

    @Test
    fun listMissingContentText_returnsExtractableDocsWithoutContent() =
        runTest {
            val embeddingId = UUID.randomUUID()
            database.embeddingDao().insert(
                EmbeddingEntity(
                    embeddingId = embeddingId,
                    objectType = "document",
                    objectId = UUID.randomUUID(),
                    modelVersion = "test-v1",
                    dimension = 384,
                    createdAt = 1L,
                ),
            )
            val needsBackfill = sampleDocument(
                name = "menu.pdf",
                path = "/docs/menu.pdf",
                extension = "pdf",
            ).copy(
                mimeType = "application/pdf",
                embeddingId = embeddingId,
                contentText = null,
            )
            val alreadyExtracted = sampleDocument(name = "notes.txt").copy(
                contentText = "hello",
                contentExtractStatus = com.nova.runtime.storage.search.ContentExtractStatus.SUCCESS,
            )
            val imageNeedsOcr = sampleDocument(
                name = "semester-timetable.png",
                path = "/docs/semester-timetable.png",
                extension = "png",
            ).copy(mimeType = "image/png", contentText = null)
            val failedNeedsRetry = sampleDocument(
                name = "locked-menu.pdf",
                path = "/docs/locked-menu.pdf",
                extension = "pdf",
            ).copy(
                mimeType = "application/pdf",
                contentText = "",
                contentExtractStatus = com.nova.runtime.storage.search.ContentExtractStatus.FAILED,
            )
            val legacyDocSkip = sampleDocument(
                name = "old-notes.doc",
                path = "/docs/old-notes.doc",
                extension = "doc",
            ).copy(
                mimeType = "application/msword",
                contentText = null,
                contentExtractStatus = com.nova.runtime.storage.search.ContentExtractStatus.NOT_TRIED,
            )
            val binarySkip = sampleDocument(
                name = "archive.zip",
                path = "/docs/archive.zip",
                extension = "zip",
            ).copy(mimeType = "application/zip", contentText = null)

            documentDao.insert(needsBackfill)
            documentDao.insert(alreadyExtracted)
            documentDao.insert(imageNeedsOcr)
            documentDao.insert(failedNeedsRetry)
            documentDao.insert(legacyDocSkip)
            documentDao.insert(binarySkip)

            val results = documentDao.listMissingContentText(limit = 10)

            assertEquals(3, results.size)
            assertTrue(results.any { it.id == needsBackfill.id })
            assertTrue(results.any { it.id == imageNeedsOcr.id })
            assertTrue(results.any { it.id == failedNeedsRetry.id })
            assertFalse(results.any { it.id == binarySkip.id })
            assertFalse(results.any { it.id == legacyDocSkip.id })
            assertFalse(results.any { it.id == alreadyExtracted.id })
        }

    @Test
    fun listMissingSummary_returnsDocsWithoutSummary() =
        runTest {
            val missing = sampleDocument(name = "bookly.pdf").copy(
                contentText = "BOOKLY PROSPECTUS",
                summary = null,
            )
            val present = sampleDocument(name = "menu.pdf").copy(
                contentText = "MENU",
                summary = "menu.pdf: MENU",
            )
            documentDao.insert(missing)
            documentDao.insert(present)

            val results = documentDao.listMissingSummary(limit = 10)

            assertEquals(1, results.size)
            assertEquals(missing.id, results.first().id)
        }

    private fun sampleDocument(
        id: UUID = UUID.randomUUID(),
        name: String = "notes.txt",
        path: String = "/storage/notes.txt",
        extension: String = "txt",
    ): DocumentEntity =
        DocumentEntity(
            id = id,
            path = path,
            name = name,
            extension = extension,
            mimeType = "text/plain",
            size = 128L,
            checksum = "abc123",
            createdAt = 1L,
            modifiedAt = 2L,
            indexedAt = null,
            projectId = null,
            embeddingId = null,
            importance = 1,
        )
}
