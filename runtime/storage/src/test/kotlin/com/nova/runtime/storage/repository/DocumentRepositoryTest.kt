package com.nova.runtime.storage.repository

import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DocumentRepositoryTest {
    private lateinit var dao: FakeDocumentDao
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        dao = FakeDocumentDao()
        repository = DocumentRepositoryImpl(dao)
    }

    @Test
    fun insert_delegatesToDao() =
        runTest {
            val document = sampleDocument()

            repository.insert(document)

            assertEquals(document, dao.records[document.id])
        }

    @Test
    fun search_delegatesToDao() =
        runTest {
            val document = sampleDocument(name = "Budget")
            dao.records[document.id] = document

            val results = repository.search("Budget")

            assertEquals(listOf(document), results)
        }

    private fun sampleDocument(): DocumentEntity =
        DocumentEntity(
            id = UUID.randomUUID(),
            path = "/docs/budget.pdf",
            name = "Budget",
            extension = "pdf",
            mimeType = "application/pdf",
            size = 2048L,
            checksum = "hash",
            createdAt = 10L,
            modifiedAt = 20L,
            indexedAt = null,
            projectId = null,
            embeddingId = null,
            importance = 0,
        )

    private class FakeDocumentDao : DocumentDao {
        val records = mutableMapOf<UUID, DocumentEntity>()

        override suspend fun insert(document: DocumentEntity) {
            records[document.id] = document
        }

        override suspend fun update(document: DocumentEntity) {
            records[document.id] = document
        }

        override suspend fun delete(document: DocumentEntity) {
            records.remove(document.id)
        }

        override suspend fun getById(id: UUID): DocumentEntity? = records[id]

        override fun observeById(id: UUID): Flow<DocumentEntity?> = emptyFlow()

        override suspend fun getByPath(path: String): DocumentEntity? = records.values.firstOrNull { it.path == path }

        override suspend fun searchByName(query: String): List<DocumentEntity> =
            records.values.filter { it.name.contains(query, ignoreCase = true) }

        override suspend fun searchFullText(query: String, limit: Int, offset: Int): List<DocumentEntity> =
            searchByName(query).drop(offset).take(limit)

        override suspend fun countFullText(query: String): Int = searchByName(query).size

        override suspend fun getByProjectId(projectId: UUID): List<DocumentEntity> =
            records.values.filter { it.projectId == projectId }

        override suspend fun listUnindexed(limit: Int): List<DocumentEntity> =
            records.values.filter { it.embeddingId == null }.take(limit)
    }
}
