package com.nova.runtime.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.nova.runtime.app.ui.NovaOsScreen
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.app.ui.theme.NovaTheme
import com.nova.runtime.kernel.RuntimeKernel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val runtimeKernel: RuntimeKernel by inject()
    private val viewModel: NovaOsViewModel by viewModel()

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* result handled on next command attempt */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMissingRuntimePermissions()
        ensureExactAlarmPermission()
        ensureAllFilesAccess()
        setContent {
            val kernelState by runtimeKernel.lifecycleManager.state.collectAsState()
            LaunchedEffect(kernelState) {
                viewModel.updateLifecycleState(kernelState)
            }
            NovaTheme {
                NovaOsScreen(viewModel = viewModel)
            }
        }
        handleCommandExtra(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCommandExtra(intent)
    }

    /**
     * Debug / smoke-test hook:
     * `adb shell am start -n com.nova.runtime.app/.MainActivity --es nova_command "open youtube"`
     */
    private fun handleCommandExtra(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_COMMAND)?.trim().orEmpty()
        if (command.isBlank()) return
        // Clear so rotation / redelivery doesn't re-fire the same command.
        intent?.removeExtra(EXTRA_COMMAND)
        viewModel.submitCommand(command)
    }

    private fun requestMissingRuntimePermissions() {
        val needed = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.WRITE_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            runtimePermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    /**
     * Exact alarms need a special settings grant on Android 12+ (not a normal runtime
     * permission dialog). Open the system screen once when missing so AlarmManager
     * fallbacks stay exact on OEMs that still require it.
     */
    private fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_EXACT_ALARM_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_EXACT_ALARM_PROMPTED, true).apply()
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    /**
     * Downloads/PDF indexing on Android 11+ requires All files access — MediaStore
     * otherwise hides other apps' documents from NOVA.
     */
    private fun ensureAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (Environment.isExternalStorageManager()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ALL_FILES_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_ALL_FILES_PROMPTED, true).apply()
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_COMMAND = "nova_command"
        private const val PREFS = "nova_runtime_prefs"
        private const val KEY_EXACT_ALARM_PROMPTED = "exact_alarm_prompted"
        private const val KEY_ALL_FILES_PROMPTED = "all_files_prompted"
    }
}
