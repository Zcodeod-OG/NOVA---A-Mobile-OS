package com.nova.runtime.reasoning.model

data class ConfidenceFactors(
    val nirConfidence: Double,
    val evidenceQuality: Double,
    val ambiguityPenalty: Double,
    val constraintVerification: Double,
    val overall: Double,
) {
    fun explainableFactors(): Map<String, Double> = mapOf(
        "nirConfidence" to nirConfidence,
        "evidenceQuality" to evidenceQuality,
        "ambiguityPenalty" to ambiguityPenalty,
        "constraintVerification" to constraintVerification,
    )
}
