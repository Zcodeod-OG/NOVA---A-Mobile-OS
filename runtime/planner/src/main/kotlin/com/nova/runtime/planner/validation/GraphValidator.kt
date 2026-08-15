package com.nova.runtime.planner.validation

import com.nova.runtime.models.Nag

data class GraphValidationResult(
    val isValid: Boolean,
    val isAcyclic: Boolean,
    val cyclesDetected: Int,
    val cycleEdges: List<Pair<java.util.UUID, java.util.UUID>>,
    val topologicalOrder: List<java.util.UUID>,
    val depth: Int,
    val errors: List<String>,
)

interface GraphValidator {
    fun validate(graph: Nag): GraphValidationResult
}

/**
 * Validates NAG structure and acyclicity — TDD §12.
 */
class DefaultGraphValidator : GraphValidator {
    override fun validate(graph: Nag): GraphValidationResult {
        val errors = mutableListOf<String>()
        val nodeIds = graph.actionNodes.map { it.id }.toSet()

        if (graph.actionNodes.isEmpty()) {
            errors += "Graph must contain at least one action node"
        }

        val duplicateIds = graph.actionNodes.groupBy { it.id }.filter { it.value.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            errors += "Duplicate action node IDs: ${duplicateIds.joinToString()}"
        }

        val missingDependencyTargets = mutableListOf<java.util.UUID>()
        for (node in graph.actionNodes) {
            for (dependency in node.dependencies) {
                if (dependency !in nodeIds) {
                    missingDependencyTargets += dependency
                }
            }
        }
        if (missingDependencyTargets.isNotEmpty()) {
            errors += "Missing dependency targets: ${missingDependencyTargets.distinct().joinToString()}"
        }

        val adjacency = buildAdjacency(graph)
        val cycleEdges = detectCycleEdges(adjacency)
        val isAcyclic = cycleEdges.isEmpty()
        val topologicalOrder = if (isAcyclic) topologicalSort(adjacency) else emptyList()
        val depth = if (isAcyclic) computeDepth(adjacency, topologicalOrder) else 0

        if (!isAcyclic) {
            errors += "Graph contains ${cycleEdges.size} cycle edge(s)"
        }

        return GraphValidationResult(
            isValid = errors.isEmpty() && isAcyclic,
            isAcyclic = isAcyclic,
            cyclesDetected = if (isAcyclic) 0 else cycleEdges.map { it.first }.distinct().size,
            cycleEdges = cycleEdges,
            topologicalOrder = topologicalOrder,
            depth = depth,
            errors = errors,
        )
    }

    private fun buildAdjacency(graph: Nag): Map<java.util.UUID, List<java.util.UUID>> =
        graph.actionNodes.associate { node ->
            node.id to node.dependencies.sorted()
        }

    private fun detectCycleEdges(
        adjacency: Map<java.util.UUID, List<java.util.UUID>>,
    ): List<Pair<java.util.UUID, java.util.UUID>> {
        val cycleEdges = mutableListOf<Pair<java.util.UUID, java.util.UUID>>()
        val visiting = mutableSetOf<java.util.UUID>()
        val visited = mutableSetOf<java.util.UUID>()

        fun dfs(node: java.util.UUID, path: MutableList<java.util.UUID>) {
            if (node in visiting) {
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0 && cycleStart + 1 < path.size) {
                    for (index in cycleStart until path.lastIndex) {
                        cycleEdges += path[index] to path[index + 1]
                    }
                    cycleEdges += path.last() to node
                }
                return
            }
            if (node in visited) return

            visiting.add(node)
            path.add(node)
            for (dependency in adjacency[node].orEmpty()) {
                dfs(dependency, path)
            }
            path.removeAt(path.lastIndex)
            visiting.remove(node)
            visited.add(node)
        }

        adjacency.keys.sortedBy { it.toString() }.forEach { node ->
            if (node !in visited) {
                dfs(node, mutableListOf())
            }
        }

        return cycleEdges.distinct().sortedWith(compareBy({ it.first.toString() }, { it.second.toString() }))
    }

    private fun topologicalSort(adjacency: Map<java.util.UUID, List<java.util.UUID>>): List<java.util.UUID> {
        val inDegree = adjacency.keys.associateWith { node -> adjacency[node]?.size ?: 0 }.toMutableMap()
        val dependents = mutableMapOf<java.util.UUID, MutableList<java.util.UUID>>()
        adjacency.forEach { (node, deps) ->
            deps.forEach { dep ->
                dependents.getOrPut(dep) { mutableListOf() }.add(node)
            }
        }

        val queue = inDegree.entries
            .filter { it.value == 0 }
            .map { it.key }
            .sortedBy { it.toString() }
            .toMutableList()

        val order = mutableListOf<java.util.UUID>()
        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            order += node
            dependents[node]?.sortedBy { it.toString() }?.forEach { dependent ->
                val updated = inDegree.getValue(dependent) - 1
                inDegree[dependent] = updated
                if (updated == 0) {
                    queue += dependent
                    queue.sortBy { it.toString() }
                }
            }
        }

        return order
    }

    private fun computeDepth(
        adjacency: Map<java.util.UUID, List<java.util.UUID>>,
        topologicalOrder: List<java.util.UUID>,
    ): Int {
        if (topologicalOrder.isEmpty()) return 0
        val depthByNode = adjacency.keys.associateWith { 0 }.toMutableMap()
        for (node in topologicalOrder) {
            val nodeDepth = depthByNode.getValue(node)
            for ((candidate, deps) in adjacency) {
                if (node in deps) {
                    depthByNode[candidate] = maxOf(depthByNode.getValue(candidate), nodeDepth + 1)
                }
            }
        }
        return depthByNode.values.maxOrNull() ?: 0
    }
}
