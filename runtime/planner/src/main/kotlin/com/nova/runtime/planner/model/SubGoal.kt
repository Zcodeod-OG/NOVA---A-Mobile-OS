package com.nova.runtime.planner.model

/** Decomposed unit derived from NIR goal and reasoning context. */
data class SubGoal(
    val key: String,
    val description: String,
    val capability: String?,
    val sortOrder: Int,
)
