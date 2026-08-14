package com.nova.runtime.app.conversation

import com.nova.runtime.conversation.response.ConversationResponseGenerator
import com.nova.runtime.models.Observation
import com.nova.runtime.orchestrator.CognitivePipelineOrchestrator
import com.nova.runtime.orchestrator.PipelineResult

/** Routes conversation observations through the full cognitive pipeline. */
class OrchestratorConversationResponseGenerator(
    private val orchestrator: CognitivePipelineOrchestrator,
) : ConversationResponseGenerator {
    override suspend fun generate(observation: Observation): String {
        val result = orchestrator.processUserCommand(
            text = observation.payload,
            traceId = observation.traceId.toString(),
        )
        return when (result) {
            is PipelineResult.Success -> result.summary
            is PipelineResult.PendingConfirmation -> result.confirmationPrompt
            is PipelineResult.Failure -> "Unable to complete: ${result.summary}"
        }
    }
}
