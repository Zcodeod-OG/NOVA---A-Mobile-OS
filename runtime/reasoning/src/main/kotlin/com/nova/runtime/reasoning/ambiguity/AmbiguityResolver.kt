package com.nova.runtime.reasoning.ambiguity

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.model.AmbiguityCandidate
import com.nova.runtime.reasoning.model.AmbiguityResolutionResult
import com.nova.runtime.reasoning.model.EvidenceItem

interface AmbiguityResolver {
    fun resolve(nir: Nir, rankedEvidence: List<EvidenceItem>): AmbiguityResolutionResult
}

/**
 * Deterministic ambiguity resolution using ranked memory evidence — TDD §10.
 */
class DefaultAmbiguityResolver : AmbiguityResolver {
    override fun resolve(nir: Nir, rankedEvidence: List<EvidenceItem>): AmbiguityResolutionResult {
        val resolved = linkedMapOf<String, String>()
        val resolutions = mutableListOf<AmbiguityCandidate>()
        val assumptions = mutableListOf<String>()

        nir.entities.forEachIndexed { index, entity ->
            val entityKey = entityKey(index, entity)
            val candidates = findCandidates(entity, rankedEvidence)

            when {
                candidates.isEmpty() -> {
                    resolved[entityKey] = entity
                    resolutions += AmbiguityCandidate(
                        entity = entity,
                        candidates = listOf(entity),
                        selected = entity,
                        reason = "No memory match; retained NIR entity value",
                    )
                }
                candidates.size == 1 -> {
                    val selected = candidates.first()
                    resolved[entityKey] = selected
                    resolutions += AmbiguityCandidate(
                        entity = entity,
                        candidates = candidates,
                        selected = selected,
                        reason = "Single memory match",
                    )
                }
                else -> {
                    val selected = selectCandidate(entity, candidates, rankedEvidence)
                    resolved[entityKey] = selected
                    resolutions += AmbiguityCandidate(
                        entity = entity,
                        candidates = candidates,
                        selected = selected,
                        reason = "Resolved via highest-ranked evidence",
                    )
                    if (selected != entity) {
                        assumptions += "Ambiguous entity '$entity' resolved to '$selected'"
                    }
                }
            }
        }

        verifyConstraints(nir, rankedEvidence, assumptions)

        return AmbiguityResolutionResult(
            resolvedEntities = resolved,
            resolutions = resolutions,
            assumptions = assumptions.distinct(),
        )
    }

    private fun findCandidates(entity: String, rankedEvidence: List<EvidenceItem>): List<String> {
        val normalizedEntity = entity.lowercase()
        val matches = rankedEvidence.mapNotNull { evidence ->
            when {
                evidence.content.lowercase().contains(normalizedEntity) -> evidence.content
                normalizedEntity.isNotBlank() &&
                    tokenOverlap(normalizedEntity, evidence.content) >= MIN_TOKEN_OVERLAP ->
                    evidence.content
                else -> null
            }
        }.distinct()

        if (matches.isNotEmpty()) return matches
        if (entity.isNotBlank()) return listOf(entity)
        return emptyList()
    }

    private fun selectCandidate(
        entity: String,
        candidates: List<String>,
        rankedEvidence: List<EvidenceItem>,
    ): String {
        val evidenceByContent = rankedEvidence.associateBy { it.content.lowercase() }
        return candidates
            .sortedWith(
                compareByDescending<String> { candidate ->
                    evidenceByContent[candidate.lowercase()]?.compositeScore ?: 0.0
                }.thenBy { it.lowercase() },
            )
            .firstOrNull()
            ?: entity
    }

    private fun verifyConstraints(
        nir: Nir,
        rankedEvidence: List<EvidenceItem>,
        assumptions: MutableList<String>,
    ) {
        nir.constraints.forEach { (key, value) ->
            val verified = rankedEvidence.any { evidence ->
                evidence.content.lowercase().contains(value.lowercase()) ||
                    evidence.source.lowercase() == key.lowercase()
            }
            if (!verified && value.isNotBlank()) {
                assumptions += "Constraint '$key=$value' could not be verified against memory"
            }
        }
    }

    private fun tokenOverlap(entity: String, content: String): Int {
        val entityTokens = entity.split(TOKEN_SPLIT_REGEX).filter { it.length > 1 }.toSet()
        val contentTokens = content.lowercase().split(TOKEN_SPLIT_REGEX).filter { it.length > 1 }.toSet()
        return entityTokens.intersect(contentTokens).size
    }

    private fun entityKey(index: Int, entity: String): String =
        "entity_${index}_${entity.lowercase()}"

    companion object {
        private const val MIN_TOKEN_OVERLAP = 1
        private val TOKEN_SPLIT_REGEX = Regex("\\W+")
    }
}
