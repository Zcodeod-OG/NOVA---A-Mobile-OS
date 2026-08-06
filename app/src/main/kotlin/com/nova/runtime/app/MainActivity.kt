package com.nova.runtime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nova.runtime.app.ui.NovaOsScreen
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.app.ui.theme.NovaTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val viewModel: NovaOsViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaTheme {
                NovaOsScreen(viewModel = viewModel)
            }
        }
    }
}
