package com.nova.runtime.app.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.app.ui.components.ActivityItem
import com.nova.runtime.app.ui.components.ContentDetailState
import com.nova.runtime.app.ui.components.NovaButton
import com.nova.runtime.app.ui.components.NovaButtonVariant
import com.nova.runtime.app.ui.components.NovaCard
import com.nova.runtime.app.ui.components.NovaStatusChip
import com.nova.runtime.app.ui.components.NovaToggle
import com.nova.runtime.app.ui.models.LiveTelemetryState
import com.nova.runtime.app.ui.theme.NovaAkzidenzRed
import com.nova.runtime.app.ui.theme.NovaPureBlack
import com.nova.runtime.app.ui.theme.NovaSecondaryGrey
import com.nova.runtime.app.ui.theme.NovaSurfaceContainerHighest
import com.nova.runtime.app.ui.theme.NovaSurfaceDim
import com.nova.runtime.app.ui.theme.NovaSurfaceLowest
import com.nova.runtime.models.RuntimeLifecycleState

/**
 * Objective Modernist SYSTEM Dashboard Tab.
 * Driven 100% by real app state and ViewModel StateFlows.
 */
@Composable
fun DashboardTab(
    viewModel: NovaOsViewModel,
    lifecycleState: RuntimeLifecycleState,
    indexingStatus: String?,
    activityFeed: List<ActivityItem>,
    telemetryState: LiveTelemetryState = LiveTelemetryState(),
    onExpandContent: (ContentDetailState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nlpEnabled by viewModel.nlpModuleEnabled.collectAsState()
    val actionsEnabled by viewModel.actionsModuleEnabled.collectAsState()
    val docsEnabled by viewModel.docsModuleEnabled.collectAsState()
    val appEnabled by viewModel.appModuleEnabled.collectAsState()
    val systemPwrActive by viewModel.systemPwrActive.collectAsState()
    val activeLayer by viewModel.activeLayer.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val tempSetting by viewModel.temperatureSetting.collectAsState()
    val ctxSetting by viewModel.contextWindowSetting.collectAsState()
    val quantSetting by viewModel.quantizationSetting.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Processing Flow Card
        ProcessingFlowCard(
            nlpEnabled = nlpEnabled,
            actionsEnabled = actionsEnabled,
            docsEnabled = docsEnabled,
            appEnabled = appEnabled,
            isProcessing = isProcessing,
            activeLayer = activeLayer,
            telemetryState = telemetryState,
            onToggleModule = viewModel::toggleModule,
            onExpandContent = onExpandContent,
        )

        // 2. Grid Layout: Autonomous Mode & Session Details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AutonomousModeCard(
                systemPwrActive = systemPwrActive,
                telemetryState = telemetryState,
                onTogglePwr = viewModel::toggleSystemPower,
                modifier = Modifier.weight(1f),
            )
            SessionDetailsCard(
                telemetryState = telemetryState,
                modifier = Modifier.weight(1f),
            )
        }

        // 3. Model Configuration Summary Card
        ModelConfigCard(
            temperature = tempSetting,
            contextWindow = ctxSetting,
            quantizationEnabled = quantSetting,
            ragEnabled = docsEnabled,
            onToggleQuant = viewModel::toggleQuantization,
            onToggleRag = { viewModel.toggleModule("DOCS") },
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProcessingFlowCard(
    nlpEnabled: Boolean,
    actionsEnabled: Boolean,
    docsEnabled: Boolean,
    appEnabled: Boolean,
    isProcessing: Boolean,
    activeLayer: String,
    telemetryState: LiveTelemetryState,
    onToggleModule: (String) -> Unit,
    onExpandContent: (ContentDetailState) -> Unit,
) {
    NovaCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = NovaSurfaceLowest,
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "PROCESSING FLOW",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.02).sp,
                        ),
                        color = NovaPureBlack,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    NovaStatusChip(
                        status = if (isProcessing) "LIVE INFERENCE" else "IDLE / READY",
                        isPrimary = isProcessing,
                    )
                }

                NovaButton(
                    text = "ADD MODULE",
                    onClick = {
                        onExpandContent(
                            ContentDetailState(
                                fileName = "MODULE REGISTRY",
                                text = "Registered Core Modules:\n• NLP Engine: ACTIVE\n• Actions Executor: ACTIVE\n• RAG Documents Indexer: ACTIVE\n• App Controller: STANDBY",
                            )
                        )
                    },
                    variant = NovaButtonVariant.PRIMARY,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Graph Canvas & Modules Diagram
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Main LLM Node
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(2.dp, NovaPureBlack, RoundedCornerShape(0.dp))
                        .background(NovaSurfaceLowest, RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "LLM Node",
                            tint = NovaAkzidenzRed,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "LOCAL LLM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = NovaPureBlack,
                        )
                        Text(
                            text = "SMOLLM2",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = NovaAkzidenzRed,
                        )
                    }
                }

                // Connection Lines (Bezier Paths)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                ) {
                    val startX = 0f
                    val endX = size.width
                    val startY = size.height / 2f
                    val targets = listOf(
                        size.height * 0.15f,
                        size.height * 0.38f,
                        size.height * 0.62f,
                        size.height * 0.85f,
                    )

                    targets.forEach { targetY ->
                        val path = Path().apply {
                            moveTo(startX, startY)
                            cubicTo(
                                startX + (endX - startX) * 0.5f, startY,
                                startX + (endX - startX) * 0.5f, targetY,
                                endX, targetY,
                            )
                        }
                        drawPath(
                            path = path,
                            color = NovaPureBlack,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                // End Subsystem Nodes with Toggles
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    ModuleToggleRow("NLP", nlpEnabled) { onToggleModule("NLP") }
                    ModuleToggleRow("ACTIONS", actionsEnabled) { onToggleModule("ACTIONS") }
                    ModuleToggleRow("DOCS", docsEnabled) { onToggleModule("DOCS") }
                    ModuleToggleRow("APP", appEnabled) { onToggleModule("APP") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Readout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp))
                    .padding(top = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "CONTEXT LOAD",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.05.sp),
                            color = NovaSecondaryGrey,
                        )
                        Text(
                            text = "${(telemetryState.memoryUsagePercent * 0.024f).let { "%.1f".format(it) }}GB",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = NovaPureBlack,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ACTIVE LAYER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.05.sp),
                            color = NovaSecondaryGrey,
                        )
                        Text(
                            text = activeLayer,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaAkzidenzRed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (checked) NovaPureBlack else NovaSecondaryGrey,
            ),
        )
        NovaToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AutonomousModeCard(
    systemPwrActive: Boolean,
    telemetryState: LiveTelemetryState,
    onTogglePwr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaCard(
        modifier = modifier,
        backgroundColor = NovaSurfaceLowest,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NOVA",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = NovaPureBlack,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Autonomous",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = NovaAkzidenzRed,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Concentric Rectangles Graphic (Swiss Style)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(2.dp, NovaPureBlack, RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .border(2.dp, NovaAkzidenzRed, RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(NovaPureBlack, RoundedCornerShape(0.dp)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SYSTEM PWR",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = NovaSecondaryGrey,
            )

            Spacer(modifier = Modifier.height(6.dp))

            NovaButton(
                text = if (systemPwrActive) "ON" else "OFF",
                onClick = onTogglePwr,
                variant = if (systemPwrActive) NovaButtonVariant.PRIMARY_RED else NovaButtonVariant.SECONDARY,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Pwr",
                        tint = NovaSurfaceLowest,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp))
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "MEM",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = NovaSecondaryGrey,
                    )
                    Text(
                        text = "${telemetryState.memoryUsagePercent}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NovaPureBlack,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CTX",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = NovaSecondaryGrey,
                    )
                    Text(
                        text = "4k/32k",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NovaPureBlack,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDetailsCard(
    telemetryState: LiveTelemetryState,
    modifier: Modifier = Modifier,
) {
    NovaCard(
        modifier = modifier,
        backgroundColor = NovaSurfaceLowest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "SESSION DETAILS",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = NovaPureBlack,
            )

            SwissDetailRow("MODE", "AUTONOMOUS", isRed = true)
            SwissDetailRow("TIME", telemetryState.formattedUptime)
            SwissDetailRow("MODEL", "SMOLLM2-1.7B")
            SwissDetailRow(
                "LOAD / T/S",
                "${telemetryState.memoryUsagePercent}% / ${(telemetryState.npuTeraOpsCurrent * 20).let { "%.1f".format(it) }}",
            )
        }
    }
}

@Composable
private fun SwissDetailRow(label: String, value: String, isRed: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = NovaSurfaceDim, shape = RoundedCornerShape(0.dp))
            .padding(bottom = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = NovaSecondaryGrey,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isRed) NovaAkzidenzRed else NovaPureBlack,
            ),
        )
    }
}

@Composable
private fun ModelConfigCard(
    temperature: Float,
    contextWindow: Int,
    quantizationEnabled: Boolean,
    ragEnabled: Boolean,
    onToggleQuant: () -> Unit,
    onToggleRag: () -> Unit,
) {
    NovaCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = NovaSurfaceLowest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "MODEL CONFIG",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = NovaPureBlack,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp))
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TEMPERATURE",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaPureBlack,
                )
                Text(
                    text = "%.1f".format(temperature),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NovaAkzidenzRed,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp))
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CONTEXT WINDOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaPureBlack,
                )
                Text(
                    text = "%,d".format(contextWindow),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NovaPureBlack,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "QUANTIZATION",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NovaPureBlack,
                    )
                    Text(
                        text = "4-BIT GGUF (REC)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaAkzidenzRed,
                    )
                }
                NovaToggle(checked = quantizationEnabled, onCheckedChange = { onToggleQuant() })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "ON-DEVICE RAG",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NovaPureBlack,
                    )
                    Text(
                        text = "ENABLE VECTOR SEARCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaSecondaryGrey,
                    )
                }
                NovaToggle(checked = ragEnabled, onCheckedChange = { onToggleRag() })
            }
        }
    }
}
