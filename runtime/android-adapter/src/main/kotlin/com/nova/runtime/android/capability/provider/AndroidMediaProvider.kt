package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.intentAdapter.IntentOperations
import com.nova.runtime.android.storageAccessAdapter.StorageAccessOperations
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Media capabilities backed by Intent and Storage Access adapters. */
class AndroidMediaProvider(
    context: Context,
    logger: NovaLogger,
    private val adapters: AndroidAdapterLayer,
) : AdapterDelegatingCapabilityProvider(context, logger) {

    override val providerId: String = "android-media"
    override val capabilityType: String = "media"
    override val version: String = "1.0.0"

    override fun supportedOperations(): Set<String> = setOf(CapabilityOperations.SHARE_FILE)

    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult {
        if (request.parameters["uri"].isNullOrBlank()) {
            return invalidParameters("uri is required for file sharing")
        }
        return CapabilityValidationResult.Valid
    }

    override suspend fun dispatch(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        if (operation != CapabilityOperations.SHARE_FILE) {
            error("unsupported operation: $operation")
        }
        return shareFile(parameters, traceId)
    }

    private suspend fun shareFile(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        val uri = parameters.require("uri")
        val mimeType = parameters["mimeType"] ?: "*/*"

        if (parameters["persist"] == "true") {
            val persistResult = adapters.storageAccess.execute(
                operation = StorageAccessOperations.TAKE_PERSISTABLE_PERMISSION,
                parameters = mapOf(
                    "uri" to uri,
                    "mode" to (parameters["mode"] ?: "read"),
                ),
                traceId = traceId,
            )
            if (persistResult is CapabilityResult.Failure) {
                return persistResult
            }
        }

        if (parameters["validateReadable"] == "true") {
            val openResult = adapters.storageAccess.execute(
                operation = StorageAccessOperations.OPEN_DOCUMENT,
                parameters = mapOf("uri" to uri),
                traceId = traceId,
            )
            if (openResult is CapabilityResult.Failure) {
                return openResult
            }
        }

        return adapters.intents.execute(
            operation = IntentOperations.SHARE,
            parameters = buildMap {
                put("uri", uri)
                put("mimeType", mimeType)
                parameters["text"]?.let { put("text", it) }
                parameters["packageName"]?.let { put("packageName", it) }
            },
            traceId = traceId,
        )
    }

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
}
