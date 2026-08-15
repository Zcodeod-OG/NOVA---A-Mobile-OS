package com.nova.runtime.android.alarmAdapter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.time.Instant
import java.time.ZoneId
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
            // Main: ACTION_SET_ALARM / startActivity must run on the UI thread.
            withContext(Dispatchers.Main) {
                when (operation) {
                    AlarmOperations.CREATE -> createAlarm(parameters, traceId)
                    AlarmOperations.CANCEL -> cancelAlarm(parameters)
                    AlarmOperations.UPDATE -> updateAlarm(parameters, traceId)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun createAlarm(parameters: Map<String, String>, traceId: UUID): Map<String, String> {
        val requestCode = parameters.require("requestCode").toInt()
        val triggerAtMillis = parameters.require("triggerAtMillis").toLong()
        val label = parameters["label"]
        val kind = parameters["alarmKind"]?.lowercase().orEmpty()
        val strategies = resolveStrategies(kind)

        val results = mutableListOf<String>()
        var clockLaunched = false
        var clockError: String? = null

        if (Strategy.CLOCK in strategies) {
            when (val clock = launchSystemClockAlarm(triggerAtMillis, label, traceId)) {
                is ClockLaunchResult.Success -> {
                    clockLaunched = true
                    results += "clock"
                }
                is ClockLaunchResult.Failure -> {
                    clockError = clock.reason
                    logger.warn(
                        module = "ANDROID_ADAPTER",
                        message = "Clock ACTION_SET_ALARM failed",
                        traceId = traceId,
                        metadata = mapOf("error" to clock.reason),
                    )
                }
            }
        }
        if (Strategy.REMINDER in strategies) {
            runCatching {
                scheduleAlarmManager(requestCode, triggerAtMillis, parameters["action"], label, traceId)
            }.onSuccess { mode ->
                results += mode
            }.onFailure { error ->
                logger.warn(
                    module = "ANDROID_ADAPTER",
                    message = "AlarmManager schedule failed",
                    traceId = traceId,
                    throwable = error,
                )
            }
        }

        // Alarm requests must open/populate the system Clock app — do not report success
        // when only AlarmManager.setAlarmClock ran (status-bar alarm ≠ Clock list entry).
        if (Strategy.CLOCK in strategies && !clockLaunched) {
            throw IllegalStateException(
                clockError
                    ?: "Couldn't open the Clock app to set the alarm (ACTION_SET_ALARM unresolved).",
            )
        }

        if (results.isEmpty()) {
            throw IllegalStateException("Failed to schedule alarm via Clock or AlarmManager")
        }

        return buildMap {
            put("requestCode", requestCode.toString())
            put("triggerAtMillis", triggerAtMillis.toString())
            put("status", "scheduled")
            put("alarmMode", results.joinToString("+"))
            put("alarmKind", kind.ifBlank { "reminder" })
            put("clockLaunched", clockLaunched.toString())
            put("canScheduleExactAlarms", canScheduleExactAlarms().toString())
            label?.let { put("label", it) }
            formatTriggerLabel(triggerAtMillis)?.let { put("triggerLabel", it) }
        }
    }

    private fun cancelAlarm(parameters: Map<String, String>): Map<String, String> {
        val requestCode = parameters.require("requestCode").toInt()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(requestCode, parameters["action"]))
        return mapOf("requestCode" to requestCode.toString(), "status" to "cancelled")
    }

    private fun updateAlarm(parameters: Map<String, String>, traceId: UUID): Map<String, String> {
        val requestCode = parameters.require("requestCode").toInt()
        cancelAlarm(mapOf("requestCode" to requestCode.toString()))
        val created = createAlarm(parameters, traceId)
        return created + ("status" to "updated")
    }

    private fun resolveStrategies(kind: String): Set<Strategy> =
        when (kind) {
            "clock", "alarm", "set_alarm" -> setOf(Strategy.CLOCK, Strategy.REMINDER)
            "reminder", "set_reminder" -> setOf(Strategy.REMINDER)
            else -> setOf(Strategy.CLOCK, Strategy.REMINDER)
        }

    /**
     * Prefer [AlarmManager.setAlarmClock] — exact, user-visible, and does not require
     * [android.Manifest.permission.SCHEDULE_EXACT_ALARM] on Android 12+. Fall back to
     * exact / inexact APIs when needed.
     */
    private fun scheduleAlarmManager(
        requestCode: Int,
        triggerAtMillis: Long,
        action: String?,
        label: String? = null,
        traceId: UUID,
    ): String {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(requestCode, action, label)
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent().setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                pendingIntent,
            )
            logger.info(
                module = "ANDROID_ADAPTER",
                message = "Alarm scheduled via setAlarmClock",
                traceId = traceId,
                metadata = mapOf(
                    "requestCode" to requestCode.toString(),
                    "triggerAtMillis" to triggerAtMillis.toString(),
                    "label" to (label ?: ""),
                ),
            )
            "alarm_clock"
        } catch (security: SecurityException) {
            logger.warn(
                module = "ANDROID_ADAPTER",
                message = "setAlarmClock blocked, falling back",
                traceId = traceId,
                metadata = mapOf("error" to (security.message ?: "SecurityException")),
            )
            scheduleFallback(alarmManager, triggerAtMillis, pendingIntent, traceId)
        }
    }

    private fun scheduleFallback(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        traceId: UUID,
    ): String {
        val canExact = canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            logger.info(
                module = "ANDROID_ADAPTER",
                message = "Alarm scheduled via setExactAndAllowWhileIdle",
                traceId = traceId,
                metadata = mapOf("triggerAtMillis" to triggerAtMillis.toString()),
            )
            return "exact"
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
        logger.warn(
            module = "ANDROID_ADAPTER",
            message = "Alarm scheduled inexact — exact alarm permission missing",
            traceId = traceId,
            metadata = mapOf(
                "triggerAtMillis" to triggerAtMillis.toString(),
                "exactAlarmSettings" to Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            ),
        )
        return "inexact"
    }

    /**
     * Hands the alarm to the system Clock app so the user sees a real alarm.
     * EXTRA_SKIP_UI=false so Clock UI opens (OxygenOS often ignores silent SET_ALARM).
     * Tries OEM Clock packages explicitly when the default resolve fails.
     */
    private fun launchSystemClockAlarm(
        triggerAtMillis: Long,
        label: String?,
        traceId: UUID,
    ): ClockLaunchResult {
        val zoned = Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault())
        val message = label?.takeIf { it.isNotBlank() } ?: "NOVA Alarm"
        val packageCandidates = buildList {
            add(null) // default resolve
            addAll(OEM_CLOCK_PACKAGES)
        }

        var lastError: String? = null
        for (pkg in packageCandidates) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, zoned.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, zoned.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                // Show Clock UI so the user can confirm the alarm was added.
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pkg != null) setPackage(pkg)
            }
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                lastError =
                    if (pkg == null) {
                        "No Clock app handles ACTION_SET_ALARM"
                    } else {
                        "Clock package $pkg does not handle ACTION_SET_ALARM"
                    }
                continue
            }
            try {
                context.startActivity(intent)
                logger.info(
                    module = "ANDROID_ADAPTER",
                    message = "Launched system Clock ACTION_SET_ALARM",
                    traceId = traceId,
                    metadata = mapOf(
                        "hour" to zoned.hour.toString(),
                        "minute" to zoned.minute.toString(),
                        "component" to resolved.flattenToString(),
                        "package" to (pkg ?: resolved.packageName),
                        "skipUi" to "false",
                    ),
                )
                return ClockLaunchResult.Success(resolved.flattenToString())
            } catch (error: SecurityException) {
                lastError =
                    "Permission denied launching Clock SET_ALARM " +
                        "(need com.android.alarm.permission.SET_ALARM): ${error.message}"
                logger.warn(
                    module = "ANDROID_ADAPTER",
                    message = "Failed to launch ACTION_SET_ALARM",
                    traceId = traceId,
                    metadata = mapOf(
                        "error" to lastError.orEmpty(),
                        "package" to (pkg ?: resolved.packageName),
                    ),
                )
            } catch (error: Exception) {
                lastError =
                    "Failed to launch Clock SET_ALARM: ${error.message ?: error::class.simpleName}"
                logger.warn(
                    module = "ANDROID_ADAPTER",
                    message = "Failed to launch ACTION_SET_ALARM",
                    traceId = traceId,
                    metadata = mapOf(
                        "error" to lastError.orEmpty(),
                        "package" to (pkg ?: resolved.packageName),
                    ),
                )
            }
        }
        return ClockLaunchResult.Failure(
            lastError ?: "Couldn't open the Clock app to set the alarm.",
        )
    }

    private fun canScheduleExactAlarms(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun buildPendingIntent(
        requestCode: Int,
        action: String?,
        label: String? = null,
    ): PendingIntent {
        val intent =
            Intent(context, NovaAlarmReceiver::class.java).apply {
                this.action = action ?: NovaAlarmReceiver.DEFAULT_ACTION
                putExtra(NovaAlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
                label?.let { putExtra(NovaAlarmReceiver.EXTRA_LABEL, it) }
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatTriggerLabel(triggerAtMillis: Long): String? =
        runCatching {
            Instant.ofEpochMilli(triggerAtMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .toString()
        }.getOrNull()

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private enum class Strategy { CLOCK, REMINDER }

    private sealed class ClockLaunchResult {
        data class Success(val component: String) : ClockLaunchResult()
        data class Failure(val reason: String) : ClockLaunchResult()
    }

    private companion object {
        const val ADAPTER_NAME = "Alarm"

        /** Common Clock packages on OnePlus / ColorOS / AOSP / Pixel. */
        val OEM_CLOCK_PACKAGES = listOf(
            "com.oneplus.deskclock",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            "com.android.deskclock",
            "com.google.android.deskclock",
        )
    }
}

class AlarmAdapterStub : AlarmAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        CapabilityResult.Success(
            buildMap {
                put("stub", "true")
                put("operation", operation)
                parameters["requestCode"]?.let { put("requestCode", it) }
                parameters["triggerAtMillis"]?.let { put("triggerAtMillis", it) }
                parameters["alarmKind"]?.let { put("alarmKind", it) }
                if (operation == AlarmOperations.CREATE) {
                    put("status", "scheduled")
                    put("alarmMode", "stub")
                }
            },
        )

    override fun supportedOperations(): Set<String> =
        setOf(AlarmOperations.CREATE, AlarmOperations.CANCEL, AlarmOperations.UPDATE)
}
