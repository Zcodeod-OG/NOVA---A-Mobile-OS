package com.nova.runtime.planner

import com.nova.runtime.models.Nag
import com.nova.runtime.models.contracts.PlanningRequest
import com.nova.runtime.models.contracts.PlanningResult

interface PlanningService {
    suspend fun buildGraph(request: PlanningRequest): PlanningResult
    suspend fun validateGraph(graph: Nag): PlanningResult
    suspend fun estimateCost(graph: Nag): Long
}
