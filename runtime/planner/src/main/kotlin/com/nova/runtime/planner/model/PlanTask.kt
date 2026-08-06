package com.nova.runtime.planner.model

/** Atomic planning task generated from a sub-goal. */
data class PlanTask(
    val key: String,
    val subGoalKey: String,
    val actionType: String,
    val inputs: Map<String, String>,
    val sortOrder: Int,
)
