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

    private fun sampleDocument(
        id: UUID = UUID.randomUUID(),
        name: String = "notes.txt",
        path: String = "/storage/notes.txt",
    ): DocumentEntity =
        DocumentEntity(
            id = id,
            path = path,
            name = name,
            extension = "txt",
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
