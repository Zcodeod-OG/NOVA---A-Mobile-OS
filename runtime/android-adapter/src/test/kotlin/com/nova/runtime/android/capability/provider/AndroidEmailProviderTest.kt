package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.android.email.GmailOAuthManager
import com.nova.runtime.android.email.GmailSyncService
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.storage.entities.MessageEntity
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
class AndroidEmailProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logger = StructuredLogger()
    private val messageRepository = InMemoryMessageRepository()

    @Test
    fun emailSearch_returnsMatchingStoredMessages() = runTest {
        messageRepository.insert(
            MessageEntity(
                id = UUID.randomUUID(),
                channel = GmailSyncService.CHANNEL_GMAIL,
                sender = "boss@company.com",
                body = "Quarterly review on Friday",
                threadKey = "thread-1",
                receivedAt = System.currentTimeMillis(),
                subject = "Review",
                externalId = "gmail:test-1",
            ),
        )

        val provider =
            AndroidEmailProvider(
                context = context,
                logger = logger,
                oauthManager = GmailOAuthManager(context),
                gmailSyncService = GmailSyncService(GmailOAuthManager(context), messageRepository, logger),
                messageRepository = messageRepository,
            )

        val response =
            provider.execute(
                CapabilityExecutionRequest(
                    operation = "email.search",
                    parameters = mapOf("query" to "Quarterly"),
                    traceId = UUID.randomUUID(),
                ),
            )

        assertTrue(response is CapabilityExecutionResponse.Success)
        val success = response as CapabilityExecutionResponse.Success
        assertEquals("1", success.output["count"])
        assertTrue(success.output["messages"]!!.contains("boss@company.com"))
    }
}
