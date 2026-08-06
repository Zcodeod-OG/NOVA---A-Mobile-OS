package com.nova.runtime.reasoning.model

data class AmbiguityCandidate(
    val entity: String,
    val candidates: List<String>,
    val selected: String?,
    val reason: String,
)

data class AmbiguityResolutionResult(
    val resolvedEntities: Map<String, String>,
    val resolutions: List<AmbiguityCandidate>,
    val assumptions: List<String>,
) {
    val resolvedCount: Int get() = resolutions.count { it.selected != null }
    val assumptionCount: Int get() = assumptions.size
}
