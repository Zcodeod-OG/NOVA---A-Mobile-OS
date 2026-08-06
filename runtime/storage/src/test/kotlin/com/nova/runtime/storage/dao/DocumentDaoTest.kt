package com.nova.runtime.storage.dao

import com.nova.runtime.storage.StorageRobolectricTest
import com.nova.runtime.storage.entities.DocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            val indexed = sampleDocument(name = "done").copy(embeddingId = UUID.randomUUID())
            documentDao.insert(unindexed)
            documentDao.insert(indexed)

            val results = documentDao.listUnindexed(limit = 10)

            assertEquals(1, results.size)
            assertEquals(unindexed.id, results.first().id)
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
