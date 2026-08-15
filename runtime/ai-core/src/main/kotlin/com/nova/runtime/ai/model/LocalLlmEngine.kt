package com.nova.runtime.ai.model

/**
 * On-device generative engine for grounded document answers.
 * Implementations must never invent facts outside the provided context.
 */
interface LocalLlmEngine {
    /** True when a generative model file is loaded/available locally. */
    fun isAvailable(): Boolean

    /**
     * Generate a short answer. Return null on failure so callers can fall back
     * to extractive formatting.
     */
    suspend fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String?

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}

/** No-op engine used in unit tests and when the mobile LLM is not installed. */
object UnavailableLocalLlmEngine : LocalLlmEngine {
    override fun isAvailable(): Boolean = false

    override suspend fun generate(prompt: String, maxTokens: Int): String? = null
}
