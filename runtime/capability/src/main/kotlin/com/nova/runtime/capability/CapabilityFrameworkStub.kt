package com.nova.runtime.capability

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult

class CapabilityFrameworkStub : CapabilityFramework {
    override suspend fun execute(request: CapabilityRequest): CapabilityResult =
        CapabilityResult.Failure(
            RuntimeError(
                code = "CAPABILITY_NOT_IMPLEMENTED",
                category = ErrorCategory.INFRASTRUCTURE,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Capability framework not yet implemented.",
            ),
        )

    override suspend fun health(capabilityType: String): Boolean = false
    override suspend fun discover(): List<String> = emptyList()
}
