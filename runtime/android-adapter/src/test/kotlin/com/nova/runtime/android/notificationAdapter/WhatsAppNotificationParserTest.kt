package com.nova.runtime.android.notificationAdapter

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WhatsAppNotificationParserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun parseMessagingStyle_extractsSenderAndBody() {
        val notification =
            NotificationCompat.Builder(context, "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(
                    NotificationCompat.MessagingStyle("Family chat")
                        .addMessage(
                            "See you at 6",
                            1_700_000_000_000L,
                            Person.Builder().setName("Alex").build(),
                        ),
                )
                .build()

        val messages =
            WhatsAppNotificationParser.parseMessagingStyleFromNotification(
                notification = notification,
                threadKey = "thread-1",
                receivedAt = 1_700_000_000_000L,
                externalPrefix = "wa:1",
            )

        assertEquals(1, messages.size)
        assertEquals("Alex", messages.first().sender)
        assertEquals("See you at 6", messages.first().body)
        assertEquals(WhatsAppNotificationParser.CHANNEL_WHATSAPP, messages.first().channel)
    }

    @Test
    fun parseTextExtras_fallsBackToBigText() {
        val extras =
            Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Jordan")
                putCharSequence(Notification.EXTRA_TEXT, "Hi")
                putCharSequence(Notification.EXTRA_BIG_TEXT, "Hi — can we meet tomorrow?")
            }
        val messages =
            WhatsAppNotificationParser.parseTextExtras(
                extras = extras,
                threadKey = "thread-2",
                receivedAt = 1_700_000_000_000L,
                externalPrefix = "wa:2",
            )

        assertEquals(1, messages.size)
        assertEquals("Jordan", messages.first().sender)
        assertTrue(messages.first().body.contains("tomorrow"))
    }
}
