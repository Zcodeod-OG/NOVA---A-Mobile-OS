package com.nova.runtime.understanding.normalization

import com.nova.runtime.models.Observation

/** Canonical observation form after normalization — TDD §6 stage 1. */
data class NormalizedObservation(
    val observation: Observation,
    val normalizedPayload: String,
    val tokens: List<String>,
    val metadata: Map<String, String>,
)
