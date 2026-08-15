package com.nova.runtime.reasoning.explanation

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.model.AmbiguityResolutionResult
import com.nova.runtime.reasoning.model.ConfidenceFactors
import com.nova.runtime.reasoning.model.EvidenceItem

interface ExplanationGenerator {
    fun generate(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        resolution: AmbiguityResolutionResult,
        confidence: ConfidenceFactors,
    ): List<String>
}

/**
 * Human-readable explanations for deterministic reasoning decisions — MSP §7.
 */
class DefaultExplanationGenerator : ExplanationGenerator {
    override fun generate(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        resolution: AmbiguityResolutionResult,
        confidence: ConfidenceFactors,
    ): List<String> {
        val explanations = mutableListOf<String>()

        explanations += "Reasoned over goal '${nir.goal}' with ${rankedEvidence.size} evidence source(s)"

        rankedEvidence.take(TOP_EVIDENCE_COUNT).forEachIndexed { index, item ->
            explanations += "Evidence rank ${index + 1}: ${item.source} " +
                "(relevance=${item.relevanceScore}, recency=${item.recencyScore}, " +
                "confidence=${item.confidenceScore}, composite=${item.compositeScore})"
        }

        resolution.resolutions
            .filter { it.candidates.size > 1 }
            .forEach { candidate ->
                explanations += "Resolved ambiguous entity '${candidate.entity}' to " +
                    "'${candidate.selected}' — ${candidate.reason}"
            }

        confidence.explainableFactors().forEach { (factor, value) ->
            explanations += "Confidence factor $factor=$value"
        }
        explanations += "Overall reasoning confidence=${confidence.overall}"

        return explanations
    }

    companion object {
        private const val TOP_EVIDENCE_COUNT = 3
    }
}
