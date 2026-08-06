package com.nova.runtime.android.capability.provider

import com.nova.runtime.android.AndroidAdapterLayerImpl
import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapterStub
import com.nova.runtime.android.alarmAdapter.AlarmAdapterStub
import com.nova.runtime.android.calendarAdapter.CalendarAdapterStub
import com.nova.runtime.android.contactsAdapter.ContactsAdapterStub
import com.nova.runtime.android.intentAdapter.IntentAdapterStub
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapterStub
import com.nova.runtime.android.notificationAdapter.NotificationAdapterStub
import com.nova.runtime.android.ocrAdapter.OcrAdapterStub
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapterStub
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidCapabilityProviderTest {
    private val context = ApplicationProvider.getApplicationContext()
    private val logger = StructuredLogger()
    private val traceId = UUID.randomUUID()
    private val adapters =
        AndroidAdapterLayerImpl(
            intents = IntentAdapterStub(),
            contacts = ContactsAdapterStub(),
            calendar = CalendarAdapterStub(),
            alarms = AlarmAdapterStub(),
            mediaStore = MediaStoreAdapterStub(),
            notifications = NotificationAdapterStub(),
            accessibility = AccessibilityAdapterStub(),
            storageAccess = StorageAccessAdapterStub(),
            ocr = OcrAdapterStub(),
        )

    private val communicationProvider = AndroidCommunicationProvider(context, logger, adapters)
    private val timeProvider = AndroidTimeProvider(context, logger, adapters)
    private val mediaProvider = AndroidMediaProvider(context, logger, adapters)

    @Test
    fun communicationProvider_supportsWhatsAppAndContactsOperations() {
        assertEquals(
            setOf(
                CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                CapabilityOperations.CONTACTS_SEARCH,
            ),
            communicationProvider.supportedOperations(),
        )
    }

    @Test
    fun timeProvider_supportsAlarmAndCalendarOperations() {
        assertEquals(
            setOf(
                CapabilityOperations.ALARM_CREATE,
                CapabilityOperations.CALENDAR_READ,
                CapabilityOperations.CALENDAR_CREATE,
            ),
            timeProvider.supportedOperations(),
        )
    }

    @Test
    fun mediaProvider_supportsShareFileOperation() {
        assertEquals(setOf(CapabilityOperations.SHARE_FILE), mediaProvider.supportedOperations())
    }

    @Test
    fun whatsAppSend_withoutMessageOrPhone_isInvalid() = runTest {
        val result = communicationProvider.validate(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )
        assertTrue(result is CapabilityValidationResult.Invalid)
    }

    @Test
    fun whatsAppSend_withMessage_executesSuccessfully() = runTest {
        val response = communicationProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf("message" to "Hello"),
                traceId = traceId,
            ),
        )
        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("android-communication", success.output["providerId"])
        assertEquals(CapabilityOperations.WHATSAPP_SEND_MESSAGE, success.output["operation"])
    }

    @Test
    fun contactsSearch_executesSuccessfully() = runTest {
        val response = communicationProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.CONTACTS_SEARCH,
                parameters = mapOf("query" to "Alice"),
                traceId = traceId,
            ),
        )
        assertTrue(response is CapabilityExecutionResponse.Success)
    }

    @Test
    fun alarmCreate_withoutTrigger_isInvalid() = runTest {
        val result = timeProvider.validate(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.ALARM_CREATE,
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )
        assertTrue(result is CapabilityValidationResult.Invalid)
    }

    @Test
    fun alarmCreate_withTrigger_executesSuccessfully() = runTest {
        val response = timeProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.ALARM_CREATE,
                parameters = mapOf(
                    "requestCode" to "42",
                    "triggerAtMillis" to "1893456000000",
                ),
                traceId = traceId,
            ),
        )
        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("42", success.output["requestCode"])
    }

    @Test
    fun calendarCreate_withoutRequiredFields_isInvalid() = runTest {
        val result = timeProvider.validate(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.CALENDAR_CREATE,
                parameters = mapOf("title" to "Meeting"),
                traceId = traceId,
            ),
        )
        assertTrue(result is CapabilityValidationResult.Invalid)
    }

    @Test
    fun calendarRead_executesSuccessfully() = runTest {
        val response = timeProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.CALENDAR_READ,
                parameters = mapOf("startTime" to "0", "endTime" to "999999999999"),
                traceId = traceId,
            ),
        )
        assertTrue(response is CapabilityExecutionResponse.Success)
    }

    @Test
    fun shareFile_withoutUri_isInvalid() = runTest {
        val result = mediaProvider.validate(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.SHARE_FILE,
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )
        assertTrue(result is CapabilityValidationResult.Invalid)
    }

    @Test
    fun shareFile_withUri_executesSuccessfully() = runTest {
        val response = mediaProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.SHARE_FILE,
                parameters = mapOf(
                    "uri" to "content://test/file.pdf",
                    "mimeType" to "application/pdf",
                ),
                traceId = traceId,
            ),
        )
        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("shared", success.output["status"])
    }

    @Test
    fun productionCapabilityProviders_includesAndroidAndStubProviders() {
        val providers = productionCapabilityProviders(context, adapters, logger)
        val providerIds = providers.map { it.providerId }.toSet()
        assertTrue("android-communication" in providerIds)
        assertTrue("android-time" in providerIds)
        assertTrue("android-media" in providerIds)
        assertTrue("stub-knowledge" in providerIds)
    }
}
