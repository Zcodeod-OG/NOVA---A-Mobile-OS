package com.nova.runtime.android.alarmAdapter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import com.nova.runtime.utils.logging.NovaLogger

/** Receives AlarmManager callbacks — AIS §4.6. */
class NovaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val logger: NovaLogger = NoOpRuntimeLogger()
        logger.info(
            module = "ANDROID_ADAPTER",
            message = "Alarm fired",
            metadata = mapOf(
                "requestCode" to requestCode.toString(),
                "action" to (intent.action ?: DEFAULT_ACTION),
            ),
        )
    }

    companion object {
        const val DEFAULT_ACTION = "com.nova.runtime.android.ALARM_FIRED"
        const val EXTRA_REQUEST_CODE = "requestCode"
    }
}
