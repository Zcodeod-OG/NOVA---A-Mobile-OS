package com.nova.runtime.android.capability.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.android.AndroidAdapterLayerImpl
import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapter
import com.nova.runtime.android.accessibilityAdapter.AccessibilityOperations
import com.nova.runtime.android.alarmAdapter.AlarmAdapterStub
import com.nova.runtime.android.calendarAdapter.CalendarAdapterStub
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.contactsAdapter.ContactsAdapter
import com.nova.runtime.android.contactsAdapter.ContactsOperations
import com.nova.runtime.android.intentAdapter.IntentAdapter
import com.nova.runtime.android.intentAdapter.IntentOperations
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapterStub
import com.nova.runtime.android.notificationAdapter.NotificationAdapterStub
import com.nova.runtime.android.ocrAdapter.OcrAdapterStub
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapterStub
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidCommunicationProviderWhatsAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logger = StructuredLogger()
    private val traceId = UUID.randomUUID()

    @Test
    fun whatsAppSend_withPhone_opensWaMeDeepLinkNotShare() = runTest {
        val intents = RecordingIntentAdapter()
        val provider = provider(intents = intents)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "message" to "hello",
                    "phoneNumber" to "9876543210",
                    "recipient" to "Atharv",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("919876543210", success.output["phoneNumber"])
        assertEquals("chat_opened", success.output["status"])
        assertTrue(success.output["userMessage"]!!.contains("Accessibility"))
        assertTrue(success.output["userMessage"]!!.contains("auto-send"))
        assertEquals(IntentOperations.OPEN_URL, intents.calls[0].operation)
        assertEquals("com.whatsapp", intents.calls[0].parameters["packageName"])
        assertTrue(intents.calls[0].parameters["url"]!!.startsWith("https://wa.me/919876543210?text="))
        assertFalse(intents.calls.any { it.operation == IntentOperations.SHARE })
        // Accessibility is off in unit tests — prompt settings once after opening chat.
        assertTrue(
            intents.calls.any {
                it.operation == IntentOperations.LAUNCH_SETTINGS &&
                    it.parameters["settingsAction"] ==
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            },
        )
    }

    @Test
    fun whatsAppSend_resolvesContactCaseInsensitive_andUsesDeepLink() = runTest {
        val intents = RecordingIntentAdapter()
        val contacts = FakeContactsAdapter(
            searchHits = mapOf(
                "atharv" to listOf("42:Atharv"),
                "Atharv" to listOf("42:Atharv"),
            ),
            phones = mapOf(42L to "+91 98765 43210"),
        )
        ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider = provider(intents = intents, contacts = contacts)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "message" to "hello",
                    "recipient" to "atharv",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("919876543210", success.output["phoneNumber"])
        assertEquals(IntentOperations.OPEN_URL, intents.calls.first().operation)
        assertTrue(intents.calls.first().parameters["url"]!!.contains("wa.me/919876543210"))
    }

    @Test
    fun whatsAppSend_withoutPhone_fallsBackToShareWithHint() = runTest {
        val intents = RecordingIntentAdapter()
        ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val contacts = FakeContactsAdapter(searchHits = emptyMap(), phones = emptyMap())
        val provider = provider(intents = intents, contacts = contacts)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "message" to "hello",
                    "recipient" to "Atharv",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("share_fallback", success.output["status"])
        assertTrue(success.output["userMessage"]!!.contains("Couldn't find Atharv's number"))
        assertEquals(IntentOperations.SHARE, intents.calls.single().operation)
        assertEquals("hello", intents.calls.single().parameters["text"])
        assertEquals("com.whatsapp", intents.calls.single().parameters["packageName"])
    }

    @Test
    fun whatsAppSend_waMeFails_fallsBackToApiWhatsAppUrl() = runTest {
        val intents = RecordingIntentAdapter(
            failIf = { op, params ->
                op == IntentOperations.OPEN_URL &&
                    params["url"].orEmpty().startsWith("https://wa.me/")
            },
        )
        val provider = provider(intents = intents)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "message" to "hi",
                    "phoneNumber" to "919876543210",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val openCalls = intents.calls.filter { it.operation == IntentOperations.OPEN_URL }
        assertTrue(openCalls.any { it.parameters["url"]!!.startsWith("https://api.whatsapp.com/send") })
        assertTrue(
            (response as CapabilityExecutionResponse.Success).output["url"]!!
                .startsWith("https://api.whatsapp.com/send"),
        )
    }

    @Test
    fun whatsAppSend_withUri_usesActionSendPackageNotQueryAsText() = runTest {
        val intents = RecordingIntentAdapter()
        ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val contacts = FakeContactsAdapter(searchHits = emptyMap(), phones = emptyMap())
        val provider = provider(intents = intents, contacts = contacts)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "uri" to "content://media/external/downloads/42",
                    "mimeType" to "application/pdf",
                    "recipient" to "Atharv Sharma",
                    // Pipeline used to leak the full command into query — must be ignored.
                    "query" to "send bookly prospectus report to atharv sharma",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("shared_document", success.output["status"])
        assertEquals("false", success.output["autoSent"])
        assertTrue(success.output["userMessage"]!!.contains("Accessibility"))
        val share = intents.calls.first { it.operation == IntentOperations.SHARE }
        assertEquals("content://media/external/downloads/42", share.parameters["uri"])
        assertEquals("com.whatsapp", share.parameters["packageName"])
        assertFalse(share.parameters.containsKey("text"))
        assertFalse(share.parameters.containsKey("jid"))
        assertFalse(intents.calls.any { it.operation == IntentOperations.OPEN_URL })
        // Without phone/jid, accessibility settings are prompted so auto-send can complete later.
        assertTrue(
            intents.calls.any {
                it.operation == IntentOperations.LAUNCH_SETTINGS &&
                    it.parameters["settingsAction"] ==
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            },
        )
    }

    @Test
    fun whatsAppSend_withUriAndPhone_usesJidTargetedShareAndAutoSends() = runTest {
        val intents = RecordingIntentAdapter()
        val accessibility = RecordingAccessibilityAdapter(succeedOnViewId = "com.whatsapp:id/send")
        val provider = provider(intents = intents, accessibility = accessibility)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "uri" to "content://media/external/downloads/42",
                    "mimeType" to "application/pdf",
                    "phoneNumber" to "9876543210",
                    "recipient" to "Atharv",
                    "query" to "send bookly prospectus to atharv on whatsapp",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("true", success.output["autoSent"])
        assertEquals("sent", success.output["status"])
        assertEquals("Auto-sent document to Atharv", success.output["userMessage"])
        assertEquals("919876543210@s.whatsapp.net", success.output["jid"])
        val share = intents.calls.first { it.operation == IntentOperations.SHARE }
        assertEquals("content://media/external/downloads/42", share.parameters["uri"])
        assertEquals("com.whatsapp", share.parameters["packageName"])
        assertEquals("919876543210@s.whatsapp.net", share.parameters["jid"])
        assertFalse(share.parameters.containsKey("text"))
        assertFalse(intents.calls.any { it.operation == IntentOperations.OPEN_URL })
        assertFalse(intents.calls.any { it.operation == IntentOperations.LAUNCH_SETTINGS })
        assertTrue(accessibility.calls.any { it.operation == AccessibilityOperations.CLICK })
    }

    @Test
    fun whatsAppSend_withUriAndPhone_withoutAccessibility_opensDocumentReady() = runTest {
        val intents = RecordingIntentAdapter()
        val provider = provider(intents = intents)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "uri" to "content://media/external/downloads/99",
                    "mimeType" to "application/pdf",
                    "phoneNumber" to "919876543210",
                    "recipient" to "Atharv",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("document_ready", success.output["status"])
        assertEquals("false", success.output["autoSent"])
        assertTrue(success.output["userMessage"]!!.contains("document is ready in chat"))
        assertEquals(
            "919876543210@s.whatsapp.net",
            intents.calls.first { it.operation == IntentOperations.SHARE }.parameters["jid"],
        )
        assertTrue(
            intents.calls.any {
                it.operation == IntentOperations.LAUNCH_SETTINGS &&
                    it.parameters["settingsAction"] ==
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            },
        )
    }

    @Test
    fun whatsAppSend_documentPicker_selectsContactThenSendsViaAccessibility() = runTest {
        val intents = RecordingIntentAdapter()
        ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val contacts = FakeContactsAdapter(searchHits = emptyMap(), phones = emptyMap())
        val accessibility = RecordingAccessibilityAdapter(
            succeedOnText = "Atharv Sharma",
            succeedOnViewId = "com.whatsapp:id/send",
        )
        val provider = provider(intents = intents, contacts = contacts, accessibility = accessibility)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "uri" to "content://media/external/downloads/42",
                    "mimeType" to "application/pdf",
                    "recipient" to "Atharv Sharma",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("sent", success.output["status"])
        assertEquals("true", success.output["autoSent"])
        assertTrue(
            accessibility.calls.any {
                it.operation == AccessibilityOperations.CLICK &&
                    (it.parameters["text"] == "Atharv Sharma" ||
                        it.parameters["contentDescription"] == "Atharv Sharma")
            },
        )
        assertTrue(
            accessibility.calls.any {
                it.operation == AccessibilityOperations.CLICK &&
                    it.parameters["viewId"] == "com.whatsapp:id/send"
            },
        )
        assertFalse(
            intents.calls.any { it.operation == IntentOperations.SHARE && it.parameters.containsKey("jid") },
        )
    }

    @Test
    fun whatsAppSend_autoSendsWhenAccessibilityClicksSend() = runTest {
        val intents = RecordingIntentAdapter()
        val accessibility = RecordingAccessibilityAdapter(succeedOnViewId = "com.whatsapp:id/send")
        val provider = provider(intents = intents, accessibility = accessibility)

        val response = provider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf(
                    "message" to "hello",
                    "phoneNumber" to "9876543210",
                    "recipient" to "Atharv",
                ),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("true", success.output["autoSent"])
        assertEquals("sent", success.output["status"])
        assertEquals("Auto-sent to Atharv", success.output["userMessage"])
        assertTrue(accessibility.calls.any { it.operation == AccessibilityOperations.CLICK })
        assertFalse(intents.calls.any { it.operation == IntentOperations.LAUNCH_SETTINGS })
    }

    private fun provider(
        intents: IntentAdapter = RecordingIntentAdapter(),
        contacts: ContactsAdapter = FakeContactsAdapter(),
        accessibility: AccessibilityAdapter = RecordingAccessibilityAdapter(),
    ): AndroidCommunicationProvider =
        AndroidCommunicationProvider(
            context,
            logger,
            AndroidAdapterLayerImpl(
                intents = intents,
                contacts = contacts,
                calendar = CalendarAdapterStub(),
                alarms = AlarmAdapterStub(),
                mediaStore = MediaStoreAdapterStub(),
                notifications = NotificationAdapterStub(),
                accessibility = accessibility,
                storageAccess = StorageAccessAdapterStub(),
                ocr = OcrAdapterStub(),
            ),
        )

    private data class AdapterCall(
        val operation: String,
        val parameters: Map<String, String>,
    )

    private class RecordingIntentAdapter(
        private val failIf: (String, Map<String, String>) -> Boolean = { _, _ -> false },
    ) : IntentAdapter {
        val calls = mutableListOf<AdapterCall>()

        override suspend fun execute(
            operation: String,
            parameters: Map<String, String>,
            traceId: UUID,
        ): CapabilityResult {
            calls += AdapterCall(operation, parameters)
            if (failIf(operation, parameters)) {
                return CapabilityResult.Failure(
                    com.nova.runtime.models.RuntimeError(
                        code = "ANDROID_PLATFORM_FAILURE",
                        category = com.nova.runtime.models.ErrorCategory.EXECUTION,
                        severity = com.nova.runtime.models.ErrorSeverity.MEDIUM,
                        recoverable = true,
                        userVisibleMessage = "simulated open failure",
                    ),
                )
            }
            return CapabilityResult.Success(
                buildMap {
                    put("status", if (operation == IntentOperations.SHARE) "shared" else "opened")
                    parameters["url"]?.let { put("url", it) }
                    parameters["packageName"]?.let { put("packageName", it) }
                },
            )
        }

        override fun supportedOperations(): Set<String> =
            setOf(
                IntentOperations.OPEN_APP,
                IntentOperations.SHARE,
                IntentOperations.VIEW_DOCUMENT,
                IntentOperations.DIAL,
                IntentOperations.LAUNCH_SETTINGS,
                IntentOperations.OPEN_URL,
            )
    }

    private class FakeContactsAdapter(
        private val searchHits: Map<String, List<String>> = emptyMap(),
        private val phones: Map<Long, String> = emptyMap(),
    ) : ContactsAdapter {
        override suspend fun execute(
            operation: String,
            parameters: Map<String, String>,
            traceId: UUID,
        ): CapabilityResult =
            when (operation) {
                ContactsOperations.SEARCH -> {
                    val query = parameters["query"].orEmpty()
                    val hits = searchHits.entries
                        .firstOrNull { it.key.equals(query, ignoreCase = true) }
                        ?.value
                        .orEmpty()
                    CapabilityResult.Success(
                        mapOf(
                            "count" to hits.size.toString(),
                            "contacts" to hits.joinToString("|"),
                        ),
                    )
                }
                ContactsOperations.RETRIEVE -> {
                    val id = parameters["contactId"]?.toLongOrNull()
                    val phone = id?.let { phones[it] }.orEmpty()
                    CapabilityResult.Success(
                        mapOf(
                            "contactId" to (id?.toString() ?: ""),
                            "displayName" to "Atharv",
                            "phoneNumber" to phone,
                        ),
                    )
                }
                else -> CapabilityResult.Success(mapOf("stub" to "true"))
            }

        override fun supportedOperations(): Set<String> =
            setOf(
                ContactsOperations.SEARCH,
                ContactsOperations.RETRIEVE,
                ContactsOperations.CREATE,
                ContactsOperations.UPDATE,
            )

        override fun requiredPermissions(): Set<String> =
            setOf(android.Manifest.permission.READ_CONTACTS)
    }

    private class RecordingAccessibilityAdapter(
        private val succeedOnViewId: String? = null,
        private val succeedOnText: String? = null,
        private val unavailable: Boolean = succeedOnViewId == null && succeedOnText == null,
    ) : AccessibilityAdapter {
        val calls = mutableListOf<AdapterCall>()

        override suspend fun execute(
            operation: String,
            parameters: Map<String, String>,
            traceId: UUID,
        ): CapabilityResult {
            calls += AdapterCall(operation, parameters)
            if (unavailable) {
                return CapabilityResult.Failure(
                    com.nova.runtime.models.RuntimeError(
                        code = "ANDROID_ACCESSIBILITY_UNAVAILABLE",
                        category = com.nova.runtime.models.ErrorCategory.PERMISSION,
                        severity = com.nova.runtime.models.ErrorSeverity.HIGH,
                        recoverable = true,
                        userVisibleMessage = "Accessibility service is not enabled",
                    ),
                )
            }
            if (operation == AccessibilityOperations.CLICK) {
                if (succeedOnViewId != null && parameters["viewId"] == succeedOnViewId) {
                    return CapabilityResult.Success(mapOf("status" to "clicked"))
                }
                val label = parameters["text"] ?: parameters["contentDescription"]
                if (succeedOnText != null && label == succeedOnText) {
                    return CapabilityResult.Success(mapOf("status" to "clicked"))
                }
            }
            return CapabilityResult.Failure(
                com.nova.runtime.models.RuntimeError(
                    code = "ANDROID_INVALID_PARAMETERS",
                    category = com.nova.runtime.models.ErrorCategory.VALIDATION,
                    severity = com.nova.runtime.models.ErrorSeverity.LOW,
                    recoverable = false,
                    userVisibleMessage = "Node not found for click",
                ),
            )
        }

        override fun supportedOperations(): Set<String> =
            setOf(
                AccessibilityOperations.CLICK,
                AccessibilityOperations.INPUT_TEXT,
                AccessibilityOperations.SCROLL,
                AccessibilityOperations.TRAVERSE,
                AccessibilityOperations.GET_ACTIVE_WINDOW,
            )
    }
}
