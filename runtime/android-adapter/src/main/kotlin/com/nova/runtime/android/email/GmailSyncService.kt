package com.nova.runtime.android.email

import com.nova.runtime.storage.entities.MessageEntity
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.utils.logging.NovaLogger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Pulls recent Gmail threads into [MessageEntity] with channel=gmail. */
class GmailSyncService(
    private val oauthManager: GmailOAuthManager,
    private val messageRepository: MessageRepository,
    private val logger: NovaLogger,
) {
    suspend fun syncRecentDays(days: Int = DEFAULT_SYNC_DAYS): SyncResult {
        val token = oauthManager.getAccessToken()
            ?: return SyncResult.NotAuthenticated("Sign in to Gmail first")

        val query = "newer_than:${days}d"
        val listUrl =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages?" +
                "q=${URLEncoder.encode(query, StandardCharsets.UTF_8)}&maxResults=$DEFAULT_MAX_RESULTS"
        val listJson = fetchJson(listUrl, token)
            ?: return SyncResult.Failure("Could not list Gmail messages")

        val messageIds = parseMessageIds(listJson)
        var inserted = 0
        for (messageId in messageIds) {
            val detailUrl =
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?" +
                    "format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date"
            val detailJson = fetchJson(detailUrl, token) ?: continue
            val parsed = parseMessageDetail(messageId, detailJson) ?: continue
            val entity =
                MessageEntity(
                    id = UUID.randomUUID(),
                    channel = CHANNEL_GMAIL,
                    sender = parsed.sender,
                    body = parsed.body,
                    threadKey = parsed.threadId,
                    receivedAt = parsed.receivedAt,
                    externalId = "gmail:$messageId",
                    subject = parsed.subject,
                )
            if (messageRepository.insert(entity)) {
                inserted++
            }
        }

        oauthManager.recordSyncTimestamp()
        logger.info(
            module = "ANDROID_ADAPTER",
            message = "Gmail sync completed",
            metadata = mapOf(
                "inserted" to inserted.toString(),
                "fetched" to messageIds.size.toString(),
            ),
        )
        return SyncResult.Success(inserted = inserted, fetched = messageIds.size)
    }

    private fun parseMessageIds(listJson: JSONObject): List<String> {
        val messages = listJson.optJSONArray("messages") ?: return emptyList()
        return buildList {
            for (index in 0 until messages.length()) {
                val id = messages.optJSONObject(index)?.optString("id").orEmpty()
                if (id.isNotBlank()) add(id)
            }
        }
    }

    internal fun parseMessageDetail(messageId: String, json: JSONObject): ParsedGmailMessage? {
        val threadId = json.optString("threadId", messageId)
        val snippet = json.optString("snippet").orEmpty()
        val payload = json.optJSONObject("payload") ?: return null
        val headers = payload.optJSONArray("headers") ?: JSONArray()
        var from = ""
        var subject = ""
        var dateHeader = ""
        for (index in 0 until headers.length()) {
            val header = headers.optJSONObject(index) ?: continue
            when (header.optString("name").lowercase()) {
                "from" -> from = header.optString("value")
                "subject" -> subject = header.optString("value")
                "date" -> dateHeader = header.optString("value")
            }
        }
        val internalDate = json.optLong("internalDate", System.currentTimeMillis())
        val receivedAt = if (internalDate > 0L) internalDate else System.currentTimeMillis()
        val body = snippet.ifBlank { subject }.ifBlank { from }
        if (body.isBlank()) return null
        return ParsedGmailMessage(
            messageId = messageId,
            threadId = threadId,
            sender = from.ifBlank { "Unknown sender" },
            subject = subject,
            body = body,
            receivedAt = receivedAt,
            dateHeader = dateHeader,
        )
    }

    private fun fetchJson(url: String, accessToken: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return try {
            val code = connection.responseCode
            val stream =
                if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                logger.warn(
                    module = "ANDROID_ADAPTER",
                    message = "Gmail API error",
                    metadata = mapOf("code" to code.toString(), "body" to body.take(200)),
                )
                null
            } else {
                JSONObject(body)
            }
        } catch (exception: Exception) {
            logger.warn(
                module = "ANDROID_ADAPTER",
                message = "Gmail API request failed",
                metadata = mapOf("error" to (exception.message ?: exception.javaClass.simpleName)),
            )
            null
        } finally {
            connection.disconnect()
        }
    }

    internal data class ParsedGmailMessage(
        val messageId: String,
        val threadId: String,
        val sender: String,
        val subject: String,
        val body: String,
        val receivedAt: Long,
        val dateHeader: String,
    )

    sealed interface SyncResult {
        data class Success(val inserted: Int, val fetched: Int) : SyncResult
        data class NotAuthenticated(val message: String) : SyncResult
        data class Failure(val message: String) : SyncResult
    }

    companion object {
        const val CHANNEL_GMAIL = "gmail"
        const val DEFAULT_SYNC_DAYS = 30
        const val DEFAULT_MAX_RESULTS = 50
    }
}
