package com.nova.runtime.storage.search

import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentSearchServiceTest {
    private lateinit var dao: FakeDocumentDao
    private lateinit var service: DocumentSearchService

    @Before
    fun setUp() {
        dao = FakeDocumentDao()
        service = DocumentSearchService(dao, NoOpRuntimeLogger())
    }

    @Test
    fun search_returnsMatchingDocumentsWithPagination() =
        runTest {
            val report = sampleDocument(name = "Quarterly Report", path = "/docs/report.pdf")
            val budget = sampleDocument(name = "Budget", path = "/docs/budget.xlsx")
            dao.records[report.id] = report
            dao.records[budget.id] = budget

            val page = service.search(
                SearchRequest(query = "Quarterly", limit = 10, offset = 0),
                traceId = UUID.randomUUID(),
            )

            assertEquals(1, page.count)
            assertEquals(report.id, page.items.first().id)
            assertFalse(page.hasMore)
        }

    @Test
    fun search_emptyResults_returnsZeroCount() =
        runTest {
            val page = service.search(
                SearchRequest(query = "missing", limit = 10, offset = 0),
                traceId = UUID.randomUUID(),
            )

            assertEquals(0, page.count)
            assertEquals(0, page.totalCount)
        }

    @Test
    fun search_ranksContentMatchAboveFilenameOnly() =
        runTest {
            val filenameOnly = sampleDocument(
                name = "BooklyProspectusReport.pdf",
                path = "/docs/bookly.pdf",
            ).copy(contentText = "generic campus notes")
            val contentMatch = sampleDocument(
                name = "scan-042.pdf",
                path = "/docs/scan.pdf",
            ).copy(contentText = "BOOKLY PROSPECTUS REPORT placement outcomes")

            dao.records[filenameOnly.id] = filenameOnly
            dao.records[contentMatch.id] = contentMatch

            val page = service.search(
                SearchRequest(query = "bookly placement", limit = 10, offset = 0),
                traceId = UUID.randomUUID(),
            )

            assertEquals(2, page.count)
            assertEquals(contentMatch.id, page.items.first().id)
            assertTrue(page.items.first().score > page.items.last().score)
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

        override suspend fun getByPath(path: String): DocumentEntity? =
            records.values.firstOrNull { it.path == path }

        override suspend fun searchByName(query: String): List<DocumentEntity> =
            records.values.filter { it.name.contains(query, ignoreCase = true) }

        override suspend fun searchFullText(query: String, limit: Int, offset: Int): List<DocumentEntity> =
            records.values
                .filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.path.contains(query, ignoreCase = true) ||
                        it.extension.contains(query, ignoreCase = true) ||
                        it.summary.orEmpty().contains(query, ignoreCase = true) ||
                        it.contentText.orEmpty().contains(query, ignoreCase = true)
                }
                .sortedByDescending { it.modifiedAt }
                .drop(offset)
                .take(limit)

        override suspend fun countFullText(query: String): Int =
            records.values.count {
                it.name.contains(query, ignoreCase = true) ||
                    it.path.contains(query, ignoreCase = true) ||
                    it.extension.contains(query, ignoreCase = true) ||
                    it.summary.orEmpty().contains(query, ignoreCase = true) ||
                    it.contentText.orEmpty().contains(query, ignoreCase = true)
            }

        override suspend fun getByProjectId(projectId: UUID): List<DocumentEntity> =
            records.values.filter { it.projectId == projectId }

        override suspend fun listUnindexed(limit: Int): List<DocumentEntity> =
            records.values.filter { it.embeddingId == null }.take(limit)
        override suspend fun listMissingContentText(limit: Int): List<DocumentEntity> =
            records.values.filter { it.contentText == null }.take(limit)
        override suspend fun listMissingSummary(limit: Int): List<DocumentEntity> =
            records.values.filter { it.summary == null }.take(limit)
        override suspend fun countAll() = records.size
        override suspend fun countWithSummary() =
            records.values.count { !it.summary.isNullOrBlank() }
        override suspend fun countMissingSummary() =
            records.values.count { it.summary.isNullOrBlank() }
    }
}
