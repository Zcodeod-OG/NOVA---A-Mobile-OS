package com.nova.runtime.android.alarmAdapter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
) : AlarmAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            AlarmOperations.CREATE,
            AlarmOperations.CANCEL,
            AlarmOperations.UPDATE,
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
                    AlarmOperations.CREATE -> createAlarm(parameters)
                    AlarmOperations.CANCEL -> cancelAlarm(parameters)
                    AlarmOperations.UPDATE -> updateAlarm(parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun createAlarm(parameters: Map<String, String>): Map<String, String> {
        val requestCode = parameters.require("requestCode").toInt()
        val triggerAtMillis = parameters.require("triggerAtMillis").toLong()
        scheduleExact(requestCode, triggerAtMillis, parameters["action"])
        return mapOf(
            "requestCode" to requestCode.toString(),
            "triggerAtMillis" to triggerAtMillis.toString(),
            "status" to "scheduled",
        )
    }

    private fun cancelAlarm(parameters: Map<String, String>): Map<String, String> {
        val requestCode = parameters.require("requestCode").toInt()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(requestCode, parameters["action"]))
        return mapOf("requestCode" to requestCode.toString(), "status" to "cancelled")
    }

    private fun updateAlarm(parameters: Map<String, String>): Map<String, String> {
        val requestCode = parameters.require("requestCode").toInt()
        val triggerAtMillis = parameters.require("triggerAtMillis").toLong()
        cancelAlarm(mapOf("requestCode" to requestCode.toString()))
        scheduleExact(requestCode, triggerAtMillis, parameters["action"])
        return mapOf(
            "requestCode" to requestCode.toString(),
            "triggerAtMillis" to triggerAtMillis.toString(),
            "status" to "updated",
        )
    }

    private fun scheduleExact(requestCode: Int, triggerAtMillis: Long, action: String?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(requestCode, action)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun buildPendingIntent(requestCode: Int, action: String?): PendingIntent {
        val intent =
            Intent(context, NovaAlarmReceiver::class.java).apply {
                this.action = action ?: NovaAlarmReceiver.DEFAULT_ACTION
                putExtra(NovaAlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private companion object {
        const val ADAPTER_NAME = "Alarm"
    }
}

class AlarmAdapterStub : AlarmAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(AlarmOperations.CREATE, AlarmOperations.CANCEL, AlarmOperations.UPDATE)
}
