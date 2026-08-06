package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.storage.search.DocumentSearchService
import com.nova.runtime.storage.search.SearchResultCodec

class DocumentSearchCapabilityProvider(
    private val documentSearchService: DocumentSearchService,
) : AbstractSearchCapabilityProvider(
    providerId = PROVIDER_ID,
    capabilityType = CAPABILITY_TYPE,
    version = VERSION,
    description = "Document metadata and full-text search over Room cache",
) {
    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun executeSearch(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        val searchRequest = parseSearchRequest(request.parameters)
        val page = documentSearchService.search(searchRequest, request.traceId)
        return CapabilityExecutionResponse.Success(
            SearchResultCodec.encodeDocumentHits(page) + mapOf(
                "providerId" to PROVIDER_ID,
                "capabilityType" to CAPABILITY_TYPE,
                "operation" to OPERATION_SEARCH,
            ),
        )
    }

    companion object {
        const val PROVIDER_ID = "search-documents"
        const val CAPABILITY_TYPE = "search.documents"
        const val VERSION = "1.0.0"
    }
}
