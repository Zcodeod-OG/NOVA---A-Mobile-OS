package com.nova.runtime.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.runtime.app.ui.components.ActivityItem
import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.capability.CapabilityEvents
import com.nova.runtime.events.execution.ExecutionEvents
import com.nova.runtime.events.planner.PlannerEvents
import com.nova.runtime.events.reasoning.ReasoningEvents
import com.nova.runtime.events.system.SystemEvents
import com.nova.runtime.events.understanding.UnderstandingEvents
import com.nova.runtime.models.RuntimeLifecycleState
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.orchestrator.CognitivePipelineOrchestrator
import com.nova.runtime.orchestrator.PipelineResult
import com.nova.runtime.orchestrator.PipelineStage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovaOsViewModel(
    private val orchestrator: CognitivePipelineOrchestrator,
    private val eventBus: EventBus,
) : ViewModel() {

    private val _lifecycleState = MutableStateFlow(RuntimeLifecycleState.CREATED)
    val lifecycleState: StateFlow<RuntimeLifecycleState> = _lifecycleState.asStateFlow()

    private val _activityFeed = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activityFeed: StateFlow<List<ActivityItem>> = _activityFeed.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        subscribeToRuntimeEvents()
        seedBootEvents()
    }

    fun updateLifecycleState(state: RuntimeLifecycleState) {
        _lifecycleState.value = state
    }

    fun submitCommand(command: String) {
        if (command.isBlank() || _isProcessing.value) return

        val traceId = UUID.randomUUID()
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "USER",
                message = command,
                isAlert = true,
            ),
        )
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "PIPELINE",
                message = "Processing command (trace=${traceId.toString().take(8)})…",
            ),
        )

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                when (val result = orchestrator.processUserCommand(command, traceId.toString())) {
                    is PipelineResult.Success -> {
                        prependActivity(
                            ActivityItem(
                                timestamp = now(),
                                source = "EXECUTION",
                                message = result.summary,
                                isAlert = true,
                            ),
                        )
                    }
                    is PipelineResult.Failure -> {
                        prependActivity(
                            ActivityItem(
                                timestamp = now(),
                                source = stageLabel(result.stage),
                                message = "Failed: ${result.summary}",
                                isAlert = true,
                            ),
                        )
                    }
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun subscribeToRuntimeEvents() {
        val trackedEvents = setOf(
            SystemEvents.RUNTIME_STARTED,
            SystemEvents.RUNTIME_READY,
            SystemEvents.RUNTIME_ERROR,
            UnderstandingEvents.INTENT_DETECTED,
            UnderstandingEvents.NIR_GENERATED,
            ReasoningEvents.STARTED,
            ReasoningEvents.COMPLETED,
            PlannerEvents.PLANNING_STARTED,
            PlannerEvents.GRAPH_BUILT,
            PlannerEvents.PLANNING_COMPLETED,
            ExecutionEvents.STARTED,
            ExecutionEvents.NODE_COMPLETED,
            ExecutionEvents.GRAPH_COMPLETED,
            ExecutionEvents.GRAPH_FAILED,
            CapabilityEvents.EXECUTED,
            CapabilityEvents.FAILED,
        )

        eventBus.subscribe(
            object : com.nova.runtime.events.EventSubscriber {
                override val subscriberId = "nova-os-ui"
                override val eventTypes = trackedEvents

                override suspend fun onEvent(event: RuntimeEvent) {
                    formatEvent(event)?.let { item ->
                        prependActivity(item)
                    }
                }
            },
        )
    }

    private fun seedBootEvents() {
        _activityFeed.value = listOf(
            ActivityItem(now(), "KERNEL", "System boot initiated. Loading Koin DI modules…"),
            ActivityItem(now(), "MEMORY", "Vector DB initialized (512-dim embedding engine)"),
            ActivityItem(now(), "INFERENCE", "Quantized model weight loaded: NOVA-Local-1.0"),
        )
    }

    private fun formatEvent(event: RuntimeEvent): ActivityItem? {
        val message = when (event.eventType) {
            SystemEvents.RUNTIME_READY ->
                "Runtime lifecycle transitioned to READY"
            SystemEvents.RUNTIME_ERROR -> {
                val payload = event.payload
                if (payload is com.nova.runtime.events.system.RuntimeErrorPayload) {
                    "Runtime error: ${payload.code} — ${payload.message}"
                } else {
                    "Runtime error reported"
                }
            }
            UnderstandingEvents.INTENT_DETECTED -> "Intent detected"
            UnderstandingEvents.NIR_GENERATED -> "NIR generated and validated"
            ReasoningEvents.STARTED -> "Reasoning engine started"
            ReasoningEvents.COMPLETED -> "Reasoning completed"
            PlannerEvents.PLANNING_STARTED -> "Planning started"
            PlannerEvents.GRAPH_BUILT -> "Action graph built"
            PlannerEvents.PLANNING_COMPLETED -> "Planning completed"
            ExecutionEvents.STARTED -> "Execution runtime started"
            ExecutionEvents.NODE_COMPLETED -> "Action node completed"
            ExecutionEvents.GRAPH_COMPLETED -> "Graph execution completed"
            ExecutionEvents.GRAPH_FAILED -> "Graph execution failed"
            CapabilityEvents.EXECUTED -> "Capability executed successfully"
            CapabilityEvents.FAILED -> "Capability execution failed"
            else -> return null
        }

        return ActivityItem(
            timestamp = formatTimestamp(event.timestamp),
            source = moduleLabel(event.sourceModule),
            message = message,
            isAlert = event.eventType in ALERT_EVENTS,
        )
    }

    private fun prependActivity(item: ActivityItem) {
        _activityFeed.update { current ->
            listOf(item) + current.take(MAX_FEED_ITEMS - 1)
        }
    }

    private fun now(): String = timeFormatter.format(Instant.now())

    private fun formatTimestamp(instant: Instant): String = timeFormatter.format(instant)

    private fun moduleLabel(module: RuntimeModule): String = when (module) {
        RuntimeModule.KERNEL -> "KERNEL"
        RuntimeModule.UNDERSTANDING -> "SUP"
        RuntimeModule.REASONING -> "REASONING"
        RuntimeModule.PLANNER -> "PLANNER"
        RuntimeModule.EXECUTION -> "EXECUTION"
        RuntimeModule.POLICY -> "POLICY"
        RuntimeModule.CAPABILITY -> "CAPABILITY"
        RuntimeModule.CONVERSATION -> "CONVERSATION"
        RuntimeModule.INFERENCE -> "INFERENCE"
        RuntimeModule.MEMORY -> "MEMORY"
        else -> module.name
    }

    private fun stageLabel(stage: PipelineStage): String = when (stage) {
        PipelineStage.UNDERSTANDING -> "SUP"
        PipelineStage.REASONING -> "REASONING"
        PipelineStage.PLANNING -> "PLANNER"
        PipelineStage.POLICY -> "POLICY"
        PipelineStage.EXECUTION -> "EXECUTION"
    }

    companion object {
        private const val MAX_FEED_ITEMS = 50
        private val ALERT_EVENTS = setOf(
            SystemEvents.RUNTIME_READY,
            ExecutionEvents.GRAPH_COMPLETED,
            ExecutionEvents.GRAPH_FAILED,
            CapabilityEvents.EXECUTED,
            CapabilityEvents.FAILED,
        )
    }
}
