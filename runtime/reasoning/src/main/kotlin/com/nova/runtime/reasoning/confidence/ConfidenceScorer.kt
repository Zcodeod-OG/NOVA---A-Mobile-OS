package com.nova.runtime.reasoning.confidence

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.model.AmbiguityResolutionResult
import com.nova.runtime.reasoning.model.ConfidenceFactors
import com.nova.runtime.reasoning.model.EvidenceItem
import kotlin.math.round

interface ConfidenceScorer {
    fun score(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        resolution: AmbiguityResolutionResult,
    ): ConfidenceFactors
}

/**
 * Explainable confidence scoring from NIR, evidence, and ambiguity signals.
 */
class DefaultConfidenceScorer : ConfidenceScorer {
    override fun score(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        resolution: AmbiguityResolutionResult,
    ): ConfidenceFactors {
        val nirConfidence = nir.confidence.coerceIn(0.0, 1.0)
        val evidenceQuality = computeEvidenceQuality(rankedEvidence)
        val ambiguityPenalty = computeAmbiguityPenalty(resolution)
        val constraintVerification = computeConstraintVerification(nir, rankedEvidence)

        val rawOverall =
            NIR_WEIGHT * nirConfidence +
                EVIDENCE_WEIGHT * evidenceQuality +
                CONSTRAINT_WEIGHT * constraintVerification -
                AMBIGUITY_WEIGHT * ambiguityPenalty

        return ConfidenceFactors(
            nirConfidence = roundScore(nirConfidence),
            evidenceQuality = roundScore(evidenceQuality),
            ambiguityPenalty = roundScore(ambiguityPenalty),
            constraintVerification = roundScore(constraintVerification),
            overall = roundScore(rawOverall.coerceIn(0.0, 1.0)),
        )
    }

    private fun computeEvidenceQuality(rankedEvidence: List<EvidenceItem>): Double {
        if (rankedEvidence.isEmpty()) return 0.0
        return rankedEvidence
            .take(TOP_EVIDENCE_COUNT)
            .map { it.compositeScore }
            .average()
    }

    private fun computeAmbiguityPenalty(resolution: AmbiguityResolutionResult): Double {
        val ambiguousCount = resolution.resolutions.count { it.candidates.size > 1 }
        if (ambiguousCount == 0) return 0.0
        return (ambiguousCount.toDouble() / resolution.resolutions.size.coerceAtLeast(1))
            .coerceIn(0.0, 1.0)
    }

    private fun computeConstraintVerification(nir: Nir, rankedEvidence: List<EvidenceItem>): Double {
        if (nir.constraints.isEmpty()) return 1.0
        if (rankedEvidence.isEmpty()) return 0.0

        val verified = nir.constraints.count { (_, value) ->
            rankedEvidence.any { evidence ->
                evidence.content.lowercase().contains(value.lowercase())
            }
        }
        return verified.toDouble() / nir.constraints.size
    }

    private fun roundScore(value: Double): Double =
        round(value * SCORE_PRECISION) / SCORE_PRECISION

    companion object {
        private const val NIR_WEIGHT = 0.30
        private const val EVIDENCE_WEIGHT = 0.35
        private const val CONSTRAINT_WEIGHT = 0.15
        private const val AMBIGUITY_WEIGHT = 0.20
        private const val TOP_EVIDENCE_COUNT = 3
        private const val SCORE_PRECISION = 10_000.0
    }
}
