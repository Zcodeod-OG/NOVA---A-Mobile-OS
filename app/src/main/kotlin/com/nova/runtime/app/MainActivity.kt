package com.nova.runtime.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
        setContent {
            val kernelState by runtimeKernel.lifecycleManager.state.collectAsState()
            LaunchedEffect(kernelState) {
                viewModel.updateLifecycleState(kernelState)
            }
            NovaTheme {
                NovaOsScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestMissingRuntimePermissions() {
        val needed = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        ).filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            runtimePermissionsLauncher.launch(needed.toTypedArray())
        }
    }
}
