package com.nova.runtime.conversation.response

import com.nova.runtime.models.Observation

/** Stub downstream reasoning/AI call — no real model integration. */
interface ConversationResponseGenerator {
    suspend fun generate(observation: Observation): String
}

class StubConversationResponseGenerator : ConversationResponseGenerator {
    override suspend fun generate(observation: Observation): String =
        "Acknowledged: ${observation.payload}"
}

/**
 * Processes observations through stub downstream stages and produces a response string.
 */
class ResponsePipeline(
    private val responseGenerator: ConversationResponseGenerator = StubConversationResponseGenerator(),
) {
    suspend fun process(observation: Observation): String {
        // Stub SUP/AIE/reasoning chain — single deterministic response for MVP.
        return responseGenerator.generate(observation)
    }

    fun streamTokens(response: String): List<String> =
        response.split(' ').mapIndexed { index, token ->
            if (index == 0) token else " $token"
        }
}
