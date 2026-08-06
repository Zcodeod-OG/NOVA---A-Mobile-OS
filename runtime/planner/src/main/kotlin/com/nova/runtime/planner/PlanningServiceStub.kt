package com.nova.runtime.planner

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.PlanningRequest
import com.nova.runtime.models.contracts.PlanningResult

class PlanningServiceStub : PlanningService {
    override suspend fun buildGraph(request: PlanningRequest): PlanningResult = notImplemented()
    override suspend fun validateGraph(graph: Nag): PlanningResult = notImplemented()
    override suspend fun estimateCost(graph: Nag): Long = 0L

    private fun notImplemented(): PlanningResult.Failure = PlanningResult.Failure(
        RuntimeError(
            code = "PLANNER_NOT_IMPLEMENTED",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.LOW,
            recoverable = true,
            userVisibleMessage = "Planning service not yet implemented.",
        ),
    )
}
