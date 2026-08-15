package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.internal.PermissionChecker
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityMetadata
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger

/**
 * Base provider that delegates execution to Android adapters and maps
 * [CapabilityResult] into capability-framework responses.
 */
abstract class AdapterDelegatingCapabilityProvider(
    protected val context: Context,
    protected val logger: NovaLogger,
) : CapabilityProvider {

    abstract override val providerId: String
    abstract override val capabilityType: String
    abstract override val version: String
    abstract override fun supportedOperations(): Set<String>
    abstract override fun requiredPermissions(): Set<String>

    protected abstract suspend fun dispatch(
        operation: String,
        parameters: Map<String, String>,
        traceId: java.util.UUID,
    ): CapabilityResult

    override fun metadata(): CapabilityMetadata =
        CapabilityMetadata(
            name = providerId,
            version = version,
            capabilityType = capabilityType,
            description = "Android adapter provider for $capabilityType",
            supportedOperations = supportedOperations(),
            requiredPermissions = requiredPermissions(),
            tags = mapOf("platform" to "android"),
        )

    override suspend fun health(): Boolean = true

    override suspend fun validate(request: CapabilityExecutionRequest): CapabilityValidationResult {
        if (request.operation !in supportedOperations() && request.operation != "rollback") {
            return CapabilityValidationResult.Invalid(
                RuntimeError(
                    code = "CAPABILITY_UNSUPPORTED_OPERATION",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = false,
                    userVisibleMessage = "Operation '${request.operation}' is not supported by $providerId.",
                    diagnostics = mapOf(
                        "providerId" to providerId,
                        "supportedOperations" to supportedOperations().joinToString(","),
                    ),
                ),
            )
        }

        val missingPermission = firstMissingPermissionForOperation(request.operation)
        if (missingPermission != null) {
            return CapabilityValidationResult.Invalid(
                RuntimeError(
                    code = "ANDROID_PERMISSION_DENIED",
                    category = ErrorCategory.PERMISSION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = true,
                    userVisibleMessage = "Permission required: $missingPermission",
                    diagnostics = mapOf(
                        "providerId" to providerId,
                        "permission" to missingPermission,
                        "operation" to request.operation,
                    ),
                ),
            )
        }

        return validateOperation(request)
    }

    protected open suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult = CapabilityValidationResult.Valid

    override suspend fun execute(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
        logger.debug(
            module = RuntimeModule.CAPABILITY.name,
            message = "$providerId executing ${request.operation}",
            traceId = request.traceId,
            metadata = mapOf(
                "providerId" to providerId,
                "operation" to request.operation,
            ),
        )

        if (request.operation == "rollback") {
            return CapabilityExecutionResponse.Success(
                output = mapOf(
                    "providerId" to providerId,
                    "operation" to "rollback",
                    "status" to "no_op",
                ),
            )
        }

        return when (
            val result = dispatch(
                operation = request.operation,
                parameters = request.parameters.filterKeys { !it.startsWith("simulate") },
                traceId = request.traceId,
            )
        ) {
            is CapabilityResult.Success ->
                CapabilityExecutionResponse.Success(
                    output = result.output + mapOf(
                        "providerId" to providerId,
                        "capabilityType" to capabilityType,
                        "operation" to request.operation,
                    ),
                )
            is CapabilityResult.Failure -> CapabilityExecutionResponse.Failure(result.error)
        }
    }

    protected open fun permissionsForOperation(operation: String): Set<String> = requiredPermissions()

    protected fun firstMissingPermissionForOperation(operation: String): String? =
        permissionsForOperation(operation).firstOrNull { permission ->
            !PermissionChecker.isGranted(context, permission)
        }

    protected fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")
}
