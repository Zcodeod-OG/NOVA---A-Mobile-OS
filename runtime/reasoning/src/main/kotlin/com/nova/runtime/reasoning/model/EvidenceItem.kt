package com.nova.runtime.reasoning.model

/** Ranked memory evidence used during deterministic reasoning. */
data class EvidenceItem(
    val id: String,
    val source: String,
    val content: String,
    val relevanceScore: Double,
    val recencyScore: Double,
    val confidenceScore: Double,
    val compositeScore: Double,
)
