package com.nova.runtime.capability.provider

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityMetadata
import com.nova.runtime.capability.model.CapabilityValidationResult

/** Abstract capability provider contract — IAS §6, TDD §15. */
interface CapabilityProvider {
    val providerId: String
    val capabilityType: String
    val version: String

    suspend fun execute(request: CapabilityExecutionRequest): CapabilityExecutionResponse
    suspend fun validate(request: CapabilityExecutionRequest): CapabilityValidationResult
    suspend fun health(): Boolean
    fun metadata(): CapabilityMetadata
    fun supportedOperations(): Set<String>
    fun requiredPermissions(): Set<String>
}
