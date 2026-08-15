package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayerImpl
import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapterStub
import com.nova.runtime.android.alarmAdapter.AlarmAdapterStub
import com.nova.runtime.android.calendarAdapter.CalendarAdapterStub
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.contactsAdapter.ContactsAdapterStub
import com.nova.runtime.android.intentAdapter.IntentAdapter
import com.nova.runtime.android.intentAdapter.IntentOperations
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapterStub
import com.nova.runtime.android.notificationAdapter.NotificationAdapterStub
import com.nova.runtime.android.ocrAdapter.OcrAdapterStub
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapterStub
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidDeviceProviderTest {

    private class RecordingIntentAdapter : IntentAdapter {
        var lastOperation: String? = null
        var lastParameters: Map<String, String> = emptyMap()

        override suspend fun execute(
            operation: String,
            parameters: Map<String, String>,
            traceId: UUID,
        ): CapabilityResult {
            lastOperation = operation
            lastParameters = parameters
            return CapabilityResult.Success(mapOf("status" to "ok"))
        }

        override fun supportedOperations(): Set<String> = IntentOperations.run {
            setOf(OPEN_APP, SHARE, VIEW_DOCUMENT, DIAL, LAUNCH_SETTINGS, OPEN_URL)
        }
    }

    private class FakeCatalog(private val apps: List<InstalledApp>) : InstalledAppCatalog {
        override fun installedApps(): List<InstalledApp> = apps
        override fun isInstalled(packageName: String): Boolean =
            apps.any { it.packageName == packageName }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logger = StructuredLogger()
    private val intents = RecordingIntentAdapter()
    private val traceId: UUID = UUID.randomUUID()

    private fun provider(vararg apps: InstalledApp): AndroidDeviceProvider =
        AndroidDeviceProvider(
            context = context,
            logger = logger,
            adapters = AndroidAdapterLayerImpl(
                intents = intents,
                contacts = ContactsAdapterStub(),
                calendar = CalendarAdapterStub(),
                alarms = AlarmAdapterStub(),
                mediaStore = MediaStoreAdapterStub(),
                notifications = NotificationAdapterStub(),
                accessibility = AccessibilityAdapterStub(),
                storageAccess = StorageAccessAdapterStub(),
                ocr = OcrAdapterStub(),
            ),
            appCatalog = FakeCatalog(apps.toList()),
        )

    @Test
    fun openApp_resolvesNameAndLaunches() = runTest {
        val deviceProvider = provider(InstalledApp("YouTube", "com.google.android.youtube"))

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_OPEN_APP,
                parameters = mapOf("appName" to "youtube"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals(IntentOperations.OPEN_APP, intents.lastOperation)
        assertEquals("com.google.android.youtube", intents.lastParameters["packageName"])
    }

    @Test
    fun openApp_shortOperationNameAlsoWorks() = runTest {
        val deviceProvider = provider(InstalledApp("Chrome", "com.android.chrome"))

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = "open_app",
                parameters = mapOf("appName" to "chrome"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals("com.android.chrome", intents.lastParameters["packageName"])
    }

    @Test
    fun openApp_settings_launchesSettingsIntent() = runTest {
        val deviceProvider = provider()

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_OPEN_APP,
                parameters = mapOf("appName" to "settings"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals(IntentOperations.LAUNCH_SETTINGS, intents.lastOperation)
    }

    @Test
    fun openApp_unknownApp_fallsBackToBrowser() = runTest {
        val deviceProvider = provider(InstalledApp("Chrome", "com.android.chrome"))

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_OPEN_APP,
                parameters = mapOf("appName" to "definitely not installed"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals(IntentOperations.OPEN_URL, intents.lastOperation)
        assertTrue(intents.lastParameters["url"].orEmpty().contains("google.com/search"))
        assertEquals("browser", (response as CapabilityExecutionResponse.Success).output["target"])
    }

    @Test
    fun openApp_youtubeMissing_opensYoutubeWeb() = runTest {
        val deviceProvider = provider()

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_OPEN_APP,
                parameters = mapOf("appName" to "youtube"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals("https://www.youtube.com", intents.lastParameters["url"])
        assertEquals("browser", (response as CapabilityExecutionResponse.Success).output["target"])
    }

    @Test
    fun openApp_withoutAppName_isInvalid() = runTest {
        val deviceProvider = provider()

        val validation = deviceProvider.validate(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_OPEN_APP,
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )

        assertTrue(validation is CapabilityValidationResult.Invalid)
    }

    @Test
    fun appSearch_youtubeInstalled_usesYoutubeDeepLink() = runTest {
        val deviceProvider = provider(InstalledApp("YouTube", "com.google.android.youtube"))

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_APP_SEARCH,
                parameters = mapOf("appName" to "youtube", "searchQuery" to "shape of you"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals(IntentOperations.OPEN_URL, intents.lastOperation)
        assertEquals(
            "https://www.youtube.com/results?search_query=shape+of+you",
            intents.lastParameters["url"],
        )
        assertEquals("com.google.android.youtube", intents.lastParameters["packageName"])
        assertEquals("app", (response as CapabilityExecutionResponse.Success).output["target"])
    }

    @Test
    fun appSearch_youtubeMissing_fallsBackToBrowser() = runTest {
        val deviceProvider = provider()

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_APP_SEARCH,
                parameters = mapOf("appName" to "youtube", "searchQuery" to "shape of you"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals(IntentOperations.OPEN_URL, intents.lastOperation)
        assertNull(intents.lastParameters["packageName"])
        assertEquals("browser", (response as CapabilityExecutionResponse.Success).output["target"])
    }

    @Test
    fun appSearch_spotifyInstalled_usesSpotifyDeepLink() = runTest {
        val deviceProvider = provider(InstalledApp("Spotify", "com.spotify.music"))

        deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_APP_SEARCH,
                parameters = mapOf("appName" to "spotify", "searchQuery" to "blinding lights"),
                traceId = traceId,
            ),
        )

        assertEquals("spotify:search:blinding+lights", intents.lastParameters["url"])
        assertEquals("com.spotify.music", intents.lastParameters["packageName"])
    }

    @Test
    fun appSearch_spotifyMissing_fallsBackToSpotifyWeb() = runTest {
        val deviceProvider = provider()

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_APP_SEARCH,
                parameters = mapOf("appName" to "spotify", "searchQuery" to "blinding lights"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        assertEquals(
            "https://open.spotify.com/search/blinding+lights",
            intents.lastParameters["url"],
        )
        assertNull(intents.lastParameters["packageName"])
        assertEquals("browser", (response as CapabilityExecutionResponse.Success).output["target"])
    }

    @Test
    fun appSearch_unknownApp_fallsBackToWebSearch() = runTest {
        val deviceProvider = provider()

        val response = deviceProvider.execute(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_APP_SEARCH,
                parameters = mapOf("appName" to "someunknownapp", "searchQuery" to "cat videos"),
                traceId = traceId,
            ),
        )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val url = intents.lastParameters["url"].orEmpty()
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertNull(intents.lastParameters["packageName"])
    }

    @Test
    fun appSearch_withoutQuery_isInvalid() = runTest {
        val deviceProvider = provider()

        val validation = deviceProvider.validate(
            CapabilityExecutionRequest(
                operation = CapabilityOperations.DEVICE_APP_SEARCH,
                parameters = mapOf("appName" to "youtube"),
                traceId = traceId,
            ),
        )

        assertTrue(validation is CapabilityValidationResult.Invalid)
    }
}
