package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.ai.native.search.SemanticSearchService
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.storage.search.PhotoSearchService
import com.nova.runtime.storage.search.SearchResultCodec

class PhotoSearchCapabilityProvider(
    private val photoSearchService: PhotoSearchService,
    private val semanticSearchService: SemanticSearchService,
) : AbstractSearchCapabilityProvider(
    providerId = PROVIDER_ID,
    capabilityType = CAPABILITY_TYPE,
    version = VERSION,
    description = "Photo search with semantic vector KNN and OCR keyword fallback",
    permissions = setOf("nova.media.read"),
) {
    override fun requiredPermissions(): Set<String> = setOf("nova.media.read")

    override suspend fun executeSearch(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        val searchRequest = parseSearchRequest(request.parameters)

        val semanticPage = semanticSearchService.search(
            request = searchRequest,
            traceId = request.traceId,
            objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_PHOTO),
        )
        if (semanticPage.items.isNotEmpty()) {
            return CapabilityExecutionResponse.Success(
                SearchResultCodec.encodeSemanticHits(semanticPage) + searchMetadata(
                    searchMode = SEARCH_MODE_SEMANTIC,
                ),
            )
        }

        val keywordPage = photoSearchService.search(searchRequest, request.traceId)
        return CapabilityExecutionResponse.Success(
            SearchResultCodec.encodePhotoHits(keywordPage) + searchMetadata(
                searchMode = SEARCH_MODE_KEYWORD,
            ),
        )
    }

    private fun searchMetadata(searchMode: String): Map<String, String> =
        mapOf(
            "providerId" to PROVIDER_ID,
            "capabilityType" to CAPABILITY_TYPE,
            "operation" to OPERATION_SEARCH,
            "searchMode" to searchMode,
        )

    companion object {
        const val PROVIDER_ID = "search-photos"
        const val CAPABILITY_TYPE = "search.photos"
        const val VERSION = "1.0.0"
        private const val SEARCH_MODE_SEMANTIC = "semantic"
        private const val SEARCH_MODE_KEYWORD = "keyword"
    }
}
