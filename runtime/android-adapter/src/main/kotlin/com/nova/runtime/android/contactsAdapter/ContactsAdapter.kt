package com.nova.runtime.android.contactsAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.4 — read/write contact abstraction. */
interface ContactsAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String>
}

object ContactsOperations {
    const val SEARCH = "search"
    const val RETRIEVE = "retrieve"
    const val CREATE = "create"
    const val UPDATE = "update"
}
