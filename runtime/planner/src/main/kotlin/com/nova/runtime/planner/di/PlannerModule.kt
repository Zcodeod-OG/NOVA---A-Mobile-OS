package com.nova.runtime.planner.di

import com.nova.runtime.planner.PlanningService
import com.nova.runtime.planner.PlanningServiceImpl
import com.nova.runtime.planner.decomposition.DefaultGoalDecomposer
import com.nova.runtime.planner.decomposition.GoalDecomposer
import com.nova.runtime.planner.dependency.DefaultDependencyAnalyzer
import com.nova.runtime.planner.dependency.DependencyAnalyzer
import com.nova.runtime.planner.events.PlannerEventPublisher
import com.nova.runtime.planner.generation.DefaultTaskGenerator
import com.nova.runtime.planner.generation.TaskGenerator
import com.nova.runtime.planner.graph.ActionGraphBuilder
import com.nova.runtime.planner.graph.DefaultActionGraphBuilder
import com.nova.runtime.planner.optimization.DefaultGraphOptimizer
import com.nova.runtime.planner.optimization.GraphOptimizer
import com.nova.runtime.planner.validation.CycleBreaker
import com.nova.runtime.planner.validation.DefaultCycleBreaker
import com.nova.runtime.planner.validation.DefaultGraphValidator
import com.nova.runtime.planner.validation.GraphValidator
import org.koin.dsl.module

/** Koin DI wiring for Planning Service per MSP §8. */
val plannerModule = module {
    single<GoalDecomposer> { DefaultGoalDecomposer() }
    single<TaskGenerator> { DefaultTaskGenerator() }
    single<ActionGraphBuilder> { DefaultActionGraphBuilder() }
    single<DependencyAnalyzer> { DefaultDependencyAnalyzer() }
    single<GraphValidator> { DefaultGraphValidator() }
    single<CycleBreaker> { DefaultCycleBreaker(get()) }
    single<GraphOptimizer> { DefaultGraphOptimizer(get()) }
    single { PlannerEventPublisher(get()) }
    single<PlanningService> {
        PlanningServiceImpl(
            goalDecomposer = get(),
            taskGenerator = get(),
            actionGraphBuilder = get(),
            dependencyAnalyzer = get(),
            graphValidator = get(),
            cycleBreaker = get(),
            graphOptimizer = get(),
            eventPublisher = get(),
            logger = get(),
        )
    }
}
