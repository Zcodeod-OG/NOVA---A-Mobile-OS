package com.nova.runtime.execution.worker

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID
import kotlinx.coroutines.delay

sealed class NodeExecutionOutcome {
    data class Success(val outputs: Map<String, String>) : NodeExecutionOutcome()
    data class Failure(
        val error: RuntimeError,
        val retryable: Boolean = true,
    ) : NodeExecutionOutcome()
}

/** Executes a single action node (stub capability invocation). */
interface ActionExecutor {
    suspend fun execute(node: ActionNode, traceId: UUID): NodeExecutionOutcome
    suspend fun rollback(node: ActionNode, traceId: UUID): NodeExecutionOutcome
}

class StubCapabilityActionExecutor(
    private val capabilityFramework: CapabilityFramework,
) : ActionExecutor {

    override suspend fun execute(node: ActionNode, traceId: UUID): NodeExecutionOutcome {
        if (node.inputs["simulateFailure"] == "true") {
            return NodeExecutionOutcome.Failure(
                error = RuntimeError(
                    code = "EXECUTION_SIMULATED_FAILURE",
                    category = ErrorCategory.EXECUTION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = node.retryPolicy != "none",
                    userVisibleMessage = "Simulated action failure.",
                ),
                retryable = node.retryPolicy != "none",
            )
        }

        val simulatedDelayMs = node.inputs["simulateDelayMs"]?.toLongOrNull() ?: 0L
        if (simulatedDelayMs > 0) {
            delay(simulatedDelayMs)
        }

        val capabilityType = node.inputs["capabilityType"] ?: inferCapabilityType(node.actionType)
        val operation = node.inputs["operation"] ?: node.actionType

        when (
            val result = capabilityFramework.execute(
                CapabilityRequest(
                    capabilityType = capabilityType,
                    operation = operation,
                    parameters = node.inputs,
                    traceId = traceId,
                ),
            )
        ) {
            is CapabilityResult.Success -> {
                return NodeExecutionOutcome.Success(result.output.ifEmpty { node.outputs })
            }
            is CapabilityResult.Failure -> {
                return NodeExecutionOutcome.Failure(
                    error = result.error,
                    retryable = result.error.recoverable,
                )
            }
        }
    }

    override suspend fun rollback(node: ActionNode, traceId: UUID): NodeExecutionOutcome {
        if (node.rollbackPolicy.trim().equals("none", ignoreCase = true)) {
            return NodeExecutionOutcome.Success(emptyMap())
        }

        val rollbackNode = node.copy(
            actionType = "rollback",
            inputs = node.inputs + mapOf("operation" to "rollback"),
        )
        return execute(rollbackNode, traceId)
    }

    private fun inferCapabilityType(actionType: String): String =
        when {
            actionType.contains("communication", ignoreCase = true) -> "communication"
            actionType.contains("calendar", ignoreCase = true) -> "time"
            actionType.contains("alarm", ignoreCase = true) -> "time"
            actionType.contains("contact", ignoreCase = true) -> "communication"
            actionType.contains("media", ignoreCase = true) -> "media"
            else -> "device"
        }
}

/** Concurrent worker pool executing ready actions. */
class WorkerPool(
    private val poolSize: Int,
    private val actionExecutor: ActionExecutor,
) {
    suspend fun <T> withPermits(block: suspend (Int) -> T): T = block(poolSize.coerceAtLeast(1))
}
