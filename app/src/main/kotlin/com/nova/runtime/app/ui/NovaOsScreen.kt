package com.nova.runtime.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.components.ActivityStream
import com.nova.runtime.app.ui.components.AppGridSection
import com.nova.runtime.app.ui.components.CommandBar
import com.nova.runtime.app.ui.components.FloatingNovaVoiceWidget
import com.nova.runtime.app.ui.components.MobileOsWidgets
import com.nova.runtime.app.ui.components.SystemHeader
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaDarkBackground
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
    viewModel: NovaOsViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleState by viewModel.lifecycleState.collectAsState()
    val activityFeed by viewModel.activities.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NovaDarkBackground,
        floatingActionButton = {
            FloatingNovaVoiceWidget(
                onVoiceCommandCaptured = { spokenCommand ->
                    viewModel.submitCommand(spokenCommand)
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(12.dp)) {
                CommandBar(
                    onCommandSubmit = { command ->
                        viewModel.submitCommand(command)
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
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Top System Telemetry Header (Dynamic Island)
            SystemHeader(lifecycleState = lifecycleState ?: RuntimeLifecycleState.READY)

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Tab Selector Row (HOME OS vs COGNITION TELEMETRY)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = NovaSurfaceDark,
                contentColor = NovaCyanAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = NovaCyanAccent
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "📱 HOME OS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTabIndex == 0) NovaCyanAccent else NovaTextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "⚙️ TELEMETRY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTabIndex == 1) NovaCyanAccent else NovaTextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Tab Content Switcher
            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: HOME MOBILE OS INTERFACE
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Weather & Music Widgets
                        MobileOsWidgets(
                            onPlayMusicClick = {
                                viewModel.submitCommand("play lo-fi song on spotify")
                            }
                        )

                        // Interactive App Grid & Dock
                        AppGridSection(
                            onAppClick = { appName ->
                                viewModel.submitCommand("open $appName")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                1 -> {
                    // TAB 1: COGNITIVE RUNTIME MODULES & TELEMETRY STREAM
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "COGNITIVE RUNTIME MODULES (TAP TO INSPECT)",
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
                            modifier = Modifier.height(170.dp)
                        ) {
                            items(modules) { module ->
                                ModuleCard(
                                    module = module,
                                    onCardClick = { viewModel.inspectModule(module.title) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        ActivityStream(
                            activities = activityFeed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleCard(
    module: CapabilityModule,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NovaSurfaceDark)
            .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(12.dp))
            .clickable { onCardClick() }
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
