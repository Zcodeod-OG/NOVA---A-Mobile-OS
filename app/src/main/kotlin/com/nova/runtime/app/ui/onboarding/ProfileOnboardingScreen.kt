package com.nova.runtime.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileOnboardingScreen(
    viewModel: ProfileOnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Personalize NOVA",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Set your work hours, priority topics, and default meeting length so NOVA can score messages and suggest calendar events.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Work hours", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.workHoursStart,
                    onValueChange = viewModel::updateWorkHoursStart,
                    label = { Text("Start") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.workHoursEnd,
                    onValueChange = viewModel::updateWorkHoursEnd,
                    label = { Text("End") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Text("Priority topics", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                viewModel.availableTopics.forEach { topic ->
                    FilterChip(
                        selected = topic in uiState.selectedTopics,
                        onClick = { viewModel.toggleTopic(topic) },
                        label = { Text(topic.replaceFirstChar { it.titlecase() }) },
                    )
                }
            }

            Text("Default meeting duration", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.meetingDurationOptions.forEach { minutes ->
                    FilterChip(
                        selected = uiState.defaultMeetingMinutes == minutes,
                        onClick = { viewModel.updateMeetingMinutes(minutes) },
                        label = { Text("${minutes} min") },
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::saveProfile,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isSaving) "Saving…" else "Save profile")
            }

            TextButton(
                onClick = viewModel::dismissForNow,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip for now")
            }
        }
    }
}
