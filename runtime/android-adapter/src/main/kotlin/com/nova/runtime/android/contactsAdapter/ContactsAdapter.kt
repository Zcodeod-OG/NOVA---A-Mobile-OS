package com.nova.runtime.android.contactsAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — ContactsAdapter interface stub */
interface ContactsAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
