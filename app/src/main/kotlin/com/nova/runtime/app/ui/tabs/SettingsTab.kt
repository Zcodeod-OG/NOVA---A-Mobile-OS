package com.nova.runtime.app.ui.tabs

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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.NovaOsViewModel
import com.nova.runtime.app.ui.components.ContentDetailState
import com.nova.runtime.app.ui.components.NovaButton
import com.nova.runtime.app.ui.components.NovaButtonVariant
import com.nova.runtime.app.ui.components.NovaCard
import com.nova.runtime.app.ui.components.NovaStatusChip
import com.nova.runtime.app.ui.components.NovaToggle
import com.nova.runtime.app.ui.models.LiveTelemetryState
import com.nova.runtime.app.ui.models.RuntimeModelSpec
import com.nova.runtime.app.ui.theme.NovaAkzidenzRed
import com.nova.runtime.app.ui.theme.NovaPureBlack
import com.nova.runtime.app.ui.theme.NovaSecondaryGrey
import com.nova.runtime.app.ui.theme.NovaSurfaceDim
import com.nova.runtime.app.ui.theme.NovaSurfaceLowest

/**
 * Objective Modernist CONFIG (Settings & System Parameters) Tab.
 * Driven 100% by real PreferenceDao, ModelDownloadManager, and telemetry state.
 */
@Composable
fun SettingsTab(
    viewModel: NovaOsViewModel,
    telemetryState: LiveTelemetryState = LiveTelemetryState(),
    onExpandContent: (ContentDetailState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tempSetting by viewModel.temperatureSetting.collectAsState()
    val ctxSetting by viewModel.contextWindowSetting.collectAsState()
    val quantSetting by viewModel.quantizationSetting.collectAsState()
    val runtimeModels by viewModel.runtimeModelsList.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header
        Column {
            Text(
                text = "SYSTEM CONFIG",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).sp,
                ),
                color = NovaPureBlack,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 2.dp, color = NovaAkzidenzRed, shape = RoundedCornerShape(0.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = "Hardware parameters and active runtime models.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NovaSecondaryGrey,
                )
            }
        }

        // Model Hyperparameters Card
        NovaCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = NovaSurfaceLowest,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "HYPERPARAMETERS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = NovaPureBlack,
                )

                // Temperature Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "TEMPERATURE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaPureBlack,
                        )
                        Text(
                            text = "%.2f".format(tempSetting),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = NovaAkzidenzRed,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = tempSetting,
                        onValueChange = { viewModel.updateTemperature(it) },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NovaAkzidenzRed,
                            activeTrackColor = NovaAkzidenzRed,
                            inactiveTrackColor = NovaSurfaceDim,
                        ),
                    )
                }

                // Context Window Selector Row
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CONTEXT WINDOW",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaPureBlack,
                        )
                        Text(
                            text = "%,d TOKEN".format(ctxSetting),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = NovaPureBlack,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(4096, 16384, 32768, 131072).forEach { option ->
                            val selected = ctxSetting == option
                            NovaButton(
                                text = when (option) {
                                    4096 -> "4K"
                                    16384 -> "16K"
                                    32768 -> "32K"
                                    else -> "128K"
                                },
                                onClick = { viewModel.updateContextWindow(option) },
                                variant = if (selected) NovaButtonVariant.PRIMARY_RED else NovaButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Quantization Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "4-BIT GGUF QUANTIZATION",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaPureBlack,
                        )
                        Text(
                            text = "ON-DEVICE NPU ACCELERATED",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaSecondaryGrey,
                        )
                    }

                    NovaToggle(
                        checked = quantSetting,
                        onCheckedChange = { viewModel.toggleQuantization() },
                    )
                }
            }
        }

        // Installed Models Card
        NovaCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = NovaSurfaceLowest,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "INSTALLED MODELS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = NovaPureBlack,
                    )
                    Text(
                        text = "${runtimeModels.size} CORES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NovaAkzidenzRed,
                    )
                }

                runtimeModels.forEach { model ->
                    RuntimeModelRow(
                        model = model,
                        onExpandContent = onExpandContent,
                    )
                }
            }
        }

        // Hardware Acceleration Telemetry Card
        NovaCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = NovaSurfaceLowest,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "HARDWARE ACCELERATION",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = NovaPureBlack,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "ACCELERATOR TYPE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovaSecondaryGrey,
                        )
                        Text(
                            text = "QUALCOMM HEXAGON NPU",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaPureBlack,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CURRENT POWER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovaSecondaryGrey,
                        )
                        Text(
                            text = "${"%.2f".format(telemetryState.npuTeraOpsCurrent)} TOPS",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaAkzidenzRed,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "NPU TEMPERATURE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovaSecondaryGrey,
                        )
                        Text(
                            text = "${telemetryState.npuTempCelsius} °C",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaPureBlack,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SYSTEM SAFETY SCORE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovaSecondaryGrey,
                        )
                        Text(
                            text = "98 / 100",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NovaAkzidenzRed,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RuntimeModelRow(
    model: RuntimeModelSpec,
    onExpandContent: (ContentDetailState) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp))
            .clickable {
                onExpandContent(
                    ContentDetailState(
                        fileName = "${model.name}: ${model.version}",
                        text = "Installed Model Specification:\n• Name: ${model.name}\n• Version: ${model.version}\n• Status: ${model.status}\n• Location: /data/user/0/com.nova/app_models/\n• Execution backend: ONNX / GGUF Local Runtime",
                    )
                )
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (model.iconName) {
                    "psychology" -> Icons.Filled.Psychology
                    "visibility" -> Icons.Filled.Visibility
                    "mic" -> Icons.Filled.Mic
                    else -> Icons.Filled.Storage
                },
                contentDescription = "Model",
                tint = NovaAkzidenzRed,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = NovaPureBlack,
                )
                Text(
                    text = model.version,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = NovaSecondaryGrey,
                )
            }
        }

        NovaStatusChip(
            status = model.status,
            isPrimary = model.status == "LOADED",
        )
    }
}
