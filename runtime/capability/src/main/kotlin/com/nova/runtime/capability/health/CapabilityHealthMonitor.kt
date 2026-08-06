package com.nova.runtime.capability.health

import com.nova.runtime.capability.lifecycle.CapabilityLifecycleManager
import com.nova.runtime.capability.model.CapabilityHealthRecord
import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.registry.CapabilityRegistry
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface CapabilityHealthMonitor {
    suspend fun checkHealth(name: String, version: String): Boolean
    suspend fun recordHeartbeat(name: String, version: String)
    suspend fun recordFailure(name: String, version: String, error: String)
    fun getHealth(name: String, version: String): CapabilityHealthRecord?
    suspend fun runHealthChecks(): Map<String, CapabilityHealthRecord>
}

class DefaultCapabilityHealthMonitor(
    private val registry: CapabilityRegistry,
    private val lifecycleManager: CapabilityLifecycleManager,
    private val failureThreshold: Int = 3,
) : CapabilityHealthMonitor {

    private val healthRecords = ConcurrentHashMap<String, CapabilityHealthRecord>()

    override suspend fun checkHealth(name: String, version: String): Boolean {
        val key = capabilityKey(name, version)
        val registration = registry.lookup(name, version) ?: return false
        if (lifecycleManager.getState(name, version) != CapabilityLifecycleState.ACTIVE) {
            return false
        }
        val providerHealthy = registration.provider.health()
        if (providerHealthy) {
            recordHeartbeat(name, version)
        } else {
            recordFailure(name, version, "Provider health check returned false")
        }
        return providerHealthy
    }

    override suspend fun recordHeartbeat(name: String, version: String) {
        val key = capabilityKey(name, version)
        healthRecords[key] = CapabilityHealthRecord(
            capabilityKey = key,
            healthy = true,
            lastHeartbeatAt = Instant.now(),
            consecutiveFailures = 0,
            lastError = null,
        )
    }

    override suspend fun recordFailure(name: String, version: String, error: String) {
        val key = capabilityKey(name, version)
        val current = healthRecords[key]
        val failures = (current?.consecutiveFailures ?: 0) + 1
        healthRecords[key] = CapabilityHealthRecord(
            capabilityKey = key,
            healthy = failures < failureThreshold,
            lastHeartbeatAt = current?.lastHeartbeatAt,
            consecutiveFailures = failures,
            lastError = error,
        )
        if (failures >= failureThreshold) {
            lifecycleManager.markUnhealthy(name, version)
        }
    }

    override fun getHealth(name: String, version: String): CapabilityHealthRecord? =
        healthRecords[capabilityKey(name, version)]

    override suspend fun runHealthChecks(): Map<String, CapabilityHealthRecord> {
        registry.all()
            .filter { it.state == CapabilityLifecycleState.ACTIVE }
            .forEach { registration ->
                checkHealth(registration.metadata.name, registration.metadata.version)
            }
        return healthRecords.toMap()
    }

    private fun capabilityKey(name: String, version: String): String = "$name:$version"
}
