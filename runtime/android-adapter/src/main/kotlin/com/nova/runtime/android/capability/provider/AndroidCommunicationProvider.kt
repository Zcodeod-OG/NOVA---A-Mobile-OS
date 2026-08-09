package com.nova.runtime.android.capability.provider

import android.content.Context
import android.provider.Settings
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.accessibilityAdapter.AccessibilityOperations
import com.nova.runtime.android.accessibilityAdapter.NovaAccessibilityService
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.contactsAdapter.ContactsOperations
import com.nova.runtime.android.intentAdapter.IntentOperations
import com.nova.runtime.android.internal.PermissionChecker
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** Communication capabilities backed by Intent and Contacts adapters. */
class AndroidCommunicationProvider(
    context: Context,
    logger: NovaLogger,
    private val adapters: AndroidAdapterLayer,
) : AdapterDelegatingCapabilityProvider(context, logger) {

    private val accessibilitySettingsPrompted = AtomicBoolean(false)

    override val providerId: String = "android-communication"
    override val capabilityType: String = "communication"
    override val version: String = "1.0.0"

    override fun supportedOperations(): Set<String> =
        setOf(
            CapabilityOperations.WHATSAPP_SEND_MESSAGE,
            CapabilityOperations.CONTACTS_SEARCH,
        )

    override fun requiredPermissions(): Set<String> =
        setOf(android.Manifest.permission.READ_CONTACTS)

    override fun permissionsForOperation(operation: String): Set<String> =
        when (operation) {
            CapabilityOperations.CONTACTS_SEARCH ->
                setOf(android.Manifest.permission.READ_CONTACTS)
            // Phone-number-only WhatsApp sends do not need contacts; name lookup is checked in validate.
            else -> emptySet()
        }

    override suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult {
        if (request.operation == CapabilityOperations.WHATSAPP_SEND_MESSAGE) {
            val message = request.parameters["message"] ?: request.parameters["text"]
            val phoneNumber = request.parameters["phoneNumber"]
            val uri = request.parameters["uri"]
            val recipient = request.parameters["recipient"] ?: request.parameters["name"]
            if (message.isNullOrBlank() && phoneNumber.isNullOrBlank() && uri.isNullOrBlank()) {
                return invalidParameters("message, phoneNumber, or uri is required for WhatsApp send")
            }
            if (
                phoneNumber.isNullOrBlank() &&
                !recipient.isNullOrBlank() &&
                !PermissionChecker.isGranted(context, android.Manifest.permission.READ_CONTACTS)
            ) {
                return CapabilityValidationResult.Invalid(
                    RuntimeError(
                        code = "ANDROID_PERMISSION_DENIED",
                        category = ErrorCategory.PERMISSION,
                        severity = ErrorSeverity.MEDIUM,
                        recoverable = true,
                        userVisibleMessage = "Contacts permission required to find $recipient",
                        diagnostics = mapOf(
                            "providerId" to providerId,
                            "permission" to android.Manifest.permission.READ_CONTACTS,
                            "operation" to request.operation,
                        ),
                    ),
                )
            }
        }
        return CapabilityValidationResult.Valid
    }

    override suspend fun dispatch(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        when (operation) {
            CapabilityOperations.WHATSAPP_SEND_MESSAGE -> sendWhatsAppMessage(parameters, traceId)
            CapabilityOperations.CONTACTS_SEARCH -> searchContacts(parameters, traceId)
            else -> error("unsupported operation: $operation")
        }

    private suspend fun sendWhatsAppMessage(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        // Never treat search.query / raw command as WhatsApp chat text.
        val message = parameters["message"]
            ?: parameters["text"]
            ?: ""
        val uri = parameters["uri"]
        val mimeType = parameters["mimeType"] ?: "*/*"
        val recipient = parameters["recipient"] ?: parameters["name"]

        var phoneNumber = WhatsAppPhoneNormalizer.normalize(parameters["phoneNumber"])
        if (phoneNumber.isNullOrBlank() && !recipient.isNullOrBlank()) {
            phoneNumber = resolvePhoneNumberForContact(recipient, traceId)
                ?.let { WhatsAppPhoneNormalizer.normalize(it) }
        }

        // Document / attachment: jid-targeted ACTION_SEND into the chat, then a11y Send.
        if (!uri.isNullOrBlank()) {
            return sendWhatsAppDocument(
                uri = uri,
                mimeType = mimeType,
                message = message,
                recipient = recipient,
                phoneNumber = phoneNumber,
                traceId = traceId,
            )
        }

        if (!phoneNumber.isNullOrBlank()) {
            val chatText = message.takeIf { it.isNotBlank() && !looksLikeRawCommand(it, recipient) }.orEmpty()
            val openResult = openWhatsAppChat(phoneNumber, chatText, traceId)
            if (openResult is CapabilityResult.Failure) return openResult

            val recipientLabel = recipient?.takeIf { it.isNotBlank() } ?: phoneNumber
            val autoSend = tryAutoSendWhatsApp(traceId)
            val userMessage =
                if (autoSend.sent) {
                    "Auto-sent to $recipientLabel"
                } else {
                    "Enable NOVA in Accessibility settings to auto-send — chat is open with your message"
                }

            if (!autoSend.sent && autoSend.accessibilityUnavailable) {
                promptAccessibilitySettingsOnce(traceId)
            }

            val base = (openResult as CapabilityResult.Success).output
            return CapabilityResult.Success(
                base + mapOf(
                    "phoneNumber" to phoneNumber,
                    "autoSent" to autoSend.sent.toString(),
                    "userMessage" to userMessage,
                    "status" to if (autoSend.sent) "sent" else "chat_opened",
                ),
            )
        }

        val missingContactHint =
            if (!recipient.isNullOrBlank()) {
                "Couldn't find ${recipient}'s number — pick a contact"
            } else {
                "Couldn't resolve a WhatsApp number — pick a contact"
            }

        val fallbackText = message.takeIf { it.isNotBlank() && !looksLikeRawCommand(it, recipient) }
        val shareResult = adapters.intents.execute(
            operation = IntentOperations.SHARE,
            parameters = buildMap {
                fallbackText?.let { put("text", it) }
                put("packageName", WHATSAPP_PACKAGE)
            },
            traceId = traceId,
        )

        return when (shareResult) {
            is CapabilityResult.Success ->
                CapabilityResult.Success(
                    shareResult.output + mapOf(
                        "userMessage" to missingContactHint,
                        "status" to "share_fallback",
                    ),
                )
            is CapabilityResult.Failure -> shareResult
        }
    }

    /**
     * Shares a document into WhatsApp, preferring a jid-targeted ACTION_SEND that lands in the
     * contact chat with the file attached (skips the "send to" picker), then auto-taps Send.
     */
    private suspend fun sendWhatsAppDocument(
        uri: String,
        mimeType: String,
        message: String,
        recipient: String?,
        phoneNumber: String?,
        traceId: UUID,
    ): CapabilityResult {
        val shareText = message.takeIf { it.isNotBlank() && !looksLikeRawCommand(it, recipient) }
        val recipientLabel = recipient?.takeIf { it.isNotBlank() } ?: "contact"
        val packages = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
        val jid = phoneNumber?.takeIf { it.isNotBlank() }?.let { "$it@$WHATSAPP_JID_HOST" }

        var lastFailure: CapabilityResult.Failure? = null
        var shareSuccess: CapabilityResult.Success? = null
        var usedJid = false

        // 1) Prefer jid-targeted share → opens media composer for that chat.
        if (jid != null) {
            for (packageName in packages) {
                when (
                    val result = shareDocumentToWhatsApp(
                        uri = uri,
                        mimeType = mimeType,
                        packageName = packageName,
                        jid = jid,
                        text = shareText,
                        traceId = traceId,
                    )
                ) {
                    is CapabilityResult.Success -> {
                        shareSuccess = result
                        usedJid = true
                        logger.info(
                            module = "ANDROID_ADAPTER",
                            message = "WhatsApp document share opened via jid",
                            traceId = traceId,
                            metadata = mapOf(
                                "jid" to jid,
                                "packageName" to packageName,
                                "uri" to uri,
                                "mimeType" to mimeType,
                            ),
                        )
                        break
                    }
                    is CapabilityResult.Failure -> lastFailure = result
                }
            }
        }

        // 2) Fallback: package-scoped ACTION_SEND (may show WhatsApp contact picker).
        if (shareSuccess == null) {
            for (packageName in packages) {
                when (
                    val result = shareDocumentToWhatsApp(
                        uri = uri,
                        mimeType = mimeType,
                        packageName = packageName,
                        jid = null,
                        text = shareText,
                        traceId = traceId,
                    )
                ) {
                    is CapabilityResult.Success -> {
                        shareSuccess = result
                        logger.info(
                            module = "ANDROID_ADAPTER",
                            message = "WhatsApp document share opened without jid (picker possible)",
                            traceId = traceId,
                            metadata = mapOf(
                                "packageName" to packageName,
                                "uri" to uri,
                                "recipient" to recipientLabel,
                            ),
                        )
                        break
                    }
                    is CapabilityResult.Failure -> lastFailure = result
                }
            }
        }

        if (shareSuccess == null) {
            // Last resort: open chat with extracted content snippet (not the raw command).
            if (!phoneNumber.isNullOrBlank() && !shareText.isNullOrBlank()) {
                logger.warn(
                    module = "ANDROID_ADAPTER",
                    message = "WhatsApp file share failed — falling back to chat snippet",
                    traceId = traceId,
                    metadata = mapOf("uri" to uri),
                )
                return openWhatsAppChat(phoneNumber, shareText, traceId)
            }
            return lastFailure ?: CapabilityResult.Failure(
                RuntimeError(
                    code = "WHATSAPP_SHARE_FAILED",
                    category = ErrorCategory.EXECUTION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = true,
                    userVisibleMessage = "Could not share document on WhatsApp",
                    diagnostics = mapOf("uri" to uri),
                ),
            )
        }

        // If we landed on the contact picker, try selecting the recipient via accessibility.
        if (!usedJid && !recipient.isNullOrBlank()) {
            when (trySelectWhatsAppContact(recipient, traceId)) {
                ClickAttempt.Success ->
                    logger.info(
                        module = "ANDROID_ADAPTER",
                        message = "WhatsApp contact selected via accessibility",
                        traceId = traceId,
                        metadata = mapOf("recipient" to recipient),
                    )
                ClickAttempt.Unavailable -> Unit
                ClickAttempt.NotFound ->
                    logger.warn(
                        module = "ANDROID_ADAPTER",
                        message = "WhatsApp contact not found on picker for accessibility tap",
                        traceId = traceId,
                        metadata = mapOf("recipient" to recipient),
                    )
            }
        }

        val autoSend = tryAutoSendWhatsApp(traceId, documentMode = true)
        if (!autoSend.sent && autoSend.accessibilityUnavailable) {
            promptAccessibilitySettingsOnce(traceId)
        }

        val userMessage =
            when {
                autoSend.sent -> "Auto-sent document to $recipientLabel"
                usedJid ->
                    "Enable NOVA in Accessibility settings to auto-send — document is ready in chat"
                else ->
                    "Enable NOVA in Accessibility settings to auto-send — pick $recipientLabel if needed"
            }

        return CapabilityResult.Success(
            shareSuccess.output + buildMap {
                put("userMessage", userMessage)
                put("autoSent", autoSend.sent.toString())
                put(
                    "status",
                    when {
                        autoSend.sent -> "sent"
                        usedJid -> "document_ready"
                        else -> "shared_document"
                    },
                )
                phoneNumber?.let { put("phoneNumber", it) }
                if (usedJid) put("jid", jid!!)
            },
        )
    }

    private suspend fun shareDocumentToWhatsApp(
        uri: String,
        mimeType: String,
        packageName: String,
        jid: String?,
        text: String?,
        traceId: UUID,
    ): CapabilityResult =
        adapters.intents.execute(
            operation = IntentOperations.SHARE,
            parameters = buildMap {
                put("uri", uri)
                put("mimeType", mimeType)
                put("packageName", packageName)
                jid?.let { put("jid", it) }
                text?.let { put("text", it) }
            },
            traceId = traceId,
        )

    /**
     * Opens a specific WhatsApp chat with [message] prefilled.
     * Prefer jid/phone deep links with setPackage — never a generic SENDTO chooser.
     */
    private suspend fun openWhatsAppChat(
        phoneNumber: String,
        message: String,
        traceId: UUID,
    ): CapabilityResult {
        val encodedText = URLEncoder.encode(message, StandardCharsets.UTF_8)
        val urls = listOf(
            "https://wa.me/$phoneNumber?text=$encodedText",
            "https://api.whatsapp.com/send?phone=$phoneNumber&text=$encodedText",
        )
        val packages = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)

        var lastFailure: CapabilityResult.Failure? = null
        for (url in urls) {
            for (packageName in packages) {
                when (
                    val result = adapters.intents.execute(
                        operation = IntentOperations.OPEN_URL,
                        parameters = mapOf(
                            "url" to url,
                            "packageName" to packageName,
                        ),
                        traceId = traceId,
                    )
                ) {
                    is CapabilityResult.Success ->
                        return CapabilityResult.Success(
                            result.output + mapOf(
                                "url" to url,
                                "packageName" to packageName,
                            ),
                        )
                    is CapabilityResult.Failure -> lastFailure = result
                }
            }
        }

        return lastFailure ?: CapabilityResult.Failure(
            RuntimeError(
                code = "WHATSAPP_OPEN_FAILED",
                category = ErrorCategory.EXECUTION,
                severity = ErrorSeverity.MEDIUM,
                recoverable = true,
                userVisibleMessage = "Could not open WhatsApp chat",
                diagnostics = mapOf("phoneNumber" to phoneNumber),
            ),
        )
    }

    private data class AutoSendOutcome(
        val sent: Boolean,
        val accessibilityUnavailable: Boolean = false,
    )

    /**
     * After WhatsApp opens, wait briefly then poll for the Send control and click it.
     * Supports consumer WhatsApp and WhatsApp Business. Document/media composer needs a longer
     * settle delay than plain text chat.
     */
    private suspend fun tryAutoSendWhatsApp(
        traceId: UUID,
        documentMode: Boolean = false,
    ): AutoSendOutcome {
        val initialDelay =
            if (documentMode) AUTO_SEND_DOCUMENT_INITIAL_DELAY_MS else AUTO_SEND_INITIAL_DELAY_MS
        val pollBudget =
            if (documentMode) AUTO_SEND_DOCUMENT_POLL_BUDGET_MS else AUTO_SEND_POLL_BUDGET_MS

        delay(initialDelay)

        var elapsedMs = initialDelay
        var sawUnavailable = false
        while (elapsedMs <= pollBudget) {
            when (val attempt = clickWhatsAppSend(traceId)) {
                ClickAttempt.Success -> {
                    logger.info(
                        module = "ANDROID_ADAPTER",
                        message = "WhatsApp Send tapped via accessibility",
                        traceId = traceId,
                        metadata = mapOf("documentMode" to documentMode.toString()),
                    )
                    return AutoSendOutcome(sent = true)
                }
                ClickAttempt.Unavailable -> {
                    sawUnavailable = true
                    break
                }
                ClickAttempt.NotFound -> {
                    delay(AUTO_SEND_POLL_INTERVAL_MS)
                    elapsedMs += AUTO_SEND_POLL_INTERVAL_MS
                }
            }
        }

        return AutoSendOutcome(
            sent = false,
            accessibilityUnavailable = sawUnavailable || !NovaAccessibilityService.isEnabled(),
        )
    }

    private sealed class ClickAttempt {
        data object Success : ClickAttempt()
        data object NotFound : ClickAttempt()
        data object Unavailable : ClickAttempt()
    }

    /**
     * On WhatsApp's contact/"send to" picker, tap the matching contact row so the media composer
     * opens for that chat. Used when jid-targeted share is unavailable.
     */
    private suspend fun trySelectWhatsAppContact(
        recipient: String,
        traceId: UUID,
    ): ClickAttempt {
        delay(AUTO_SEND_INITIAL_DELAY_MS)
        val labels = contactTapLabels(recipient)
        var elapsedMs = AUTO_SEND_INITIAL_DELAY_MS
        var sawUnavailable = false

        while (elapsedMs <= AUTO_SEND_POLL_BUDGET_MS) {
            for (label in labels) {
                when (val attempt = clickWhatsAppLabel(label, traceId)) {
                    ClickAttempt.Success -> return ClickAttempt.Success
                    ClickAttempt.Unavailable -> {
                        sawUnavailable = true
                        break
                    }
                    ClickAttempt.NotFound -> Unit
                }
            }
            if (sawUnavailable) return ClickAttempt.Unavailable
            delay(AUTO_SEND_POLL_INTERVAL_MS)
            elapsedMs += AUTO_SEND_POLL_INTERVAL_MS
        }
        return ClickAttempt.NotFound
    }

    private fun contactTapLabels(recipient: String): List<String> {
        val trimmed = recipient.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        return linkedSetOf(
            trimmed,
            parts.firstOrNull().orEmpty(),
            parts.take(2).joinToString(" "),
        ).filter { it.isNotBlank() }
    }

    private suspend fun clickWhatsAppSend(traceId: UUID): ClickAttempt {
        val packageFilter = "$WHATSAPP_PACKAGE,$WHATSAPP_BUSINESS_PACKAGE"

        for (viewId in WHATSAPP_SEND_VIEW_IDS) {
            when (
                val result = adapters.accessibility.execute(
                    operation = AccessibilityOperations.CLICK,
                    parameters = mapOf(
                        "viewId" to viewId,
                        "packageName" to packageFilter,
                    ),
                    traceId = traceId,
                )
            ) {
                is CapabilityResult.Success -> return ClickAttempt.Success
                is CapabilityResult.Failure -> {
                    if (result.error.code == "ANDROID_ACCESSIBILITY_UNAVAILABLE") {
                        return ClickAttempt.Unavailable
                    }
                }
            }
        }

        for (label in WHATSAPP_SEND_LABELS) {
            when (val attempt = clickWhatsAppLabel(label, traceId)) {
                ClickAttempt.Success -> return ClickAttempt.Success
                ClickAttempt.Unavailable -> return ClickAttempt.Unavailable
                ClickAttempt.NotFound -> Unit
            }
        }

        return ClickAttempt.NotFound
    }

    private suspend fun clickWhatsAppLabel(label: String, traceId: UUID): ClickAttempt {
        val packageFilter = "$WHATSAPP_PACKAGE,$WHATSAPP_BUSINESS_PACKAGE"
        for (key in listOf("contentDescription", "text")) {
            when (
                val result = adapters.accessibility.execute(
                    operation = AccessibilityOperations.CLICK,
                    parameters = mapOf(
                        key to label,
                        "packageName" to packageFilter,
                    ),
                    traceId = traceId,
                )
            ) {
                is CapabilityResult.Success -> return ClickAttempt.Success
                is CapabilityResult.Failure -> {
                    if (result.error.code == "ANDROID_ACCESSIBILITY_UNAVAILABLE") {
                        return ClickAttempt.Unavailable
                    }
                }
            }
        }
        return ClickAttempt.NotFound
    }

    private suspend fun promptAccessibilitySettingsOnce(traceId: UUID) {
        if (!accessibilitySettingsPrompted.compareAndSet(false, true)) return
        adapters.intents.execute(
            operation = IntentOperations.LAUNCH_SETTINGS,
            parameters = mapOf("settingsAction" to Settings.ACTION_ACCESSIBILITY_SETTINGS),
            traceId = traceId,
        )
    }

    private suspend fun resolvePhoneNumberForContact(name: String, traceId: UUID): String? {
        val candidates = linkedSetOf(name.trim(), name.trim().lowercase())
        var bestPhone: String? = null
        var bestRank = Int.MAX_VALUE

        for (query in candidates) {
            if (query.isBlank()) continue
            val searchResult = searchContacts(mapOf("query" to query, "name" to query), traceId)
            if (searchResult !is CapabilityResult.Success) continue

            val contacts = searchResult.output["contacts"].orEmpty()
            if (contacts.isBlank()) continue

            for (entry in contacts.split("|")) {
                if (entry.isBlank()) continue
                val contactId = entry.substringBefore(":").toLongOrNull() ?: continue
                val displayName = entry.substringAfter(":", missingDelimiterValue = "")
                val rank = matchRank(name, displayName)
                if (rank >= bestRank) continue

                val phone = retrievePhone(contactId, traceId) ?: continue
                bestPhone = phone
                bestRank = rank
                if (rank == 0) return phone
            }
        }

        return bestPhone
    }

    /** 0 = exact (case-insensitive), 1 = starts-with, 2 = contains / other. */
    private fun matchRank(query: String, displayName: String): Int {
        val q = query.trim().lowercase()
        val d = displayName.trim().lowercase()
        if (q.isEmpty() || d.isEmpty()) return 2
        return when {
            d == q -> 0
            d.startsWith(q) || q.startsWith(d) -> 1
            else -> 2
        }
    }

    private suspend fun retrievePhone(contactId: Long, traceId: UUID): String? =
        when (
            val retrieve = adapters.contacts.execute(
                operation = ContactsOperations.RETRIEVE,
                parameters = mapOf("contactId" to contactId.toString()),
                traceId = traceId,
            )
        ) {
            is CapabilityResult.Success ->
                retrieve.output["phoneNumber"]?.takeIf { it.isNotBlank() }
            else -> null
        }

    private suspend fun searchContacts(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        adapters.contacts.execute(
            operation = ContactsOperations.SEARCH,
            parameters = mapOf("query" to (parameters["query"] ?: parameters["name"].orEmpty())),
            traceId = traceId,
        )

    private fun invalidParameters(detail: String): CapabilityValidationResult.Invalid =
        CapabilityValidationResult.Invalid(
            RuntimeError(
                code = "CAPABILITY_INVALID_PARAMETERS",
                category = ErrorCategory.VALIDATION,
                severity = ErrorSeverity.LOW,
                recoverable = false,
                userVisibleMessage = detail,
                diagnostics = mapOf("providerId" to providerId),
            ),
        )

    /**
     * Detects when [text] is the full user command (e.g. "send bookly … to Atharv") rather than
     * a real chat body — must never be pasted into WhatsApp.
     */
    private fun looksLikeRawCommand(text: String, recipient: String?): Boolean {
        val lower = text.trim().lowercase()
        if (!lower.startsWith("send ") && !lower.startsWith("share ") && !lower.startsWith("find ")) {
            return false
        }
        if (Regex("""\bto\b""").containsMatchIn(lower)) return true
        val name = recipient?.trim()?.lowercase()
        return !name.isNullOrBlank() && name in lower
    }

    private companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val WHATSAPP_JID_HOST = "s.whatsapp.net"
        const val AUTO_SEND_INITIAL_DELAY_MS = 700L
        const val AUTO_SEND_POLL_INTERVAL_MS = 250L
        /** Total wait after open (initial delay counts toward this budget). */
        const val AUTO_SEND_POLL_BUDGET_MS = 3_000L
        /** Media composer takes longer to inflate than a text chat. */
        const val AUTO_SEND_DOCUMENT_INITIAL_DELAY_MS = 1_200L
        const val AUTO_SEND_DOCUMENT_POLL_BUDGET_MS = 5_000L

        val WHATSAPP_SEND_VIEW_IDS = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp:id/send_media_btn",
            "com.whatsapp:id/conversation_entry_action_button",
            "com.whatsapp:id/send_button",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp.w4b:id/send_btn",
            "com.whatsapp.w4b:id/send_media_btn",
            "com.whatsapp.w4b:id/conversation_entry_action_button",
            "com.whatsapp.w4b:id/send_button",
        )

        val WHATSAPP_SEND_LABELS = listOf("Send", "Send message", "Send file")
    }
}
