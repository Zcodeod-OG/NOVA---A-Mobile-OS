package com.nova.runtime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.models.RuntimeLifecycleState
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val runtimeKernel: RuntimeKernel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ready = runtimeKernel.lifecycleManager.state.value == RuntimeLifecycleState.READY
        setContent {
            MaterialTheme {
                Surface {
                    Text(text = "NOVA — Sprint 0 (ready=$ready)")
                }
            }
        }
    }
}
