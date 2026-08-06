package com.nova.runtime.android.storageAccessAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — StorageAccessAdapter interface stub */
interface StorageAccessAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
