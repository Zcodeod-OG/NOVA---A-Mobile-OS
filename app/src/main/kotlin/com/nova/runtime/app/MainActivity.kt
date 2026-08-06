package com.nova.runtime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nova.runtime.app.ui.NovaOsScreen
import com.nova.runtime.app.ui.theme.NovaTheme
import com.nova.runtime.kernel.RuntimeKernel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val runtimeKernel: RuntimeKernel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by runtimeKernel.lifecycleManager.state.collectAsState()
            NovaTheme {
                NovaOsScreen(lifecycleState = state)
            }
        }
    }
}
