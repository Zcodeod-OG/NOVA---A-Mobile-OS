package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.alarmAdapter.AlarmOperations
import com.nova.runtime.android.calendarAdapter.CalendarOperations
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Time capabilities backed by Alarm and Calendar adapters. */
class AndroidTimeProvider(
    context: Context,
    logger: NovaLogger,
    private val adapters: AndroidAdapterLayer,
) : AdapterDelegatingCapabilityProvider(context, logger) {

    override val providerId: String = "android-time"
    override val capabilityType: String = "time"
    override val version: String = "1.0.0"

    override fun supportedOperations(): Set<String> =
        setOf(
            CapabilityOperations.ALARM_CREATE,
            CapabilityOperations.CALENDAR_READ,
            CapabilityOperations.CALENDAR_CREATE,
        )

    override fun requiredPermissions(): Set<String> =
        setOf(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
        )

    override fun permissionsForOperation(operation: String): Set<String> =
        when (operation) {
            CapabilityOperations.CALENDAR_READ ->
                setOf(android.Manifest.permission.READ_CALENDAR)
            CapabilityOperations.CALENDAR_CREATE ->
                setOf(android.Manifest.permission.WRITE_CALENDAR)
            CapabilityOperations.ALARM_CREATE -> emptySet()
            else -> emptySet()
        }

    override suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult =
        when (request.operation) {
            CapabilityOperations.ALARM_CREATE -> {
                if (request.parameters["triggerAtMillis"].isNullOrBlank() &&
                    request.parameters["triggerTime"].isNullOrBlank()
                ) {
                    invalidParameters("triggerAtMillis or triggerTime is required")
                } else {
                    CapabilityValidationResult.Valid
                }
            }
            CapabilityOperations.CALENDAR_CREATE -> {
                val missing = listOf("title", "startTime", "endTime")
                    .filter { request.parameters[it].isNullOrBlank() }
                if (missing.isNotEmpty()) {
                    invalidParameters("Missing required parameters: ${missing.joinToString(", ")}")
                } else {
                    CapabilityValidationResult.Valid
                }
            }
            else -> CapabilityValidationResult.Valid
        }

    override suspend fun dispatch(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        when (operation) {
            CapabilityOperations.ALARM_CREATE -> createAlarm(parameters, traceId)
            CapabilityOperations.CALENDAR_READ -> readCalendar(parameters, traceId)
            CapabilityOperations.CALENDAR_CREATE -> createCalendarEvent(parameters, traceId)
            else -> error("unsupported operation: $operation")
        }

    private suspend fun createAlarm(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        val triggerAtMillis = parameters["triggerAtMillis"]
            ?: parameters["triggerTime"]
            ?: error("triggerAtMillis required")
        val requestCode = parameters["requestCode"]
            ?: parameters.hashCode().toString().takeLast(6).toInt().toString()

        return adapters.alarms.execute(
            operation = AlarmOperations.CREATE,
            parameters = buildMap {
                put("requestCode", requestCode)
                put("triggerAtMillis", triggerAtMillis)
                parameters["action"]?.let { put("action", it) }
            },
            traceId = traceId,
        )
    }

    private suspend fun readCalendar(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        adapters.calendar.execute(
            operation = CalendarOperations.READ_EVENTS,
            parameters = buildMap {
                parameters["startTime"]?.let { put("startTime", it) }
                parameters["endTime"]?.let { put("endTime", it) }
                parameters["calendarId"]?.let { put("calendarId", it) }
            },
            traceId = traceId,
        )

    private suspend fun createCalendarEvent(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        adapters.calendar.execute(
            operation = CalendarOperations.CREATE_EVENT,
            parameters = buildMap {
                put("title", parameters.require("title"))
                put("startTime", parameters.require("startTime"))
                put("endTime", parameters.require("endTime"))
                parameters["calendarId"]?.let { put("calendarId", it) }
                parameters["description"]?.let { put("description", it) }
            },
            traceId = traceId,
        )

    private fun invalidParameters(detail: String): CapabilityValidationResult.Invalid =
        CapabilityValidationResult.Invalid(
            com.nova.runtime.models.RuntimeError(
                code = "CAPABILITY_INVALID_PARAMETERS",
                category = com.nova.runtime.models.ErrorCategory.VALIDATION,
                severity = com.nova.runtime.models.ErrorSeverity.LOW,
                recoverable = false,
                userVisibleMessage = detail,
                diagnostics = mapOf("providerId" to providerId),
            ),
        )
}
