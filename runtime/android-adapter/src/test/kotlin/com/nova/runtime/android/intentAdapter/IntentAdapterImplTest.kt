package com.nova.runtime.android.intentAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nova.runtime.android.AndroidAdapterRobolectricTest

class IntentAdapterImplTest : AndroidAdapterRobolectricTest() {
    private val adapter = IntentAdapterImpl(context, StructuredLogger())
    private val traceId = UUID.randomUUID()

    @Test
    fun supportedOperations_matchesAisContract() {
        assertEquals(
            setOf(
                IntentOperations.OPEN_APP,
                IntentOperations.SHARE,
                IntentOperations.VIEW_DOCUMENT,
                IntentOperations.DIAL,
                IntentOperations.LAUNCH_SETTINGS,
                IntentOperations.OPEN_URL,
            ),
            adapter.supportedOperations(),
        )
    }

    @Test
    fun execute_unknownOperation_returnsValidationFailure() = runTest {
        val result = adapter.execute("unknown", emptyMap(), traceId)
        assertTrue(result is CapabilityResult.Failure)
        val failure = result as CapabilityResult.Failure
        assertEquals("ANDROID_INVALID_OPERATION", failure.error.code)
    }

    @Test
    fun execute_launchSettings_succeeds() = runTest {
        val result = adapter.execute(IntentOperations.LAUNCH_SETTINGS, emptyMap(), traceId)
        assertTrue(result is CapabilityResult.Success)
    }

    @Test
    fun execute_share_withPackageName_succeeds() = runTest {
        val result = adapter.execute(
            IntentOperations.SHARE,
            mapOf("text" to "hello", "packageName" to "com.example.app"),
            traceId,
        )
        assertTrue(result is CapabilityResult.Success)
        val success = result as CapabilityResult.Success
        assertEquals("shared", success.output["status"])
        assertEquals("com.example.app", success.output["packageName"])
    }

    @Test
    fun execute_share_withJid_returnsJidInOutput() = runTest {
        val result = adapter.execute(
            IntentOperations.SHARE,
            mapOf(
                "uri" to "content://media/external/downloads/42",
                "mimeType" to "application/pdf",
                "packageName" to "com.whatsapp",
                "jid" to "919876543210@s.whatsapp.net",
            ),
            traceId,
        )
        assertTrue(result is CapabilityResult.Success)
        val success = result as CapabilityResult.Success
        assertEquals("shared", success.output["status"])
        assertEquals("com.whatsapp", success.output["packageName"])
        assertEquals("919876543210@s.whatsapp.net", success.output["jid"])
    }

    @Test
    fun execute_dial_withoutPhoneNumber_returnsValidationFailure() = runTest {
        val result = adapter.execute(IntentOperations.DIAL, emptyMap(), traceId)
        assertTrue(result is CapabilityResult.Failure)
        val failure = result as CapabilityResult.Failure
        assertEquals("ANDROID_INVALID_PARAMETERS", failure.error.code)
    }
}
