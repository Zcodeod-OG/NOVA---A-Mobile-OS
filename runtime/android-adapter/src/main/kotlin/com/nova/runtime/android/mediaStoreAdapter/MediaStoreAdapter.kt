package com.nova.runtime.android.mediaStoreAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — MediaStoreAdapter interface stub */
interface MediaStoreAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
