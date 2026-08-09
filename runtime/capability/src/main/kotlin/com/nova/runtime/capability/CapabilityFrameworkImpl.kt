package com.nova.runtime.capability

import com.nova.runtime.capability.events.CapabilityEventPublisher
import com.nova.runtime.capability.health.CapabilityHealthMonitor
import com.nova.runtime.capability.lifecycle.CapabilityLifecycleManager
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.model.CapabilityResolutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.capability.resolver.CapabilityProviderResolver
import com.nova.runtime.capability.transaction.CapabilityTransactionManager
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import kotlin.system.measureTimeMillis

/**
 * Capability Framework — TDD §15 / MSP §11.
 * Orchestrates resolution, validation, transactional execution, health, and events.
 */
class CapabilityFrameworkImpl(
    private val registry: CapabilityRegistry,
    private val resolver: CapabilityProviderResolver,
    private val lifecycleManager: CapabilityLifecycleManager,
    private val healthMonitor: CapabilityHealthMonitor,
    private val transactionManager: CapabilityTransactionManager,
    private val eventPublisher: CapabilityEventPublisher,
    private val logger: NovaLogger,
) : CapabilityFramework {

    override suspend fun execute(request: CapabilityRequest): CapabilityResult {
        logger.debug(
            RuntimeModule.CAPABILITY.name,
            "Executing capability ${request.capabilityType}.${request.operation}",
            request.traceId,
            metadata = mapOf("capabilityType" to request.capabilityType),
        )

        val resolution = resolver.resolve(
            CapabilityResolutionRequest(
                capabilityType = request.capabilityType,
                operation = request.operation,
                constraints = request.parameters,
            ),
        )

        if (resolution == null) {
            eventPublisher.publishUnavailable(
                traceId = request.traceId,
                capabilityType = request.capabilityType,
                operation = request.operation,
            )
            return CapabilityResult.Failure(
                CapabilityErrors.unavailable(request.capabilityType, request.operation),
            )
        }

        val metadata = resolution.metadata
        val provider = resolution.provider
        // Use the alias-matched operation the provider actually supports
        // (pipeline may send short form like "create" / "send_message").
        val operation = resolution.resolvedOperation

        eventPublisher.publishResolved(
            traceId = request.traceId,
            capabilityType = request.capabilityType,
            providerId = provider.providerId,
            operation = operation,
        )
        eventPublisher.publishSelected(
            traceId = request.traceId,
            capabilityType = request.capabilityType,
            providerId = provider.providerId,
            providerVersion = provider.version,
        )

        val state = lifecycleManager.getState(metadata.name, metadata.version)
        if (state != CapabilityLifecycleState.ACTIVE) {
            eventPublisher.publishFailed(
                traceId = request.traceId,
                capabilityType = request.capabilityType,
                errorCode = "CAPABILITY_NOT_ACTIVE",
                providerId = provider.providerId,
            )
            return CapabilityResult.Failure(
                CapabilityErrors.notActive(metadata.name, state?.name ?: "unknown"),
            )
        }

        val execRequest = CapabilityExecutionRequest(
            operation = operation,
            parameters = request.parameters,
            traceId = request.traceId,
        )

        when (val validation = provider.validate(execRequest)) {
            is CapabilityValidationResult.Invalid -> {
                eventPublisher.publishFailed(
                    traceId = request.traceId,
                    capabilityType = request.capabilityType,
                    errorCode = validation.error.code,
                    providerId = provider.providerId,
                )
                return CapabilityResult.Failure(validation.error)
            }
            CapabilityValidationResult.Valid -> Unit
        }

        val transaction = transactionManager.begin(request.traceId)
        var result: CapabilityResult = CapabilityResult.Failure(CapabilityErrors.internalError())
        var latencyMs = 0L

        try {
            latencyMs = measureTimeMillis {
                when (val response = transaction.execute(provider, execRequest)) {
                    is CapabilityExecutionResponse.Success -> {
                        transaction.commit()
                        healthMonitor.recordHeartbeat(metadata.name, metadata.version)
                        result = CapabilityResult.Success(response.output)
                    }
                    is CapabilityExecutionResponse.Failure -> {
                        transaction.rollback()
                        healthMonitor.recordFailure(
                            metadata.name,
                            metadata.version,
                            response.error.code,
                        )
                        eventPublisher.publishFailed(
                            traceId = request.traceId,
                            capabilityType = request.capabilityType,
                            errorCode = response.error.code,
                            providerId = provider.providerId,
                        )
                        result = CapabilityResult.Failure(response.error)
                    }
                }
            }
        } catch (throwable: Throwable) {
            transaction.rollback()
            healthMonitor.recordFailure(metadata.name, metadata.version, throwable.message ?: "unknown")
            eventPublisher.publishFailed(
                traceId = request.traceId,
                capabilityType = request.capabilityType,
                errorCode = "CAPABILITY_EXECUTION_ERROR",
                providerId = provider.providerId,
            )
            logger.error(
                RuntimeModule.CAPABILITY.name,
                "Capability execution failed",
                request.traceId,
                throwable,
            )
            result = CapabilityResult.Failure(CapabilityErrors.executionError(throwable))
        }

        if (result is CapabilityResult.Success) {
            eventPublisher.publishExecuted(
                traceId = request.traceId,
                capabilityType = request.capabilityType,
                providerId = provider.providerId,
                operation = operation,
                latencyMs = latencyMs,
                transactionId = transaction.transactionId,
                userMessage = result.output["userMessage"],
                answer = result.output["answer"],
                matchDebug = result.output["matchDebug"],
            )
        }

        logger.info(
            RuntimeModule.CAPABILITY.name,
            "Capability ${request.capabilityType}.$operation completed",
            request.traceId,
            durationMs = latencyMs,
            metadata = mapOf(
                "providerId" to provider.providerId,
                "requestedOperation" to request.operation,
                "resolvedOperation" to operation,
                "success" to (result is CapabilityResult.Success).toString(),
            ),
        )

        return result
    }

    override suspend fun health(capabilityType: String): Boolean {
        val active = registry.lookupByType(capabilityType)
            .filter { registration ->
                lifecycleManager.getState(registration.metadata.name, registration.metadata.version) ==
                    CapabilityLifecycleState.ACTIVE
            }

        if (active.isEmpty()) return false

        return active.any { registration ->
            healthMonitor.checkHealth(registration.metadata.name, registration.metadata.version)
        }
    }

    override suspend fun discover(): List<String> =
        registry.all()
            .filter { registration ->
                lifecycleManager.getState(registration.metadata.name, registration.metadata.version) ==
                    CapabilityLifecycleState.ACTIVE
            }
            .map { it.metadata.key }
}

