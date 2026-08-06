package com.nova.runtime.kernel.config

import com.nova.runtime.error.NovaException
import com.nova.runtime.error.NovaErrors
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime configuration snapshot. Values are loaded at startup and treated as read-only
 * during normal operation unless explicitly updated through [ConfigurationManager].
 */
data class RuntimeConfiguration(
    val values: Map<String, String> = emptyMap(),
) {
    fun getString(key: String, default: String? = null): String? = values[key] ?: default

    fun requireString(key: String): String =
        values[key] ?: throw NovaException(NovaErrors.configurationMissing(key))
}

interface ConfigurationManager {
    fun load(): RuntimeConfiguration
    fun get(key: String): String?
    fun require(key: String): String
    fun set(key: String, value: String)
    fun snapshot(): RuntimeConfiguration
}

/**
 * In-memory configuration manager for MVP. Persistent storage deferred to Data Platform sprint.
 */
class InMemoryConfigurationManager(
    initial: Map<String, String> = emptyMap(),
) : ConfigurationManager {

    private val values = ConcurrentHashMap(initial)

    override fun load(): RuntimeConfiguration = snapshot()

    override fun get(key: String): String? = values[key]

    override fun require(key: String): String =
        values[key] ?: throw NovaException(NovaErrors.configurationMissing(key))

    override fun set(key: String, value: String) {
        values[key] = value
    }

    override fun snapshot(): RuntimeConfiguration = RuntimeConfiguration(values.toMap())
}

object KernelConfigKeys {
    const val RUNTIME_VERSION = "runtime.version"
    const val LOG_LEVEL = "runtime.log.level"
    const val EVENT_BUS_ASYNC = "runtime.eventbus.async"
}
