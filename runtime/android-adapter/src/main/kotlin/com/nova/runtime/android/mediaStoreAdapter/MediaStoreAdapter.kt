package com.nova.runtime.android.mediaStoreAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.3 — local media query abstraction. */
interface MediaStoreAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String>
}

object MediaStoreOperations {
    const val QUERY_IMAGES = "queryImages"
    const val QUERY_VIDEOS = "queryVideos"
    const val QUERY_AUDIO = "queryAudio"
}
