package com.nova.runtime.kernel.config

import com.nova.runtime.error.NovaError
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.utils.logging.LogLevel

interface ConfigurationValidator {
    fun validate(configuration: RuntimeConfiguration): List<NovaError>
    fun validateEntry(key: String, value: String): NovaError?
}

/**
 * Validates known kernel configuration keys at bootstrap.
 */
class DefaultConfigurationValidator : ConfigurationValidator {

    override fun validate(configuration: RuntimeConfiguration): List<NovaError> =
        configuration.values.mapNotNull { (key, value) -> validateEntry(key, value) }

    override fun validateEntry(key: String, value: String): NovaError? = when (key) {
        KernelConfigKeys.LOG_LEVEL -> validateLogLevel(value)
        KernelConfigKeys.EVENT_BUS_ASYNC -> validateBoolean(value, key)
        KernelConfigKeys.RUNTIME_VERSION -> validateNonBlank(value, key)
        else -> null
    }

    private fun validateLogLevel(value: String): NovaError? =
        if (runCatching { LogLevel.valueOf(value) }.isSuccess) {
            null
        } else {
            NovaErrors.configurationInvalid(
                key = KernelConfigKeys.LOG_LEVEL,
                reason = "Must be one of ${LogLevel.entries.joinToString()}",
            )
        }

    private fun validateBoolean(value: String, key: String): NovaError? =
        if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
            null
        } else {
            NovaErrors.configurationInvalid(key = key, reason = "Must be true or false")
        }

    private fun validateNonBlank(value: String, key: String): NovaError? =
        if (value.isNotBlank()) {
            null
        } else {
            NovaErrors.configurationInvalid(key = key, reason = "Must not be blank")
        }
}
