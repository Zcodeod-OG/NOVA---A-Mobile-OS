package com.nova.runtime.storage.coordinator

import com.nova.runtime.storage.StorageRobolectricTest
import com.nova.runtime.storage.cache.LruStorageCache
import com.nova.runtime.storage.repository.ContactRepositoryImpl
import com.nova.runtime.storage.repository.DocumentRepositoryImpl
import com.nova.runtime.storage.repository.EmbeddingRepositoryImpl
import com.nova.runtime.storage.repository.ExecutionHistoryRepositoryImpl
import com.nova.runtime.storage.repository.PhotoRepositoryImpl
import com.nova.runtime.storage.repository.PreferenceRepositoryImpl
import com.nova.runtime.storage.repository.ProjectRepositoryImpl
import com.nova.runtime.storage.repository.SessionRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class StorageCoordinatorImplTest : StorageRobolectricTest() {
    private lateinit var coordinator: StorageCoordinatorImpl

    @Before
    fun setUp() {
        val database = createInMemoryDatabase()
        coordinator =
            StorageCoordinatorImpl(
                database = database,
                documentRepository = DocumentRepositoryImpl(database.documentDao()),
                photoRepository = PhotoRepositoryImpl(database.photoDao()),
                contactRepository = ContactRepositoryImpl(database.contactDao()),
                projectRepository = ProjectRepositoryImpl(database.projectDao()),
                sessionRepository = SessionRepositoryImpl(database.sessionDao()),
                preferenceRepository = PreferenceRepositoryImpl(database.preferenceDao()),
                executionHistoryRepository = ExecutionHistoryRepositoryImpl(database.executionHistoryDao()),
                embeddingRepository = EmbeddingRepositoryImpl(database.embeddingDao()),
                cache = LruStorageCache(32),
            )
    }

    @Test
    fun storeAndRetrieve_roundTripsDocument() =
        runTest {
            val id = UUID.randomUUID()
            val payload =
                mapOf(
                    "collection" to StorageCollections.DOCUMENTS,
                    "id" to id.toString(),
                    "path" to "/docs/spec.pdf",
                    "name" to "spec.pdf",
                    "extension" to "pdf",
                    "mimeType" to "application/pdf",
                    "size" to "1024",
                    "checksum" to "hash",
                    "createdAt" to "1",
                    "modifiedAt" to "2",
                    "importance" to "1",
                )

            coordinator.store(id.toString(), payload)
            val retrieved = coordinator.retrieve(id.toString())

            assertEquals("spec.pdf", retrieved?.get("name"))
        }

    @Test
    fun query_returnsMatchingDocuments() =
        runTest {
            val id = UUID.randomUUID()
            coordinator.store(
                id.toString(),
                mapOf(
                    "collection" to StorageCollections.DOCUMENTS,
                    "id" to id.toString(),
                    "path" to "/docs/budget.pdf",
                    "name" to "Budget Plan",
                    "extension" to "pdf",
                    "mimeType" to "application/pdf",
                    "size" to "512",
                    "checksum" to "hash",
                    "createdAt" to "1",
                    "modifiedAt" to "2",
                    "importance" to "1",
                ),
            )

            val results = coordinator.query(StorageCollections.DOCUMENTS, mapOf("query" to "Budget"))

            assertEquals(1, results.size)
            assertEquals("Budget Plan", results.first()["name"])
        }
}
