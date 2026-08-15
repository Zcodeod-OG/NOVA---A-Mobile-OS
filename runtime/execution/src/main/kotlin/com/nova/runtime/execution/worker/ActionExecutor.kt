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

        if (node.actionType !in CAPABILITY_ACTION_TYPES) {
            return NodeExecutionOutcome.Success(
                outputs = mapOf(
                    "taskKey" to (node.outputs["taskKey"] ?: node.actionType),
                    "status" to "skipped",
                ),
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
                if (isStubOnlyOutput(result.output)) {
                    return NodeExecutionOutcome.Failure(
                        error = RuntimeError(
                            code = "CAPABILITY_STUB_ONLY",
                            category = ErrorCategory.INFRASTRUCTURE,
                            severity = ErrorSeverity.HIGH,
                            recoverable = false,
                            userVisibleMessage = stubOnlyUserMessage(capabilityType, operation),
                            diagnostics = mapOf(
                                "capabilityType" to capabilityType,
                                "operation" to operation,
                                "providerId" to (result.output["providerId"] ?: "unknown"),
                            ),
                        ),
                        retryable = false,
                    )
                }
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

    companion object {
        private val CAPABILITY_ACTION_TYPES = setOf("execute_capability")

        private fun isStubOnlyOutput(output: Map<String, String>): Boolean =
            output["status"] == "stub_executed" ||
                output["stub"] == "true"

        private fun stubOnlyUserMessage(capabilityType: String, operation: String): String =
            when {
                capabilityType.startsWith("search") ->
                    "Document search isn't wired on this device — check indexing and All files access"
                capabilityType == "whatsapp" || operation.contains("whatsapp") ->
                    "WhatsApp send isn't available — grant Contacts and enable NOVA in Accessibility"
                capabilityType == "alarm" || (operation == "create" && capabilityType == "time") ->
                    "Alarm couldn't be set — grant exact alarm permission in Clock settings"
                capabilityType == "device" ->
                    "Couldn't open the app on this device"
                else ->
                    "Action unavailable on device ($capabilityType.$operation)"
            }
    }
}

/** Concurrent worker pool executing ready actions. */
class WorkerPool(
    private val poolSize: Int,
    private val actionExecutor: ActionExecutor,
) {
    suspend fun <T> withPermits(block: suspend (Int) -> T): T = block(poolSize.coerceAtLeast(1))
}
