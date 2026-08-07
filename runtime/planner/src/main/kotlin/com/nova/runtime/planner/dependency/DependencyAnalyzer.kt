package com.nova.runtime.planner.dependency

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.planner.model.PlanTask

interface DependencyAnalyzer {
    fun assignDependencies(graph: Nag, tasks: List<PlanTask>): Nag
}

/**
 * Infers dependencies between action nodes from task structure — TDD §11.
 */
class DefaultDependencyAnalyzer : DependencyAnalyzer {
    override fun assignDependencies(graph: Nag, tasks: List<PlanTask>): Nag {
        val sortedTasks = tasks.sortedWith(compareBy({ it.sortOrder }, { it.key }))
        val nodeByTaskKey = sortedTasks.zip(graph.actionNodes).toMap()

        val dependencyMap = mutableMapOf<String, MutableSet<String>>()
        sortedTasks.forEach { task -> dependencyMap[task.key] = mutableSetOf() }

        val assumptionTasks = sortedTasks.filter { it.actionType == "validate_assumption" }
        val constraintTasks = sortedTasks.filter { it.actionType == "enforce_constraint" }
        val prepareTasks = sortedTasks.filter { it.actionType == "prepare_capability" }
        val executeTasks = sortedTasks.filter { it.actionType == "execute_capability" }
        val completeTask = sortedTasks.firstOrNull { it.actionType == "complete_goal" }
        val goalTask = sortedTasks.firstOrNull { it.actionType == "execute_goal" }

        val prerequisiteKeys = (assumptionTasks + constraintTasks).map { it.key }

        for (task in prepareTasks + executeTasks + listOfNotNull(goalTask, completeTask)) {
            if (task.actionType == "execute_capability") {
                val capability = task.inputs["capability"] ?: continue
                val prepareKey = "prepare:$capability"
                if (prepareKey in dependencyMap) {
                    dependencyMap.getValue(task.key).add(prepareKey)
                }
            }
            if (task.key != assumptionTasks.firstOrNull()?.key) {
                prerequisiteKeys.forEach { prerequisite ->
                    if (prerequisite != task.key) {
                        dependencyMap.getValue(task.key).add(prerequisite)
                    }
                }
            }
        }

        for (prepare in prepareTasks) {
            assumptionTasks.forEach { assumption ->
                dependencyMap.getValue(prepare.key).add(assumption.key)
            }
            constraintTasks.forEach { constraint ->
                dependencyMap.getValue(prepare.key).add(constraint.key)
            }
        }

        val sortedExecuteTasks = executeTasks.sortedBy { it.sortOrder }
        for (index in 1 until sortedExecuteTasks.size) {
            dependencyMap.getValue(sortedExecuteTasks[index].key)
                .add(sortedExecuteTasks[index - 1].key)
        }

        completeTask?.let { complete ->
            val terminalTasks = (executeTasks + listOfNotNull(goalTask)).map { it.key }
            terminalTasks.forEach { terminal ->
                dependencyMap.getValue(complete.key).add(terminal)
            }
        }

        val updatedNodes = graph.actionNodes.map { node ->
            val taskKey = node.outputs["taskKey"] ?: return@map node
            val dependencyTaskKeys = dependencyMap[taskKey]?.sorted().orEmpty()
            val dependencyIds = dependencyTaskKeys.mapNotNull { key -> nodeByTaskKey[key]?.id }
            node.copy(dependencies = dependencyIds)
        }

        val adjacency = updatedNodes.associate { node ->
            node.id to node.dependencies
        }

        return graph.copy(
            actionNodes = updatedNodes,
            dependencies = adjacency,
        )
    }
}

private fun List<PlanTask>.zip(nodes: List<ActionNode>): Map<String, ActionNode> {
    require(size == nodes.size) { "Task and node lists must have equal size" }
    return mapIndexed { index, task -> task.key to nodes[index] }.toMap()
}
