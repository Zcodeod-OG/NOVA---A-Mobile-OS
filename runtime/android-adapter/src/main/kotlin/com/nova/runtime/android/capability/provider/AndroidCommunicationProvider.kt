package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.contactsAdapter.ContactsOperations
import com.nova.runtime.android.intentAdapter.IntentOperations
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Communication capabilities backed by Intent and Contacts adapters. */
class AndroidCommunicationProvider(
    context: Context,
    logger: NovaLogger,
    private val adapters: AndroidAdapterLayer,
) : AdapterDelegatingCapabilityProvider(context, logger) {

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
            else -> emptySet()
        }

    override suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult {
        if (request.operation == CapabilityOperations.WHATSAPP_SEND_MESSAGE) {
            val message = request.parameters["message"] ?: request.parameters["text"]
            val phoneNumber = request.parameters["phoneNumber"]
            if (message.isNullOrBlank() && phoneNumber.isNullOrBlank()) {
                return invalidParameters("message or phoneNumber is required for WhatsApp send")
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
        val message = parameters["message"] ?: parameters["text"] ?: ""
        val phoneNumber = parameters["phoneNumber"]?.filter { it.isDigit() }

        return if (!phoneNumber.isNullOrBlank()) {
            val encodedText = URLEncoder.encode(message, StandardCharsets.UTF_8)
            adapters.intents.execute(
                operation = IntentOperations.OPEN_URL,
                parameters = mapOf(
                    "url" to "https://api.whatsapp.com/send?phone=$phoneNumber&text=$encodedText",
                    "packageName" to WHATSAPP_PACKAGE,
                ),
                traceId = traceId,
            )
        } else {
            adapters.intents.execute(
                operation = IntentOperations.SHARE,
                parameters = buildMap {
                    if (message.isNotBlank()) put("text", message)
                    parameters["uri"]?.let { put("uri", it) }
                    parameters["mimeType"]?.let { put("mimeType", it) }
                    put("packageName", WHATSAPP_PACKAGE)
                },
                traceId = traceId,
            )
        }
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
            com.nova.runtime.models.RuntimeError(
                code = "CAPABILITY_INVALID_PARAMETERS",
                category = com.nova.runtime.models.ErrorCategory.VALIDATION,
                severity = com.nova.runtime.models.ErrorSeverity.LOW,
                recoverable = false,
                userVisibleMessage = detail,
                diagnostics = mapOf("providerId" to providerId),
            ),
        )

    private companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
    }
}
