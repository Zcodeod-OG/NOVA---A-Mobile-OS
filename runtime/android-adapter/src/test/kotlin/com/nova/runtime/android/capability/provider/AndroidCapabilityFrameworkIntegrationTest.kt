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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidCapabilityFrameworkIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext()
    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
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
        val registry = DefaultCapabilityRegistry(productionCapabilityProviders(context, adapters, logger))
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
        val registry = DefaultCapabilityRegistry(productionCapabilityProviders(context, adapters, logger))
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
}
