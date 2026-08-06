package com.nova.runtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.components.ActivityItem
import com.nova.runtime.app.ui.components.ActivityStream
import com.nova.runtime.app.ui.components.CommandBar
import com.nova.runtime.app.ui.components.SystemHeader
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaDarkBackground
import com.nova.runtime.app.ui.theme.NovaIndigoAccent
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary
import com.nova.runtime.models.RuntimeLifecycleState

data class CapabilityModule(
    val title: String,
    val description: String,
    val isOnline: Boolean
)

@Composable
fun NovaOsScreen(
    lifecycleState: RuntimeLifecycleState,
    modifier: Modifier = Modifier
) {
    val modules = remember {
        listOf(
            CapabilityModule("Kernel Engine", "Core lifecycle & event bus", true),
            CapabilityModule("Cognitive Planner", "Task breakdown & routing", true),
            CapabilityModule("Reasoning Matrix", "Context & decision engine", true),
            CapabilityModule("Vector Memory", "Short-term & long-term store", true),
            CapabilityModule("Local Inference", "GGML/ONNX quantized LLM", true),
            CapabilityModule("Execution System", "Sandboxed action executor", true)
        )
    }

    val activityFeed = remember {
        mutableStateListOf(
            ActivityItem("14:55:01", "KERNEL", "System boot initiated. Loading Koin DI modules..."),
            ActivityItem("14:55:02", "MEMORY", "Vector DB initialized (512-dim embedding engine)"),
            ActivityItem("14:55:03", "INFERENCE", "Quantized model weight loaded: NOVA-Local-1.0"),
            ActivityItem("14:55:04", "KERNEL", "Runtime lifecycle transitioned to READY", isAlert = true)
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NovaDarkBackground,
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                CommandBar(
                    onCommandSubmit = { command ->
                        activityFeed.add(
                            0,
                            ActivityItem(
                                timestamp = "NOW",
                                source = "USER",
                                message = command,
                                isAlert = true
                            )
                        )
                        activityFeed.add(
                            0,
                            ActivityItem(
                                timestamp = "NOW",
                                source = "PLANNER",
                                message = "Processing intent: '$command' -> Generating plan..."
                            )
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top System Telemetry Header
            SystemHeader(lifecycleState = lifecycleState)

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Cognitive Modules Grid
            Text(
                text = "COGNITIVE RUNTIME MODULES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NovaTextSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(modules) { module ->
                    ModuleCard(module = module)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Live Cognition Event Stream
            ActivityStream(
                activities = activityFeed,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ModuleCard(
    module: CapabilityModule,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NovaSurfaceDark)
            .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable { }
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = module.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NovaTextPrimary
                )

                Text(
                    text = if (module.isOnline) "● ACTIVE" else "○ IDLE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (module.isOnline) NovaCyanAccent else NovaTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = module.description,
                fontSize = 10.sp,
                color = NovaTextSecondary,
                maxLines = 2
            )
        }
    }
}
