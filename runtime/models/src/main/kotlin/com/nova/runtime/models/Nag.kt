package com.nova.runtime.models

import java.util.UUID

/** NOVA Action Graph — IAS §3, TDD §12 */
data class Nag(
    val graphId: UUID,
    val metadata: Map<String, String>,
    val taskHierarchy: List<String>,
    val actionNodes: List<ActionNode>,
    val dependencies: Map<UUID, List<UUID>>,
    val executionPolicies: Map<String, String>,
)

data class ActionNode(
    val id: UUID,
    val actionType: String,
    val inputs: Map<String, String>,
    val outputs: Map<String, String>,
    val dependencies: List<UUID>,
    val timeoutMs: Long,
    val retryPolicy: String,
    val rollbackPolicy: String,
    val executionPriority: Priority,
)
