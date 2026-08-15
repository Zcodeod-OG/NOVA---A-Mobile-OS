package com.nova.runtime.capability.provider

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityMetadata
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError

/** Placeholder provider that returns deterministic stub output — no Android APIs. */
abstract class StubCapabilityProvider(
    override val providerId: String,
    override val capabilityType: String,
    override val version: String,
    private val operations: Set<String>,
    private val permissions: Set<String> = emptySet(),
    private val description: String = "Stub provider for $capabilityType",
) : CapabilityProvider {

    override fun metadata(): CapabilityMetadata =
        CapabilityMetadata(
            name = providerId,
            version = version,
            capabilityType = capabilityType,
            description = description,
            supportedOperations = operations,
            requiredPermissions = permissions,
        )

    override fun supportedOperations(): Set<String> = operations

    override fun requiredPermissions(): Set<String> = permissions

    override suspend fun health(): Boolean = true

    override suspend fun validate(request: CapabilityExecutionRequest): CapabilityValidationResult {
        if (request.operation !in operations && request.operation != "rollback") {
            return CapabilityValidationResult.Invalid(
                RuntimeError(
                    code = "CAPABILITY_UNSUPPORTED_OPERATION",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = false,
                    userVisibleMessage = "Operation '${request.operation}' is not supported by $providerId.",
                    diagnostics = mapOf(
                        "providerId" to providerId,
                        "supportedOperations" to operations.joinToString(","),
                    ),
                ),
            )
        }
        return CapabilityValidationResult.Valid
    }

    override suspend fun execute(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        if (request.parameters["simulateFailure"] == "true") {
            return CapabilityExecutionResponse.Failure(
                RuntimeError(
                    code = "CAPABILITY_SIMULATED_FAILURE",
                    category = ErrorCategory.EXECUTION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = true,
                    userVisibleMessage = "Simulated capability failure.",
                ),
            )
        }

        return CapabilityExecutionResponse.Success(
            output = buildMap {
                put("providerId", providerId)
                put("capabilityType", capabilityType)
                put("operation", request.operation)
                put("status", if (request.operation == "rollback") "stub_rolled_back" else "stub_executed")
                request.transactionId?.let { put("transactionId", it.toString()) }
                request.parameters
                    .filterKeys { key -> !key.startsWith("simulate") }
                    .forEach { (key, value) -> put(key, value) }
            },
        )
    }
}
