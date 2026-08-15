package com.nova.runtime.android.calendarAdapter

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.android.internal.PermissionChecker
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
) : CalendarAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            CalendarOperations.READ_EVENTS,
            CalendarOperations.CREATE_EVENT,
            CalendarOperations.MODIFY_EVENT,
            CalendarOperations.DELETE_EVENT,
        )

    override fun requiredPermissions(): Set<String> =
        setOf(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
        )

    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        if (operation !in supportedOperations()) {
            return CapabilityResult.Failure(
                AdapterErrorMapper.invalidOperation(ADAPTER_NAME, operation, supportedOperations()),
            )
        }
        return AdapterBoundary.execute(logger, ADAPTER_NAME, operation, traceId) {
            withContext(Dispatchers.IO) {
                when (operation) {
                    CalendarOperations.READ_EVENTS -> readEvents(parameters)
                    CalendarOperations.CREATE_EVENT -> createEvent(parameters)
                    CalendarOperations.MODIFY_EVENT -> modifyEvent(parameters)
                    CalendarOperations.DELETE_EVENT -> deleteEvent(parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun readEvents(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.READ_CALENDAR)
        val startMs = parameters["startTime"]?.toLongOrNull() ?: 0L
        val endMs = parameters["endTime"]?.toLongOrNull() ?: Long.MAX_VALUE
        val calendarId = parameters["calendarId"]?.toLongOrNull()
        val selectionBuilder = StringBuilder("${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTEND} <= ?")
        val args = mutableListOf(startMs.toString(), endMs.toString())
        if (calendarId != null) {
            selectionBuilder.append(" AND ${CalendarContract.Events.CALENDAR_ID} = ?")
            args.add(calendarId.toString())
        }
        val events = mutableListOf<String>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
            ),
            selectionBuilder.toString(),
            args.toTypedArray(),
            CalendarContract.Events.DTSTART,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            while (cursor.moveToNext()) {
                events.add(
                    listOf(
                        cursor.getLong(idIndex),
                        cursor.getString(titleIndex),
                        cursor.getLong(startIndex),
                        cursor.getLong(endIndex),
                    ).joinToString(":"),
                )
            }
        } ?: throw IllegalStateException("Calendar provider unavailable")
        return mapOf("count" to events.size.toString(), "events" to events.joinToString("|"))
    }

    private fun createEvent(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.WRITE_CALENDAR)
        val calendarId = parameters["calendarId"]?.toLongOrNull() ?: resolveDefaultCalendarId()
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, parameters.require("title"))
                put(CalendarContract.Events.DTSTART, parameters.require("startTime").toLong())
                put(CalendarContract.Events.DTEND, parameters.require("endTime").toLong())
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                parameters["description"]?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create calendar event")
        val eventId = ContentUris.parseId(uri)
        return mapOf("eventId" to eventId.toString(), "calendarId" to calendarId.toString())
    }

    private fun modifyEvent(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.WRITE_CALENDAR)
        val eventId = parameters.require("eventId").toLong()
        val values = ContentValues()
        parameters["title"]?.let { values.put(CalendarContract.Events.TITLE, it) }
        parameters["startTime"]?.let { values.put(CalendarContract.Events.DTSTART, it.toLong()) }
        parameters["endTime"]?.let { values.put(CalendarContract.Events.DTEND, it.toLong()) }
        parameters["description"]?.let { values.put(CalendarContract.Events.DESCRIPTION, it) }
        if (values.size() == 0) throw IllegalArgumentException("No fields to update")
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val updated = context.contentResolver.update(uri, values, null, null)
        if (updated == 0) throw IllegalArgumentException("Event not found: $eventId")
        return mapOf("eventId" to eventId.toString(), "status" to "updated")
    }

    private fun deleteEvent(parameters: Map<String, String>): Map<String, String> {
        PermissionChecker.ensureGranted(context, android.Manifest.permission.WRITE_CALENDAR)
        val eventId = parameters.require("eventId").toLong()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val deleted = context.contentResolver.delete(uri, null, null)
        if (deleted == 0) throw IllegalArgumentException("Event not found: $eventId")
        return mapOf("eventId" to eventId.toString(), "status" to "deleted")
    }

    private fun resolveDefaultCalendarId(): Long {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            }
        }
        throw IllegalStateException("No visible calendar found")
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private companion object {
        const val ADAPTER_NAME = "Calendar"
    }
}

class CalendarAdapterStub : CalendarAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(
            CalendarOperations.READ_EVENTS,
            CalendarOperations.CREATE_EVENT,
            CalendarOperations.MODIFY_EVENT,
            CalendarOperations.DELETE_EVENT,
        )

    override fun requiredPermissions(): Set<String> = emptySet()
}
