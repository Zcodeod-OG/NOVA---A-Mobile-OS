package com.nova.runtime.models

data class ReasoningContext(
    val resolvedEntities: Map<String, String>,
    val evidence: List<String>,
    val assumptions: List<String>,
    val recommendations: List<String>,
    val confidence: Double,
)
