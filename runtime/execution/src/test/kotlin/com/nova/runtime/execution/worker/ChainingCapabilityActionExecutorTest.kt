package com.nova.runtime.execution.worker

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ChainingCapabilityActionExecutorTest {
    private companion object {
        const val FIELD_SEP = "\u001e"
        const val URI = "content://media/external/downloads/42"
        const val SNIPPET = "FRIDAY\nLunch: Chole Bhature\nDinner: Veg Biryani"
    }

    /** Mirrors SearchResultCodec.encodeSemanticHits for a single document hit. */
    private fun encodedSemanticDocumentItem(contentSnippet: String?): String =
        listOf(
            UUID.randomUUID().toString(),
            "document",
            "0.91",
            "mess-menu.pdf",
            URI,
            contentSnippet.orEmpty(),
        ).joinToString(FIELD_SEP)

    private fun searchNode(): ActionNode =
        node(
            inputs = mapOf(
                "capabilityType" to "search.documents",
                "operation" to "search",
                "query" to "todays mess menu",
            ),
        )

    private fun whatsappNode(): ActionNode =
        node(
            inputs = mapOf(
                "capabilityType" to "whatsapp",
                "operation" to "send_message",
                "recipient" to "Atharv",
                "awaitSearchResult" to "true",
            ),
        )

    private fun node(inputs: Map<String, String>): ActionNode =
        ActionNode(
            id = UUID.randomUUID(),
            actionType = "execute_capability",
            inputs = inputs,
            outputs = emptyMap(),
            dependencies = emptyList(),
            timeoutMs = 5_000,
            retryPolicy = "default",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )

    private class RecordingCapabilityFramework(
        private val searchOutput: Map<String, String>,
    ) : CapabilityFramework {
        val requests = mutableListOf<CapabilityRequest>()

        override suspend fun execute(request: CapabilityRequest): CapabilityResult {
            requests += request
            return if (request.capabilityType.startsWith("search")) {
                CapabilityResult.Success(searchOutput)
            } else {
                CapabilityResult.Success(mapOf("status" to "sent"))
            }
        }

        override suspend fun health(capabilityType: String): Boolean = true

        override suspend fun discover(): List<String> = emptyList()
    }

    @Test
    fun chainsSearchResult_intoWhatsAppStep_withUriAndSnippetMessage() = runTest {
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf("items" to encodedSemanticDocumentItem(SNIPPET)),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        assertIs<NodeExecutionOutcome.Success>(executor.execute(searchNode(), traceId))
        assertIs<NodeExecutionOutcome.Success>(executor.execute(whatsappNode(), traceId))

        val whatsappRequest = framework.requests.last()
        assertEquals("whatsapp", whatsappRequest.capabilityType)
        assertEquals(URI, whatsappRequest.parameters["uri"])
        assertEquals("mess-menu.pdf", whatsappRequest.parameters["fileName"])
        assertEquals(SNIPPET, whatsappRequest.parameters["message"])
    }

    @Test
    fun chainsSearchResult_withoutSnippet_omitsMessage() = runTest {
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf("items" to encodedSemanticDocumentItem(contentSnippet = null)),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        executor.execute(searchNode(), traceId)
        executor.execute(whatsappNode(), traceId)

        val whatsappRequest = framework.requests.last()
        assertEquals(URI, whatsappRequest.parameters["uri"])
        assertNull(whatsappRequest.parameters["message"])
    }

    @Test
    fun doesNotOverrideExplicitMessage() = runTest {
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf("items" to encodedSemanticDocumentItem(SNIPPET)),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        executor.execute(searchNode(), traceId)
        val explicit = node(
            inputs = mapOf(
                "capabilityType" to "whatsapp",
                "operation" to "send_message",
                "awaitSearchResult" to "true",
                "message" to "custom text",
            ),
        )
        executor.execute(explicit, traceId)

        assertEquals("custom text", framework.requests.last().parameters["message"])
    }

    @Test
    fun awaitSearchResult_withNoDocument_failsClearly() = runTest {
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf(
                "items" to "",
                "query" to "this months mess menu",
            ),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        assertIs<NodeExecutionOutcome.Success>(executor.execute(searchNode(), traceId))
        val outcome = executor.execute(whatsappNode(), traceId)

        assertIs<NodeExecutionOutcome.Failure>(outcome)
        assertEquals("CHAIN_NO_DOCUMENT", outcome.error.code)
        assertTrue(
            outcome.error.userVisibleMessage.contains("mess menu", ignoreCase = true),
        )
        assertTrue(
            outcome.error.userVisibleMessage.contains("indexing", ignoreCase = true),
        )
        assertEquals(1, framework.requests.size) // WhatsApp step never dispatched
    }

    @Test
    fun chainsSearchResult_withJavaFileUri_normalizesAndShares() = runTest {
        val javaFileUri = "file:/storage/emulated/0/Download/BooklyProspectusReport.pdf"
        val encoded = listOf(
            UUID.randomUUID().toString(),
            javaFileUri,
            "BooklyProspectusReport.pdf",
            "pdf",
            "application/pdf",
            "100",
            "0.9",
            "",
        ).joinToString(FIELD_SEP)
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf("items" to encoded, "query" to "bookly prospectus report"),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        executor.execute(searchNode(), traceId)
        assertIs<NodeExecutionOutcome.Success>(executor.execute(whatsappNode(), traceId))

        assertEquals(
            "file:///storage/emulated/0/Download/BooklyProspectusReport.pdf",
            framework.requests.last().parameters["uri"],
        )
    }

    @Test
    fun chainsSearchResult_withAbsoluteFilePath_convertsToFileUri() = runTest {
        val path = "/storage/emulated/0/Download/BooklyProspectusReport.pdf"
        val encoded = listOf(
            UUID.randomUUID().toString(),
            path,
            "BooklyProspectusReport.pdf",
            "pdf",
            "application/pdf",
            "100",
            "0.9",
            "",
        ).joinToString(FIELD_SEP)
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf("items" to encoded, "query" to "bookly prospectus report"),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        executor.execute(searchNode(), traceId)
        assertIs<NodeExecutionOutcome.Success>(executor.execute(whatsappNode(), traceId))

        val whatsappRequest = framework.requests.last()
        assertEquals("file://$path", whatsappRequest.parameters["uri"])
        assertEquals("application/pdf", whatsappRequest.parameters["mimeType"])
        assertEquals("BooklyProspectusReport.pdf", whatsappRequest.parameters["fileName"])
    }

    @Test
    fun chainsSearchResult_passesRecipientNameForWhatsAppDeepLink() = runTest {
        val framework = RecordingCapabilityFramework(
            searchOutput = mapOf("items" to encodedSemanticDocumentItem(SNIPPET)),
        )
        val executor = ChainingCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()

        executor.execute(searchNode(), traceId)
        executor.execute(whatsappNode(), traceId)

        val whatsappRequest = framework.requests.last()
        assertEquals("Atharv", whatsappRequest.parameters["recipient"])
        assertEquals(URI, whatsappRequest.parameters["uri"])
        assertEquals(SNIPPET, whatsappRequest.parameters["message"])
    }
}
