package com.nova.runtime.execution.worker

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
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

        val enrichment = enrichInputs(node.inputs, traceId)
        if (enrichment.missingDocument) {
            val searchQuery = searchOutputsByTrace[traceId]?.get("query")
                ?: node.inputs["query"].orEmpty()
            searchOutputsByTrace.remove(traceId)
            return NodeExecutionOutcome.Failure(
                error = RuntimeError(
                    code = "CHAIN_NO_DOCUMENT",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.LOW,
                    recoverable = false,
                    userVisibleMessage = missingDocumentMessage(searchQuery),
                ),
                retryable = false,
            )
        }

        val enrichedNode = node.copy(inputs = enrichment.inputs)
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

    private data class EnrichmentResult(
        val inputs: Map<String, String>,
        val missingDocument: Boolean = false,
    )

    private fun enrichInputs(inputs: Map<String, String>, traceId: UUID): EnrichmentResult {
        if (inputs["awaitSearchResult"] != "true") return EnrichmentResult(inputs)
        if (!inputs["uri"].isNullOrBlank()) return EnrichmentResult(inputs)

        val searchOutput = searchOutputsByTrace[traceId]
            ?: return EnrichmentResult(inputs, missingDocument = true)
        val document = decodeFirstShareableDocument(searchOutput["items"].orEmpty())
            ?: return EnrichmentResult(inputs, missingDocument = true)

        return EnrichmentResult(
            inputs = inputs + buildMap {
                put("uri", document.uri)
                put("mimeType", document.mimeType)
                document.name?.let { put("fileName", it) }
                // Extracted document text (e.g. today's menu section) accompanies the shared file.
                if (inputs["message"].isNullOrBlank() && !document.contentSnippet.isNullOrBlank()) {
                    put("message", document.contentSnippet)
                }
            },
        )
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
        val contentSnippet: String? = null,
    )

    private fun decodeFirstShareableDocument(itemsEncoded: String): ShareableDocumentHit? {
        val firstItem = itemsEncoded.split(ITEM_SEP).firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val fields = firstItem.split(FIELD_SEP)
        return when {
            // encodeDocumentHits: id, path, name, extension, mimeType, modifiedAt, score, snippet
            fields.size >= 7 && looksLikeShareableUriOrPath(fields[1]) ->
                ShareableDocumentHit(
                    uri = toShareableUri(fields[1]),
                    mimeType = fields[4].ifBlank { "*/*" },
                    name = fields[2].ifBlank { null },
                    contentSnippet = fields.getOrNull(7)?.takeIf { it.isNotBlank() },
                )
            // encodeSemanticHits document: objectId, "document", score, title, snippetOrUri, contentSnippet
            fields.size >= 5 && fields[1].equals("document", ignoreCase = true) &&
                looksLikeShareableUriOrPath(fields[4]) ->
                ShareableDocumentHit(
                    uri = toShareableUri(fields[4]),
                    mimeType = "*/*",
                    name = fields[3].ifBlank { null },
                    contentSnippet = fields.getOrNull(5)?.takeIf { it.isNotBlank() },
                )
            else -> null
        }
    }

    private fun looksLikeShareableUriOrPath(raw: String): Boolean {
        if (raw.isBlank()) return false
        // content://…, file://…, and Java File.toURI() form file:/absolute/path
        if (raw.contains("://") || raw.startsWith("file:/")) return true
        return raw.startsWith("/") && raw.length > 1
    }

    private fun toShareableUri(raw: String): String =
        when {
            raw.contains("://") -> raw
            // Normalize Java File.toURI() ("file:/path") for FileProvider conversion.
            raw.startsWith("file:/") && !raw.startsWith("file://") ->
                "file://" + raw.removePrefix("file:")
            raw.startsWith("/") -> "file://$raw"
            else -> raw
        }

    private fun missingDocumentMessage(query: String): String {
        val lower = query.lowercase()
        val label = query.trim().ifBlank { "matching document" }
        return when {
            "menu" in lower || "mess" in lower ->
                "Couldn't find a mess menu document — make sure it's in Downloads/Files and wait for indexing"
            else ->
                "Couldn't find \"$label\" to share — make sure the file is in Downloads/Files and wait for indexing"
        }
    }

    private companion object {
        private const val FIELD_SEP = "\u001e"
        private const val ITEM_SEP = "|"
    }
}
