package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityMetadata
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.storage.search.SearchRequest

internal abstract class AbstractSearchCapabilityProvider(
    override val providerId: String,
    override val capabilityType: String,
    override val version: String,
    private val description: String,
    private val permissions: Set<String> = emptySet(),
) : CapabilityProvider {
    override fun metadata(): CapabilityMetadata =
        CapabilityMetadata(
            name = providerId,
            version = version,
            capabilityType = capabilityType,
            description = description,
            supportedOperations = supportedOperations(),
            requiredPermissions = permissions,
        )

    override fun supportedOperations(): Set<String> = setOf(OPERATION_SEARCH, OPERATION_ROLLBACK)

    override suspend fun health(): Boolean = true

    override suspend fun validate(request: CapabilityExecutionRequest): CapabilityValidationResult {
        if (request.operation !in supportedOperations()) {
            return invalidOperation(request.operation)
        }
        if (request.operation == OPERATION_SEARCH && request.parameters["query"].isNullOrBlank()) {
            return CapabilityValidationResult.Invalid(
                RuntimeError(
                    code = "SEARCH_MISSING_QUERY",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = true,
                    userVisibleMessage = "Search requires a non-empty query parameter.",
                ),
            )
        }
        return CapabilityValidationResult.Valid
    }

    override suspend fun execute(request: CapabilityExecutionRequest): CapabilityExecutionResponse =
        when (request.operation) {
            OPERATION_SEARCH -> executeSearch(request)
            OPERATION_ROLLBACK -> CapabilityExecutionResponse.Success(
                mapOf("status" to "search_rollback_noop"),
            )
            else -> {
                val validation = invalidOperation(request.operation)
                CapabilityExecutionResponse.Failure(
                    (validation as CapabilityValidationResult.Invalid).error,
                )
            }
        }

    protected abstract suspend fun executeSearch(
        request: CapabilityExecutionRequest,
    ): CapabilityExecutionResponse

    protected fun parseSearchRequest(parameters: Map<String, String>): SearchRequest =
        SearchRequest(
            query = parameters.getValue("query"),
            limit = parameters["limit"]?.toIntOrNull() ?: SearchRequest.DEFAULT_LIMIT,
            offset = parameters["offset"]?.toIntOrNull() ?: 0,
            indexOnQuery = parameters["indexOnQuery"]?.toBooleanStrictOrNull() ?: true,
        )

    private fun invalidOperation(operation: String): CapabilityValidationResult.Invalid =
        CapabilityValidationResult.Invalid(
            RuntimeError(
                code = "CAPABILITY_UNSUPPORTED_OPERATION",
                category = ErrorCategory.VALIDATION,
                severity = ErrorSeverity.MEDIUM,
                recoverable = false,
                userVisibleMessage = "Operation '$operation' is not supported by $providerId.",
                diagnostics = mapOf(
                    "providerId" to providerId,
                    "supportedOperations" to supportedOperations().joinToString(","),
                ),
            ),
        )

    companion object {
        const val OPERATION_SEARCH = "search"
        const val OPERATION_ROLLBACK = "rollback"
    }
}
