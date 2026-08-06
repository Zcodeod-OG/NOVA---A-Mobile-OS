package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.storage.search.DocumentSearchService
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchCapabilityProviderTest {
    private val traceId = UUID.randomUUID()

    @Test
    fun documentProvider_rejectsMissingQuery() =
        runTest {
            val provider = DocumentSearchCapabilityProvider(
                documentSearchService = DocumentSearchService(FakeDocumentDao(), NoOpRuntimeLogger()),
            )

            val validation = provider.validate(
                CapabilityExecutionRequest(
                    operation = "search",
                    parameters = emptyMap(),
                    traceId = traceId,
                ),
            )

            assert(validation is com.nova.runtime.capability.model.CapabilityValidationResult.Invalid)
        }

    @Test
    fun documentProvider_executesSearch() =
        runTest {
            val provider = DocumentSearchCapabilityProvider(
                documentSearchService = DocumentSearchService(FakeDocumentDao(), NoOpRuntimeLogger()),
            )

            val response = provider.execute(
                CapabilityExecutionRequest(
                    operation = "search",
                    parameters = mapOf("query" to "budget"),
                    traceId = traceId,
                ),
            )

            assert(response is com.nova.runtime.capability.model.CapabilityExecutionResponse.Success)
            val output = (response as com.nova.runtime.capability.model.CapabilityExecutionResponse.Success).output
            assertEquals("search.documents", output["capabilityType"])
            assertEquals("search", output["operation"])
        }

    private class FakeDocumentDao : com.nova.runtime.storage.dao.DocumentDao {
        override suspend fun insert(document: com.nova.runtime.storage.entities.DocumentEntity) = Unit
        override suspend fun update(document: com.nova.runtime.storage.entities.DocumentEntity) = Unit
        override suspend fun delete(document: com.nova.runtime.storage.entities.DocumentEntity) = Unit
        override suspend fun getById(id: UUID) = null
        override fun observeById(id: UUID) = kotlinx.coroutines.flow.emptyFlow<com.nova.runtime.storage.entities.DocumentEntity?>()
        override suspend fun getByPath(path: String) = null
        override suspend fun searchByName(query: String) = emptyList<com.nova.runtime.storage.entities.DocumentEntity>()
        override suspend fun searchFullText(query: String, limit: Int, offset: Int) = emptyList<com.nova.runtime.storage.entities.DocumentEntity>()
        override suspend fun countFullText(query: String) = 0
        override suspend fun getByProjectId(projectId: UUID) = emptyList<com.nova.runtime.storage.entities.DocumentEntity>()
        override suspend fun listUnindexed(limit: Int) = emptyList<com.nova.runtime.storage.entities.DocumentEntity>()
    }
}
