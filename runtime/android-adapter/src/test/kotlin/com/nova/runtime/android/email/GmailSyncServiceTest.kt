package com.nova.runtime.android.email

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GmailSyncServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val service =
        GmailSyncService(
            oauthManager = GmailOAuthManager(context),
            messageRepository = com.nova.runtime.android.capability.provider.InMemoryMessageRepository(),
            logger = com.nova.runtime.utils.logging.StructuredLogger(),
        )

    @Test
    fun parseMessageDetail_extractsHeadersAndSnippet() {
        val json =
            JSONObject(
                """
                {
                  "id": "msg-1",
                  "threadId": "thread-1",
                  "snippet": "Project update attached",
                  "internalDate": "1700000000000",
                  "payload": {
                    "headers": [
                      {"name": "From", "value": "team@example.com"},
                      {"name": "Subject", "value": "Weekly sync"},
                      {"name": "Date", "value": "Mon, 1 Jan 2024 10:00:00 +0000"}
                    ]
                  }
                }
                """.trimIndent(),
            )

        val parsed = service.parseMessageDetail("msg-1", json)

        assertNotNull(parsed)
        assertEquals("team@example.com", parsed!!.sender)
        assertEquals("Weekly sync", parsed.subject)
        assertEquals("Project update attached", parsed.body)
        assertEquals("thread-1", parsed.threadId)
    }
}
