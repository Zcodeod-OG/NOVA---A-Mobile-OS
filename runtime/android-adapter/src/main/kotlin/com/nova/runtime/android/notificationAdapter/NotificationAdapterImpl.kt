package com.nova.runtime.android.notificationAdapter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.android.internal.PermissionChecker
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
    private val messageRepository: MessageRepository? = null,
) : NotificationAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            NotificationOperations.PUBLISH,
            NotificationOperations.DISMISS,
            NotificationOperations.READ,
        )

    override fun requiredPermissions(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptySet()
        }

    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        if (operation !in supportedOperations()) {
            return CapabilityResult.Failure(
                AdapterErrorMapper.invalidOperation(ADAPTER_NAME, operation, supportedOperations()),
            )
        }
        return AdapterBoundary.execute(logger, ADAPTER_NAME, operation, traceId) {
            withContext(Dispatchers.IO) {
                when (operation) {
                    NotificationOperations.PUBLISH -> publish(parameters)
                    NotificationOperations.DISMISS -> dismiss(parameters)
                    NotificationOperations.READ -> read(parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun publish(parameters: Map<String, String>): Map<String, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionChecker.ensureGranted(context, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val channelId = parameters["channelId"] ?: DEFAULT_CHANNEL_ID
        val notificationId = parameters["notificationId"]?.toIntOrNull() ?: DEFAULT_NOTIFICATION_ID
        val title = parameters.require("title")
        val body = parameters["body"] ?: ""
        ensureChannel(channelId, parameters["channelName"] ?: "NOVA")
        val notification =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return mapOf(
            "notificationId" to notificationId.toString(),
            "channelId" to channelId,
            "status" to "published",
        )
    }

    private fun dismiss(parameters: Map<String, String>): Map<String, String> {
        val notificationId = parameters.require("notificationId").toInt()
        NotificationManagerCompat.from(context).cancel(notificationId)
        return mapOf("notificationId" to notificationId.toString(), "status" to "dismissed")
    }

    private suspend fun read(parameters: Map<String, String>): Map<String, String> {
        if (!NotificationListenerState.isEnabled(context)) {
            throw SecurityException("Notification listener not enabled")
        }
        val active = NotificationListenerState.snapshot()
        val output = mutableMapOf(
            "count" to active.size.toString(),
            "notifications" to active.joinToString("|"),
        )
        val channel = parameters["channel"]
        if (messageRepository != null && !channel.isNullOrBlank()) {
            val limit = parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 25
            val messages = messageRepository.search(channel, parameters["query"].orEmpty(), limit)
            output["messageCount"] = messages.size.toString()
            output["messages"] =
                messages.joinToString("|") { msg ->
                    listOf(
                        msg.id,
                        msg.sender,
                        msg.body.replace("|", " "),
                        msg.receivedAt,
                    ).joinToString(":")
                }
        }
        return output
    }

    private fun ensureChannel(channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        val channel =
            NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        manager.createNotificationChannel(channel)
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private companion object {
        const val ADAPTER_NAME = "Notification"
        const val DEFAULT_CHANNEL_ID = "nova_runtime"
        const val DEFAULT_NOTIFICATION_ID = 1001
    }
}

/** Holds active notification summaries when listener service is connected. */
object NotificationListenerState {
    private val activeNotifications = mutableListOf<String>()

    fun isEnabled(context: Context): Boolean {
        if (NovaNotificationListenerService.isConnected()) return true
        val enabled =
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
        return enabled.contains(context.packageName)
    }

    fun updateSnapshot(entries: List<String>) {
        synchronized(activeNotifications) {
            activeNotifications.clear()
            activeNotifications.addAll(entries)
        }
    }

    fun snapshot(): List<String> =
        synchronized(activeNotifications) {
            activeNotifications.toList()
        }
}

class NotificationAdapterStub : NotificationAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(
            NotificationOperations.PUBLISH,
            NotificationOperations.DISMISS,
            NotificationOperations.READ,
        )

    override fun requiredPermissions(): Set<String> = emptySet()
}
