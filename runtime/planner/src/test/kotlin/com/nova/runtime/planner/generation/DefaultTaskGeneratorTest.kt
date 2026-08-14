package com.nova.runtime.planner.generation

import com.nova.runtime.models.Nir
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.planner.model.SubGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultTaskGeneratorTest {

    private val generator = DefaultTaskGenerator()

    @Test
    fun generate_readCalendar_mapsCalendarReadOperation() {
        val nir = Nir(
            version = 1,
            goal = "read_calendar",
            entities = emptyList(),
            constraints = mapOf(
                "capabilityOperation" to NovaCapabilityOperations.CALENDAR_READ,
                "startTime" to "1000",
                "endTime" to "2000",
            ),
            context = emptyMap(),
            requiredCapabilities = listOf("calendar.read"),
            confidence = 0.9,
        )
        val tasks = generator.generate(
            subGoals = listOf(
                SubGoal("execute_capability:calendar.read", "read", "calendar.read", 0),
            ),
            nir = nir,
            reasoningContext = ReasoningContext(emptyMap(), emptyList(), emptyList(), emptyList(), 0.9),
        )
        val execute = tasks.first { it.key.startsWith("execute:") }
        assertEquals(NovaCapabilityOperations.CALENDAR_READ, execute.inputs["capabilityOperation"])
        assertEquals("read", execute.inputs["operation"])
    }

    @Test
    fun generate_scheduleFromMessage_requiresConfirmationOnCreate() {
        val nir = Nir(
            version = 1,
            goal = "schedule_from_message",
            entities = emptyList(),
            constraints = mapOf(
                "requiresConfirmation" to "true",
                "title" to "Sync with team",
                "startTime" to "1000",
                "endTime" to "2000",
            ),
            context = emptyMap(),
            requiredCapabilities = listOf("calendar.read", "calendar"),
            confidence = 0.9,
        )
        val tasks = generator.generate(
            subGoals = listOf(
                SubGoal("execute_capability:calendar", "create", "calendar", 1),
            ),
            nir = nir,
            reasoningContext = ReasoningContext(emptyMap(), emptyList(), emptyList(), emptyList(), 0.9),
        )
        val execute = tasks.first { it.key == "execute:calendar" }
        assertEquals("true", execute.inputs["requiresConfirmation"])
        assertEquals(NovaCapabilityOperations.CALENDAR_CREATE, execute.inputs["capabilityOperation"])
    }
}
