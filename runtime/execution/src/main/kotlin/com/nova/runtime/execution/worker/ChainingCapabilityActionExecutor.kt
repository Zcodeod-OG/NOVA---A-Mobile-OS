package com.nova.runtime.execution.worker

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes capabilities and chains search results into downstream share/WhatsApp steps.
 */
class ChainingCapabilityActionExecutor(
    private val capabilityFramework: CapabilityFramework,
) : ActionExecutor {
    private val delegate = StubCapabilityActionExecutor(capabilityFramework)
    private val searchOutputsByTrace = ConcurrentHashMap<UUID, Map<String, String>>()

    override suspend fun execute(node: ActionNode, traceId: UUID): NodeExecutionOutcome {
        if (node.actionType != "execute_capability") {
            return delegate.execute(node, traceId)
        }

        val enrichedNode = node.copy(inputs = enrichInputs(node.inputs, traceId))
        val outcome = delegate.execute(enrichedNode, traceId)

        if (outcome is NodeExecutionOutcome.Success && isSearchStep(node.inputs)) {
            searchOutputsByTrace[traceId] = outcome.outputs
        }

        if (node.inputs["awaitSearchResult"] == "true") {
            searchOutputsByTrace.remove(traceId)
        }

        return outcome
    }

    override suspend fun rollback(node: ActionNode, traceId: UUID): NodeExecutionOutcome =
        delegate.rollback(node, traceId)

    private fun enrichInputs(inputs: Map<String, String>, traceId: UUID): Map<String, String> {
        if (inputs["awaitSearchResult"] != "true") return inputs
        if (!inputs["uri"].isNullOrBlank()) return inputs

        val searchOutput = searchOutputsByTrace[traceId] ?: return inputs
        val document = decodeFirstShareableDocument(searchOutput["items"].orEmpty()) ?: return inputs

        return inputs + buildMap {
            put("uri", document.uri)
            put("mimeType", document.mimeType)
            document.name?.let { put("fileName", it) }
        }
    }

    private fun isSearchStep(inputs: Map<String, String>): Boolean {
        val operation = inputs["operation"].orEmpty()
        val capabilityOperation = inputs["capabilityOperation"].orEmpty()
        return operation == "search" ||
            capabilityOperation.startsWith("search.") ||
            inputs["capabilityType"]?.startsWith("search.") == true
    }

    private data class ShareableDocumentHit(
        val uri: String,
        val mimeType: String,
        val name: String? = null,
    )

    private fun decodeFirstShareableDocument(itemsEncoded: String): ShareableDocumentHit? {
        val firstItem = itemsEncoded.split(ITEM_SEP).firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val fields = firstItem.split(FIELD_SEP)
        return when {
            fields.size >= 7 && fields[1].contains("://") ->
                ShareableDocumentHit(
                    uri = fields[1],
                    mimeType = fields[4].ifBlank { "*/*" },
                    name = fields[2].ifBlank { null },
                )
            fields.size >= 5 && fields[1].equals("document", ignoreCase = true) && fields[4].contains("://") ->
                ShareableDocumentHit(
                    uri = fields[4],
                    mimeType = "*/*",
                    name = fields[3].ifBlank { null },
                )
            else -> null
        }
    }

    private companion object {
        private const val FIELD_SEP = "\u001e"
        private const val ITEM_SEP = "|"
    }
}
