package com.nova.runtime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nova.runtime.app.ui.NovaOsScreen
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.app.ui.theme.NovaTheme
import com.nova.runtime.kernel.RuntimeKernel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val runtimeKernel: RuntimeKernel by inject()
    private val viewModel: NovaOsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
