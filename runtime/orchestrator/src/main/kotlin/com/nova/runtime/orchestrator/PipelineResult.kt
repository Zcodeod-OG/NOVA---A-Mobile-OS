package com.nova.runtime.orchestrator

import com.nova.runtime.models.Nag
import com.nova.runtime.models.Nir
import com.nova.runtime.models.RuntimeError
import java.util.UUID

enum class PipelineStage {
    UNDERSTANDING,
    REASONING,
    PLANNING,
    POLICY,
    EXECUTION,
}

sealed class PipelineResult {
    abstract val traceId: UUID

    data class Success(
        override val traceId: UUID,
        val nir: Nir,
        val graph: Nag,
        val capabilityOperation: String,
        val completedNodes: Int,
        val summary: String,
    ) : PipelineResult()

    data class Failure(
        override val traceId: UUID,
        val stage: PipelineStage,
        val error: RuntimeError,
        val summary: String,
    ) : PipelineResult()

    /** Calendar create (or similar) blocked until the user confirms in UI. */
    data class PendingConfirmation(
        override val traceId: UUID,
        val nir: Nir,
        val graph: Nag,
        val capabilityOperation: String,
        val summary: String,
        val confirmationPrompt: String,
    ) : PipelineResult()
}
