package com.nova.runtime.planner.model

/** Metrics captured during graph generation — IAS §11 observability. */
data class PlanningMetrics(
    val nodeCount: Int,
    val depth: Int,
    val generationTimeMs: Long,
    val cyclesDetected: Int,
    val cyclesBroken: Int,
    val nodesMerged: Int,
    val nodesPruned: Int,
    val taskCount: Int,
    val subGoalCount: Int,
) {
    fun toMetadata(): Map<String, String> = mapOf(
        "nodeCount" to nodeCount.toString(),
        "depth" to depth.toString(),
        "generationTimeMs" to generationTimeMs.toString(),
        "cyclesDetected" to cyclesDetected.toString(),
        "cyclesBroken" to cyclesBroken.toString(),
        "nodesMerged" to nodesMerged.toString(),
        "nodesPruned" to nodesPruned.toString(),
        "taskCount" to taskCount.toString(),
        "subGoalCount" to subGoalCount.toString(),
    )
}
