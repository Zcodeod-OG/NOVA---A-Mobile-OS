package com.nova.runtime.android.internal

import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Wraps adapter calls with trace logging and AIS §11 error translation. */
internal object AdapterBoundary {
    suspend fun execute(
        logger: NovaLogger,
        adapterName: String,
        operation: String,
        traceId: UUID,
        block: suspend () -> Map<String, String>,
    ): CapabilityResult {
        val startMs = System.currentTimeMillis()
        return try {
            val output = block()
            logger.info(
                module = RuntimeModule.ANDROID_ADAPTER.name,
                message = "$adapterName.$operation completed",
                traceId = traceId,
                durationMs = System.currentTimeMillis() - startMs,
                metadata = mapOf("adapter" to adapterName, "operation" to operation),
            )
            CapabilityResult.Success(output)
        } catch (securityException: SecurityException) {
            logger.warn(
                module = RuntimeModule.ANDROID_ADAPTER.name,
                message = "$adapterName.$operation permission denied",
                traceId = traceId,
                throwable = securityException,
                metadata = mapOf("adapter" to adapterName, "operation" to operation),
            )
            CapabilityResult.Failure(
                AdapterErrorMapper.permissionDenied(
                    permission = securityException.message ?: "unknown",
                    diagnostics = mapOf("adapter" to adapterName, "operation" to operation),
                ),
            )
        } catch (illegalArgumentException: IllegalArgumentException) {
            CapabilityResult.Failure(
                AdapterErrorMapper.invalidParameters(
                    adapter = adapterName,
                    operation = operation,
                    detail = illegalArgumentException.message ?: "invalid",
                ),
            )
        } catch (timeout: AccessibilityOperationTimeoutException) {
            CapabilityResult.Failure(AdapterErrorMapper.accessibilityTimeout(timeout.operation))
        } catch (throwable: Throwable) {
            logger.error(
                module = RuntimeModule.ANDROID_ADAPTER.name,
                message = "$adapterName.$operation failed",
                traceId = traceId,
                throwable = throwable,
                metadata = mapOf("adapter" to adapterName, "operation" to operation),
            )
            CapabilityResult.Failure(
                AdapterErrorMapper.platformFailure(
                    adapter = adapterName,
                    operation = operation,
                    cause = throwable,
                ),
            )
        }
    }
}
