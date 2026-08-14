package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.email.GmailOAuthManager
import com.nova.runtime.android.email.GmailSyncService
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Gmail read/search capabilities backed by OAuth sync + local [MessageEntity] storage. */
class AndroidEmailProvider(
    context: Context,
    logger: NovaLogger,
    private val oauthManager: GmailOAuthManager,
    private val gmailSyncService: GmailSyncService,
    private val messageRepository: MessageRepository,
) : AdapterDelegatingCapabilityProvider(context, logger) {

    override val providerId: String = "android-email"
    override val capabilityType: String = "email"
    override val version: String = "1.0.0"

    override fun supportedOperations(): Set<String> =
        setOf(
            CapabilityOperations.EMAIL_READ,
            CapabilityOperations.EMAIL_SEARCH,
        )

    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult {
        if (request.operation == CapabilityOperations.EMAIL_READ && !oauthManager.isSignedIn()) {
            return CapabilityValidationResult.Invalid(
                RuntimeError(
                    code = "GMAIL_NOT_AUTHENTICATED",
                    category = ErrorCategory.PERMISSION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = true,
                    userVisibleMessage = "Sign in to Gmail to read mail",
                    diagnostics = mapOf("providerId" to providerId),
                ),
            )
        }
        return CapabilityValidationResult.Valid
    }

    override suspend fun dispatch(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        when (operation) {
            CapabilityOperations.EMAIL_READ -> readEmail(parameters, traceId)
            CapabilityOperations.EMAIL_SEARCH -> searchEmail(parameters, traceId)
            else -> error("unsupported operation: $operation")
        }

    private suspend fun readEmail(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        val sync = parameters["sync"]?.toBooleanStrictOrNull() ?: true
        if (sync) {
            when (val result = gmailSyncService.syncRecentDays()) {
                is GmailSyncService.SyncResult.NotAuthenticated ->
                    return CapabilityResult.Failure(
                        RuntimeError(
                            code = "GMAIL_NOT_AUTHENTICATED",
                            category = ErrorCategory.PERMISSION,
                            severity = ErrorSeverity.MEDIUM,
                            recoverable = true,
                            userVisibleMessage = result.message,
                            diagnostics = mapOf("providerId" to providerId),
                        ),
                    )
                is GmailSyncService.SyncResult.Failure ->
                    logger.warn(
                        module = "ANDROID_ADAPTER",
                        message = "Gmail sync failed during email.read",
                        traceId = traceId,
                        metadata = mapOf("detail" to result.message),
                    )
                is GmailSyncService.SyncResult.Success -> Unit
            }
        }

        val limit = parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 25
        val messages = messageRepository.listByChannel(GmailSyncService.CHANNEL_GMAIL, limit)
        return CapabilityResult.Success(
            mapOf(
                "count" to messages.size.toString(),
                "account" to oauthManager.storedAccountEmail().orEmpty(),
                "lastSyncAt" to oauthManager.lastSyncAt().toString(),
                "messages" to formatMessages(messages),
                "userMessage" to formatUserSummary(messages),
            ),
        )
    }

    private suspend fun searchEmail(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        val query = parameters["query"] ?: parameters["q"]
        if (query.isNullOrBlank()) {
            return CapabilityResult.Failure(
                RuntimeError(
                    code = "CAPABILITY_INVALID_PARAMETERS",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.LOW,
                    recoverable = false,
                    userVisibleMessage = "query is required for email.search",
                    diagnostics = mapOf("providerId" to providerId),
                ),
            )
        }
        val limit = parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 25
        val messages =
            messageRepository.search(GmailSyncService.CHANNEL_GMAIL, query, limit)
        return CapabilityResult.Success(
            mapOf(
                "count" to messages.size.toString(),
                "query" to query,
                "messages" to formatMessages(messages),
                "userMessage" to formatUserSummary(messages, query),
            ),
        )
    }

    private fun formatMessages(messages: List<com.nova.runtime.storage.entities.MessageEntity>): String =
        messages.joinToString("|") { message ->
            listOf(
                message.id,
                message.sender.replace("|", " "),
                message.subject.orEmpty().replace("|", " "),
                message.body.replace("|", " "),
                message.receivedAt,
            ).joinToString(":")
        }

    private fun formatUserSummary(
        messages: List<com.nova.runtime.storage.entities.MessageEntity>,
        query: String? = null,
    ): String {
        if (messages.isEmpty()) {
            return if (query.isNullOrBlank()) {
                "No Gmail messages indexed yet"
            } else {
                "No Gmail messages matched \"$query\""
            }
        }
        val preview = messages.take(3).joinToString("; ") { msg ->
            val subject = msg.subject?.takeIf { it.isNotBlank() } ?: msg.body.take(80)
            "${msg.sender}: $subject"
        }
        return if (query.isNullOrBlank()) {
            "${messages.size} recent emails — $preview"
        } else {
            "${messages.size} matches for \"$query\" — $preview"
        }
    }
}
