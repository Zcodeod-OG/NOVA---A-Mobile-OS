package com.nova.runtime.android.storageAccessAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.8 — Storage Access Framework abstraction. */
interface StorageAccessAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String> = emptySet()
}

object StorageAccessOperations {
    const val BUILD_DOCUMENT_PICKER = "buildDocumentPicker"
    const val TAKE_PERSISTABLE_PERMISSION = "takePersistablePermission"
    const val OPEN_DOCUMENT = "openDocument"
    const val QUERY_DOWNLOADS = "queryDownloads"
}
