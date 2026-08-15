package com.nova.runtime.inference.metrics

import com.nova.runtime.inference.tier.InferenceTier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class InferenceMetricSnapshot(
    val totalRequests: Long,
    val successCount: Long,
    val failureCount: Long,
    val tierUsage: Map<InferenceTier, Long>,
    val averageLatencyMs: Double,
    val lastLatencyMs: Long?,
)

interface InferenceMetrics {
    fun recordStart(traceId: String): Long
    fun recordSuccess(traceId: String, tier: InferenceTier, startedAtMs: Long)
    fun recordFailure(traceId: String, tier: InferenceTier?, startedAtMs: Long)
    fun snapshot(): InferenceMetricSnapshot
}

class DefaultInferenceMetrics : InferenceMetrics {

    private val totalRequests = AtomicLong(0)
    private val successCount = AtomicLong(0)
    private val failureCount = AtomicLong(0)
    private val totalLatencyMs = AtomicLong(0)
    private val lastLatencyMs = AtomicLong(0)
    private val tierUsage = ConcurrentHashMap<InferenceTier, AtomicLong>()

    override fun recordStart(traceId: String): Long {
        totalRequests.incrementAndGet()
        return System.currentTimeMillis()
    }

    override fun recordSuccess(traceId: String, tier: InferenceTier, startedAtMs: Long) {
        successCount.incrementAndGet()
        recordLatency(startedAtMs)
        tierUsage.computeIfAbsent(tier) { AtomicLong(0) }.incrementAndGet()
    }

    override fun recordFailure(traceId: String, tier: InferenceTier?, startedAtMs: Long) {
        failureCount.incrementAndGet()
        recordLatency(startedAtMs)
        tier?.let { tierUsage.computeIfAbsent(it) { AtomicLong(0) }.incrementAndGet() }
    }

    override fun snapshot(): InferenceMetricSnapshot {
        val total = totalRequests.get()
        val successes = successCount.get()
        val latencyTotal = totalLatencyMs.get()
        val completed = successes + failureCount.get()
        return InferenceMetricSnapshot(
            totalRequests = total,
            successCount = successes,
            failureCount = failureCount.get(),
            tierUsage = tierUsage.mapValues { it.value.get() },
            averageLatencyMs = if (completed == 0L) 0.0 else latencyTotal.toDouble() / completed,
            lastLatencyMs = lastLatencyMs.get().takeIf { completed > 0 },
        )
    }

    private fun recordLatency(startedAtMs: Long) {
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0)
        totalLatencyMs.addAndGet(elapsed)
        lastLatencyMs.set(elapsed)
    }
}
