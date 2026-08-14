package com.nova.runtime.android.capability.provider

import android.content.Context
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
import com.nova.runtime.capability.CapabilityFrameworkImpl
import com.nova.runtime.capability.events.CapabilityEventPublisher
import com.nova.runtime.capability.health.DefaultCapabilityHealthMonitor
import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import com.nova.runtime.capability.resolver.DefaultCapabilityProviderResolver
import com.nova.runtime.capability.transaction.DefaultCapabilityTransactionManager
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidCapabilityFrameworkIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)

    @Before
    fun grantTestPermissions() {
        ShadowApplication.getInstance().grantPermissions(
            android.Manifest.permission.READ_CONTACTS,
        )
    }

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

    @Test
    fun framework_resolvesAndroidProviderForWhatsAppSend() = runTest {
        val registry = DefaultCapabilityRegistry(productionCapabilityProvidersForTest(context, adapters, logger))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val framework = CapabilityFrameworkImpl(
            registry = registry,
            resolver = DefaultCapabilityProviderResolver(registry, lifecycle),
            lifecycleManager = lifecycle,
            healthMonitor = DefaultCapabilityHealthMonitor(registry, lifecycle),
            transactionManager = DefaultCapabilityTransactionManager(),
            eventPublisher = CapabilityEventPublisher(eventBus),
            logger = logger,
        )

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "communication",
                operation = CapabilityOperations.WHATSAPP_SEND_MESSAGE,
                parameters = mapOf("message" to "Hello from NOVA"),
                traceId = UUID.randomUUID(),
            ),
        )

        assertTrue(result is CapabilityResult.Success)
        val success = result as CapabilityResult.Success
        assertEquals("android-communication", success.output["providerId"])
    }

    @Test
    fun framework_prefersAndroidProviderOverStubForContactsSearch() = runTest {
        val registry = DefaultCapabilityRegistry(productionCapabilityProvidersForTest(context, adapters, logger))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val framework = CapabilityFrameworkImpl(
            registry = registry,
            resolver = DefaultCapabilityProviderResolver(registry, lifecycle),
            lifecycleManager = lifecycle,
            healthMonitor = DefaultCapabilityHealthMonitor(registry, lifecycle),
            transactionManager = DefaultCapabilityTransactionManager(),
            eventPublisher = CapabilityEventPublisher(eventBus),
            logger = logger,
        )

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "communication",
                operation = CapabilityOperations.CONTACTS_SEARCH,
                parameters = mapOf("query" to "Bob"),
                traceId = UUID.randomUUID(),
            ),
        )

        assertTrue(result is CapabilityResult.Success)
        assertEquals("android-communication", (result as CapabilityResult.Success).output["providerId"])
    }

    @Test
    fun framework_pipelineShortFormAlarm_resolvesToAndroidTimeProvider() = runTest {
        val framework = productionFramework()

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "alarm",
                operation = "create",
                parameters = mapOf(
                    "triggerAtMillis" to (System.currentTimeMillis() + 60_000L).toString(),
                    "label" to "test",
                ),
                traceId = UUID.randomUUID(),
            ),
        )

        assertTrue(
            "Expected Success for alarm.create short form, got $result",
            result is CapabilityResult.Success,
        )
        assertEquals("android-time", (result as CapabilityResult.Success).output["providerId"])
        assertEquals(CapabilityOperations.ALARM_CREATE, result.output["operation"])
    }

    @Test
    fun framework_pipelineShortFormWhatsApp_resolvesToAndroidCommunicationProvider() = runTest {
        val framework = productionFramework()

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "whatsapp",
                operation = "send_message",
                parameters = mapOf("message" to "hello"),
                traceId = UUID.randomUUID(),
            ),
        )

        assertTrue(
            "Expected Success for whatsapp.send_message short form, got $result",
            result is CapabilityResult.Success,
        )
        assertEquals("android-communication", (result as CapabilityResult.Success).output["providerId"])
    }

    @Test
    fun framework_pipelineOpenApp_resolvesToAndroidDeviceProvider() = runTest {
        val framework = productionFramework()

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "device",
                operation = "open_app",
                parameters = mapOf("appName" to "settings"),
                traceId = UUID.randomUUID(),
            ),
        )

        assertTrue(
            "Expected Success for device.open_app, got $result",
            result is CapabilityResult.Success,
        )
        assertEquals("android-device", (result as CapabilityResult.Success).output["providerId"])
    }

    private fun productionFramework(): CapabilityFrameworkImpl {
        val registry = DefaultCapabilityRegistry(productionCapabilityProvidersForTest(context, adapters, logger))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        return CapabilityFrameworkImpl(
            registry = registry,
            resolver = DefaultCapabilityProviderResolver(registry, lifecycle),
            lifecycleManager = lifecycle,
            healthMonitor = DefaultCapabilityHealthMonitor(registry, lifecycle),
            transactionManager = DefaultCapabilityTransactionManager(),
            eventPublisher = CapabilityEventPublisher(eventBus),
            logger = logger,
        )
    }
}
