package com.nova.runtime.capability

import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult

interface CapabilityFramework {
    suspend fun execute(request: CapabilityRequest): CapabilityResult
    suspend fun health(capabilityType: String): Boolean
    suspend fun discover(): List<String>
}
