package com.nova.runtime.planner.validation

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import java.util.UUID

data class CycleBreakResult(
    val graph: Nag,
    val cyclesBroken: Int,
)

interface CycleBreaker {
    fun ensureAcyclic(graph: Nag): CycleBreakResult
}

/**
 * Deterministically breaks cycles by removing the weakest dependency edge — TDD §12.
 * Weakest edge = lexicographically smallest (dependentId, dependencyId) pair.
 */
class DefaultCycleBreaker(
    private val validator: GraphValidator = DefaultGraphValidator(),
) : CycleBreaker {
    override fun ensureAcyclic(graph: Nag): CycleBreakResult {
        var current = graph
        var cyclesBroken = 0

        while (true) {
            val validation = validator.validate(current)
            if (validation.isAcyclic) {
                return CycleBreakResult(current, cyclesBroken)
            }

            val edgeToBreak = validation.cycleEdges
                .sortedWith(compareBy({ it.second.toString() }, { it.first.toString() }))
                .first()

            current = removeDependency(current, dependentId = edgeToBreak.first, dependencyId = edgeToBreak.second)
            cyclesBroken++
        }
    }

    private fun removeDependency(graph: Nag, dependentId: UUID, dependencyId: UUID): Nag {
        val updatedNodes = graph.actionNodes.map { node ->
            if (node.id == dependentId) {
                node.copy(dependencies = node.dependencies.filterNot { it == dependencyId })
            } else {
                node
            }
        }

        val updatedAdjacency = updatedNodes.associate { node ->
            node.id to node.dependencies
        }

        return graph.copy(
            actionNodes = updatedNodes,
            dependencies = updatedAdjacency,
            metadata = graph.metadata + ("cycleBreak" to "$dependentId->$dependencyId"),
        )
    }
}
