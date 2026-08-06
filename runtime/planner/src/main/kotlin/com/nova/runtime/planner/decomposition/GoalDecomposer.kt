package com.nova.runtime.planner.decomposition

import com.nova.runtime.models.Nir
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.planner.model.SubGoal

interface GoalDecomposer {
    fun decompose(nir: Nir, reasoningContext: ReasoningContext): List<SubGoal>
}

/**
 * Deterministic goal decomposition from NIR and reasoning context — TDD §11.
 */
class DefaultGoalDecomposer : GoalDecomposer {
    override fun decompose(nir: Nir, reasoningContext: ReasoningContext): List<SubGoal> {
        val subGoals = mutableListOf<SubGoal>()
        var order = 0

        if (reasoningContext.assumptions.isNotEmpty()) {
            subGoals += SubGoal(
                key = "validate_assumptions",
                description = "Validate ${reasoningContext.assumptions.size} assumption(s) before execution",
                capability = null,
                sortOrder = order++,
            )
        }

        val sortedConstraints = nir.constraints.entries.sortedBy { it.key }
        for ((key, value) in sortedConstraints) {
            subGoals += SubGoal(
                key = "apply_constraint:$key",
                description = "Apply constraint '$key' = '$value'",
                capability = null,
                sortOrder = order++,
            )
        }

        val capabilities = resolveCapabilities(nir, reasoningContext)
        for (capability in capabilities) {
            subGoals += SubGoal(
                key = "execute_capability:$capability",
                description = "Execute capability '$capability' for goal '${nir.goal}'",
                capability = capability,
                sortOrder = order++,
            )
        }

        if (subGoals.isEmpty()) {
            subGoals += SubGoal(
                key = "execute_goal",
                description = "Execute goal '${nir.goal}'",
                capability = null,
                sortOrder = order,
            )
        }

        subGoals += SubGoal(
            key = "complete_goal",
            description = "Finalize goal '${nir.goal}'",
            capability = null,
            sortOrder = order + 1,
        )

        return subGoals
    }

    private fun resolveCapabilities(nir: Nir, reasoningContext: ReasoningContext): List<String> {
        val fromNir = nir.requiredCapabilities.sorted()
        if (fromNir.isNotEmpty()) return fromNir

        val fromRecommendations = reasoningContext.recommendations
            .mapNotNull { recommendation ->
                CAPABILITY_PREFIX.find(recommendation)?.groupValues?.get(1)
            }
            .sorted()
            .distinct()

        return fromRecommendations
    }

    companion object {
        private val CAPABILITY_PREFIX = Regex("Required capabilities: (.+)")
    }
}