/** Structured capability errors per IAS §9. */
object CapabilityErrors {
    fun unavailable(capabilityType: String, operation: String): RuntimeError =
        RuntimeError(
            code = "CAPABILITY_UNAVAILABLE",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.MEDIUM,
            recoverable = true,
            userVisibleMessage = "No provider available for $capabilityType operation '$operation'.",
            diagnostics = mapOf(
                "capabilityType" to capabilityType,
                "operation" to operation,
            ),
        )

    fun notActive(name: String, state: String): RuntimeError =
        RuntimeError(
            code = "CAPABILITY_NOT_ACTIVE",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.MEDIUM,
            recoverable = true,
            userVisibleMessage = "Capability $name is not active (state=$state).",
        )

    fun internalError(): RuntimeError =
        RuntimeError(
            code = "CAPABILITY_INTERNAL_ERROR",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.HIGH,
            recoverable = false,
            userVisibleMessage = "Capability execution failed unexpectedly.",
        )

    fun executionError(throwable: Throwable): RuntimeError =
        RuntimeError(
            code = "CAPABILITY_EXECUTION_ERROR",
            category = ErrorCategory.EXECUTION,
            severity = ErrorSeverity.HIGH,
            recoverable = true,
            userVisibleMessage = "Capability execution failed.",
            diagnostics = mapOf(
                "exception" to throwable.javaClass.simpleName,
                "message" to (throwable.message ?: ""),
            ),
        )
}
