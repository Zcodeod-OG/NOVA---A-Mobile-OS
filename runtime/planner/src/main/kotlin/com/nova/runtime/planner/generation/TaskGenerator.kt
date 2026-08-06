package com.nova.runtime.planner.generation

import com.nova.runtime.models.Nir
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.planner.model.PlanTask
import com.nova.runtime.planner.model.SubGoal

interface TaskGenerator {
    fun generate(
        subGoals: List<SubGoal>,
        nir: Nir,
        reasoningContext: ReasoningContext,
    ): List<PlanTask>
}

/**
 * Generates atomic tasks from decomposed sub-goals — MSP §8 pipeline.
 */
class DefaultTaskGenerator : TaskGenerator {
    override fun generate(
        subGoals: List<SubGoal>,
        nir: Nir,
        reasoningContext: ReasoningContext,
    ): List<PlanTask> {
        val tasks = mutableListOf<PlanTask>()
        var order = 0

        for (subGoal in subGoals.sortedBy { it.sortOrder }) {
            when {
                subGoal.key == "validate_assumptions" -> {
                    reasoningContext.assumptions.forEachIndexed { index, assumption ->
                        tasks += PlanTask(
                            key = "check_assumption:$index",
                            subGoalKey = subGoal.key,
                            actionType = "validate_assumption",
                            inputs = mapOf("assumption" to assumption),
                            sortOrder = order++,
                        )
                    }
                }
                subGoal.key.startsWith("apply_constraint:") -> {
                    val constraintKey = subGoal.key.removePrefix("apply_constraint:")
                    tasks += PlanTask(
                        key = "enforce_constraint:$constraintKey",
                        subGoalKey = subGoal.key,
                        actionType = "enforce_constraint",
                        inputs = mapOf(
                            "constraint" to constraintKey,
                            "value" to (nir.constraints[constraintKey] ?: ""),
                        ),
                        sortOrder = order++,
                    )
                }
                subGoal.key.startsWith("execute_capability:") -> {
                    val capability = subGoal.capability ?: subGoal.key.removePrefix("execute_capability:")
                    tasks += PlanTask(
                        key = "prepare:$capability",
                        subGoalKey = subGoal.key,
                        actionType = "prepare_capability",
                        inputs = buildCapabilityInputs(capability, nir, reasoningContext),
                        sortOrder = order++,
                    )
                    tasks += PlanTask(
                        key = "execute:$capability",
                        subGoalKey = subGoal.key,
                        actionType = "execute_capability",
                        inputs = buildCapabilityInputs(capability, nir, reasoningContext),
                        sortOrder = order++,
                    )
                }
                subGoal.key == "execute_goal" -> {
                    tasks += PlanTask(
                        key = "execute_goal",
                        subGoalKey = subGoal.key,
                        actionType = "execute_goal",
                        inputs = mapOf(
                            "goal" to nir.goal,
                            "confidence" to reasoningContext.confidence.toString(),
                        ),
                        sortOrder = order++,
                    )
                }
                subGoal.key == "complete_goal" -> {
                    tasks += PlanTask(
                        key = "complete_goal",
                        subGoalKey = subGoal.key,
                        actionType = "complete_goal",
                        inputs = mapOf("goal" to nir.goal),
                        sortOrder = order++,
                    )
                }
            }
        }

        return tasks
    }

    private fun buildCapabilityInputs(
        capability: String,
        nir: Nir,
        reasoningContext: ReasoningContext,
    ): Map<String, String> = buildMap {
        put("capability", capability)
        put("goal", nir.goal)
        put("capabilityType", nir.constraints["capabilityType"] ?: capability)
        put("operation", nir.constraints["operation"] ?: "execute")
        nir.constraints["capabilityOperation"]?.let { put("capabilityOperation", it) }
        nir.constraints["channel"]?.let { put("channel", it) }
        nir.constraints["recipient"]?.let { put("recipient", it) }
        nir.constraints["fileName"]?.let { put("fileName", it) }
        nir.context["rawPayload"]?.let { put("query", it) }
        if (nir.entities.isNotEmpty()) {
            put("entities", nir.entities.sorted().joinToString(","))
        }
        if (reasoningContext.resolvedEntities.isNotEmpty()) {
            put(
                "resolvedEntities",
                reasoningContext.resolvedEntities.entries
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}=${it.value}" },
            )
        }
    }
}
