package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.ai.native.search.SemanticSearchService
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.storage.search.SearchResultCodec

class SemanticSearchCapabilityProvider(
    private val semanticSearchService: SemanticSearchService,
) : AbstractSearchCapabilityProvider(
    providerId = PROVIDER_ID,
    capabilityType = CAPABILITY_TYPE,
    version = VERSION,
    description = "Semantic vector search over indexed photos and documents",
) {
    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun executeSearch(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        val searchRequest = parseSearchRequest(request.parameters)
        val objectTypes = request.parameters["objectTypes"]
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: SemanticSearchService.DEFAULT_OBJECT_TYPES

        val page = semanticSearchService.search(
            request = searchRequest,
            traceId = request.traceId,
            objectTypes = objectTypes,
        )
        return CapabilityExecutionResponse.Success(
            SearchResultCodec.encodeSemanticHits(page) + mapOf(
                "providerId" to PROVIDER_ID,
                "capabilityType" to CAPABILITY_TYPE,
                "operation" to OPERATION_SEARCH,
                "objectTypes" to objectTypes.joinToString(","),
            ),
        )
    }

    companion object {
        const val PROVIDER_ID = "search-semantic"
        const val CAPABILITY_TYPE = "search.semantic"
        const val VERSION = "1.0.0"
    }
}
