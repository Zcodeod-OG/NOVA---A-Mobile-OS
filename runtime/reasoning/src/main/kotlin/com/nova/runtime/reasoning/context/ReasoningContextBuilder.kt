package com.nova.runtime.reasoning.context

import com.nova.runtime.models.Nir
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.reasoning.model.AmbiguityResolutionResult
import com.nova.runtime.reasoning.model.ConfidenceFactors
import com.nova.runtime.reasoning.model.EvidenceItem

interface ReasoningContextBuilder {
    fun build(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        resolution: AmbiguityResolutionResult,
        confidence: ConfidenceFactors,
    ): ReasoningContext
}

class DefaultReasoningContextBuilder : ReasoningContextBuilder {
    override fun build(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        resolution: AmbiguityResolutionResult,
        confidence: ConfidenceFactors,
    ): ReasoningContext {
        val evidence = rankedEvidence.map { item ->
            "${item.source}:${item.content} (score=${item.compositeScore})"
        }

        val recommendations = buildList {
            if (rankedEvidence.isNotEmpty()) {
                add("Prioritize evidence from '${rankedEvidence.first().source}' for goal '${nir.goal}'")
            }
            if (nir.requiredCapabilities.isNotEmpty()) {
                add("Required capabilities: ${nir.requiredCapabilities.joinToString(", ")}")
            }
            if (resolution.assumptions.isNotEmpty()) {
                add("Review ${resolution.assumptions.size} assumption(s) before planning")
            }
        }

        return ReasoningContext(
            resolvedEntities = resolution.resolvedEntities,
            evidence = evidence,
            assumptions = resolution.assumptions,
            recommendations = recommendations,
            confidence = confidence.overall,
        )
    }
}
