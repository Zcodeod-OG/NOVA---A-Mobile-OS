package com.nova.runtime.execution.metrics

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Latency, success/failure counts, retry stats, and throughput. */
data class ExecutionMetricsSnapshot(
    val totalNodes: Int,
    val completedNodes: Int,
    val failedNodes: Int,
    val retryCount: Int,
    val totalLatencyMs: Long,
    val throughputNodesPerSecond: Double,
)

class ExecutionMetrics {
    private val totalNodes = AtomicInteger(0)
    private val completedNodes = AtomicInteger(0)
    private val failedNodes = AtomicInteger(0)
    private val retryCount = AtomicInteger(0)
    private val totalLatencyMs = AtomicLong(0)
    private val startNanos = AtomicLong(0)

    fun beginGraph(nodeCount: Int) {
        totalNodes.set(nodeCount)
        completedNodes.set(0)
        failedNodes.set(0)
        retryCount.set(0)
        totalLatencyMs.set(0)
        startNanos.set(System.nanoTime())
    }

    fun recordNodeSuccess(latencyMs: Long) {
        completedNodes.incrementAndGet()
        totalLatencyMs.addAndGet(latencyMs)
    }

    fun recordNodeFailure(latencyMs: Long) {
        failedNodes.incrementAndGet()
        totalLatencyMs.addAndGet(latencyMs)
    }

    fun recordRetry() {
        retryCount.incrementAndGet()
    }

    fun snapshot(): ExecutionMetricsSnapshot {
        val elapsedSeconds = ((System.nanoTime() - startNanos.get()).coerceAtLeast(1L)) / 1_000_000_000.0
        val completed = completedNodes.get()
        return ExecutionMetricsSnapshot(
            totalNodes = totalNodes.get(),
            completedNodes = completed,
            failedNodes = failedNodes.get(),
            retryCount = retryCount.get(),
            totalLatencyMs = totalLatencyMs.get(),
            throughputNodesPerSecond = completed / elapsedSeconds,
        )
    }
}
