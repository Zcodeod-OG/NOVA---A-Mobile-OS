package com.nova.runtime.planner.optimization

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.planner.validation.DefaultGraphValidator
import com.nova.runtime.planner.validation.GraphValidator
import java.util.UUID

data class OptimizationResult(
    val graph: Nag,
    val nodesMerged: Int,
    val nodesPruned: Int,
    val topologicalOrder: List<UUID>,
)

interface GraphOptimizer {
    fun optimize(graph: Nag): OptimizationResult
}

/**
 * Merge redundant nodes, prune unreachable nodes, and compute topological order.
 */
class DefaultGraphOptimizer(
    private val validator: GraphValidator = DefaultGraphValidator(),
) : GraphOptimizer {
    override fun optimize(graph: Nag): OptimizationResult {
        val merged = mergeRedundantNodes(graph)
        val pruned = pruneUnreachableNodes(merged.graph)
        val validation = validator.validate(pruned)
        val order = validation.topologicalOrder

        val metadata = pruned.metadata + mapOf(
            "topologicalOrder" to order.joinToString(",") { it.toString() },
        )

        return OptimizationResult(
            graph = pruned.copy(metadata = metadata),
            nodesMerged = merged.nodesMerged,
            nodesPruned = pruned.let { merged.graph.actionNodes.size - it.actionNodes.size },
            topologicalOrder = order,
        )
    }

    private fun mergeRedundantNodes(graph: Nag): MergePassResult {
        val groups = graph.actionNodes.groupBy { node ->
            "${node.actionType}:${node.inputs.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }}"
        }

        val redirect = mutableMapOf<UUID, UUID>()
        var nodesMerged = 0

        for ((_, nodes) in groups.entries.sortedBy { it.key }) {
            val canonical = nodes.minByOrNull { it.id.toString() } ?: continue
            for (node in nodes) {
                if (node.id != canonical.id) {
                    redirect[node.id] = canonical.id
                    nodesMerged++
                }
            }
        }

        if (redirect.isEmpty()) {
            return MergePassResult(graph, 0)
        }

        fun resolve(id: UUID): UUID {
            var current = id
            while (redirect[current] != null) {
                current = redirect.getValue(current)
            }
            return current
        }

        val canonicalIds = graph.actionNodes.map { resolve(it.id) }.toSet()
        val mergedNodes = canonicalIds.map { canonicalId ->
            val groupNodes = graph.actionNodes.filter { resolve(it.id) == canonicalId }
            val representative = groupNodes.minBy { it.id.toString() }
            val mergedDependencies = groupNodes
                .flatMap { it.dependencies }
                .map { resolve(it) }
                .filter { it != canonicalId && it in canonicalIds }
                .distinct()
                .sortedBy { it.toString() }
            representative.copy(dependencies = mergedDependencies)
        }

        val adjacency = mergedNodes.associate { it.id to it.dependencies }
        return MergePassResult(
            graph = graph.copy(actionNodes = mergedNodes, dependencies = adjacency),
            nodesMerged = nodesMerged,
        )
    }

    private fun pruneUnreachableNodes(graph: Nag): Nag {
        if (graph.actionNodes.isEmpty()) return graph

        val nodeIds = graph.actionNodes.map { it.id }.toSet()
        val nodeById = graph.actionNodes.associateBy { it.id }
        val completeNodes = graph.actionNodes.filter { it.actionType == "complete_goal" }
        val startNodes = if (completeNodes.isNotEmpty()) {
            completeNodes.map { it.id }
        } else {
            graph.actionNodes.map { it.id }
        }

        val reachable = mutableSetOf<UUID>()
        fun collectPredecessors(nodeId: UUID) {
            if (!reachable.add(nodeId)) return
            nodeById[nodeId]?.dependencies?.forEach { dependency ->
                if (dependency in nodeIds) {
                    collectPredecessors(dependency)
                }
            }
        }
        startNodes.forEach(::collectPredecessors)

        val prunedNodes = graph.actionNodes.filter { it.id in reachable }
        val reachableIds = prunedNodes.map { it.id }.toSet()
        val adjacency = prunedNodes.associate { node ->
            node.id to node.dependencies.filter { it in reachableIds }.sortedBy { it.toString() }
        }

        return graph.copy(
            actionNodes = prunedNodes,
            dependencies = adjacency,
        )
    }

    private data class MergePassResult(
        val graph: Nag,
        val nodesMerged: Int,
    )
}
