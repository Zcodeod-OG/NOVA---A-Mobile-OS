package com.nova.runtime.models

import java.time.Instant
import java.util.UUID

/** Canonical external input — IAS §3 */
data class Observation(
    val id: UUID,
    val timestamp: Instant,
    val sessionId: UUID,
    val traceId: UUID,
    val modality: Modality,
    val payload: String,
    val metadata: Map<String, String> = emptyMap(),
)
