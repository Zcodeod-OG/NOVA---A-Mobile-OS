package com.nova.runtime.execution.retry

/** Retry failed actions with deterministic backoff policy. */
interface RetryManager {
    fun maxAttempts(retryPolicy: String): Int
    fun shouldRetry(retryPolicy: String, attempt: Int): Boolean
    fun backoffDelayMs(retryPolicy: String, attempt: Int): Long
}

class DefaultRetryManager(
    private val defaultMaxRetries: Int = 3,
    private val defaultBackoffMs: Long = 100L,
) : RetryManager {

    override fun maxAttempts(retryPolicy: String): Int {
        val parsed = parsePolicy(retryPolicy)
        return if (parsed == null) 1 else parsed.maxRetries + 1
    }

    override fun shouldRetry(retryPolicy: String, attempt: Int): Boolean {
        val parsed = parsePolicy(retryPolicy) ?: return false
        return attempt < parsed.maxRetries
    }

    override fun backoffDelayMs(retryPolicy: String, attempt: Int): Long {
        val parsed = parsePolicy(retryPolicy) ?: return 0L
        val oneBasedAttempt = attempt + 1
        return when (parsed.strategy) {
            RetryStrategy.NONE -> 0L
            RetryStrategy.FIXED -> parsed.baseDelayMs
            RetryStrategy.EXPONENTIAL -> {
                val multiplier = parsed.multiplier ?: 2.0
                (parsed.baseDelayMs * Math.pow(multiplier, (oneBasedAttempt - 1).toDouble())).toLong()
            }
        }
    }

    private fun parsePolicy(retryPolicy: String): ParsedRetryPolicy? {
        val normalized = retryPolicy.trim().lowercase()
        if (normalized.isEmpty() || normalized == "none") {
            return null
        }

        val parts = normalized.split(":")
        return when (parts.firstOrNull()) {
            "fixed", "linear" -> ParsedRetryPolicy(
                strategy = RetryStrategy.FIXED,
                maxRetries = parts.getOrNull(1)?.toIntOrNull() ?: defaultMaxRetries,
                baseDelayMs = parts.getOrNull(2)?.toLongOrNull() ?: defaultBackoffMs,
            )
            "exponential" -> ParsedRetryPolicy(
                strategy = RetryStrategy.EXPONENTIAL,
                maxRetries = parts.getOrNull(1)?.toIntOrNull() ?: defaultMaxRetries,
                baseDelayMs = parts.getOrNull(2)?.toLongOrNull() ?: defaultBackoffMs,
                multiplier = parts.getOrNull(3)?.toDoubleOrNull() ?: 2.0,
            )
            else -> null
        }
    }

    private enum class RetryStrategy {
        NONE,
        FIXED,
        EXPONENTIAL,
    }

    private data class ParsedRetryPolicy(
        val strategy: RetryStrategy,
        val maxRetries: Int,
        val baseDelayMs: Long,
        val multiplier: Double? = null,
    )
}
