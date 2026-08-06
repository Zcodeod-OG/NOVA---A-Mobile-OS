package com.nova.runtime.kernel.lifecycle

import com.nova.runtime.error.NovaException
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.kernel.api.LifecycleAware
import com.nova.runtime.utils.logging.NovaLogger
import com.nova.runtime.kernel.registry.ServiceRegistry
import com.nova.runtime.models.RuntimeLifecycleState
import com.nova.runtime.models.RuntimeModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

interface LifecycleManager {
    val state: StateFlow<RuntimeLifecycleState>
    suspend fun initialize(traceId: UUID = UUID.randomUUID())
    suspend fun start(traceId: UUID = UUID.randomUUID())
    suspend fun stop(traceId: UUID = UUID.randomUUID())
}

class DefaultLifecycleManager(
    private val serviceRegistry: ServiceRegistry,
    private val eventBus: EventBus,
    private val logger: NovaLogger,
) : LifecycleManager {

    private val currentState = AtomicReference(RuntimeLifecycleState.CREATED)
    private val _state = MutableStateFlow(RuntimeLifecycleState.CREATED)
    override val state: StateFlow<RuntimeLifecycleState> = _state.asStateFlow()

    override suspend fun initialize(traceId: UUID) {
        transition(RuntimeLifecycleState.CREATED, RuntimeLifecycleState.INITIALIZING, traceId)
        publishSystemEvent("RuntimeStarted", traceId)
        transition(RuntimeLifecycleState.INITIALIZING, RuntimeLifecycleState.READY, traceId)
        publishSystemEvent("RuntimeReady", traceId)
        logger.info(RuntimeModule.KERNEL.name, "Runtime initialized", traceId)
    }

    override suspend fun start(traceId: UUID) {
        requireState(RuntimeLifecycleState.READY)
        val lifecycleServices = serviceRegistry.registeredTypes()
            .mapNotNull { type -> serviceRegistry.getOrNull(type) }
            .filterIsInstance<LifecycleAware>()

        for (service in lifecycleServices) {
            logger.info(RuntimeModule.KERNEL.name, "Starting service", traceId)
            service.onStart()
        }
    }

    override suspend fun stop(traceId: UUID) {
        transition(currentState.get(), RuntimeLifecycleState.STOPPING, traceId)
        publishSystemEvent("RuntimeStopping", traceId)

        val lifecycleServices = serviceRegistry.registeredTypes()
            .mapNotNull { type -> serviceRegistry.getOrNull(type) }
            .filterIsInstance<LifecycleAware>()
            .reversed()

        for (service in lifecycleServices) {
            logger.info(RuntimeModule.KERNEL.name, "Stopping service", traceId)
            service.onStop()
        }

        transition(RuntimeLifecycleState.STOPPING, RuntimeLifecycleState.STOPPED, traceId)
        publishSystemEvent("RuntimeShutdown", traceId)
    }

    private suspend fun publishSystemEvent(eventType: String, traceId: UUID) {
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.KERNEL,
                eventType = eventType,
            ),
        )
    }

    private fun transition(from: RuntimeLifecycleState, to: RuntimeLifecycleState, traceId: UUID) {
        if (!currentState.compareAndSet(from, to)) {
            val actual = currentState.get()
            logger.error(
                RuntimeModule.KERNEL.name,
                "Invalid lifecycle transition from $actual to $to",
                traceId,
            )
            throw NovaException(NovaErrors.invalidLifecycleTransition(actual.name, to.name))
        }
        _state.value = to
    }

    private fun requireState(expected: RuntimeLifecycleState) {
        val actual = currentState.get()
        if (actual != expected) {
            throw NovaException(NovaErrors.invalidLifecycleTransition(actual.name, expected.name))
        }
    }
}
