package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.storage.search.PhotoSearchService
import com.nova.runtime.storage.search.SearchResultCodec

class PhotoSearchCapabilityProvider(
    private val photoSearchService: PhotoSearchService,
) : AbstractSearchCapabilityProvider(
    providerId = PROVIDER_ID,
    capabilityType = CAPABILITY_TYPE,
    version = VERSION,
    description = "Photo search over MediaStore and Room OCR cache",
    permissions = setOf("nova.media.read"),
) {
    override fun requiredPermissions(): Set<String> = setOf("nova.media.read")

    override suspend fun executeSearch(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        val searchRequest = parseSearchRequest(request.parameters)
        val page = photoSearchService.search(searchRequest, request.traceId)
        return CapabilityExecutionResponse.Success(
            SearchResultCodec.encodePhotoHits(page) + mapOf(
                "providerId" to PROVIDER_ID,
                "capabilityType" to CAPABILITY_TYPE,
                "operation" to OPERATION_SEARCH,
            ),
        )
    }

    companion object {
        const val PROVIDER_ID = "search-photos"
        const val CAPABILITY_TYPE = "search.photos"
        const val VERSION = "1.0.0"
    }
}
