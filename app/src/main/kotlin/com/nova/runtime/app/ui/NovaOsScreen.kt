package com.nova.runtime.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.runtime.app.ui.components.ActivityStream
import com.nova.runtime.app.ui.components.CommandBar
import com.nova.runtime.app.ui.components.ContentDetailSheet
import com.nova.runtime.app.ui.components.ModelDownloadOverlay
import com.nova.runtime.app.ui.components.SystemHeader
import com.nova.runtime.app.ui.theme.NovaColors
import com.nova.runtime.app.voice.VoiceCaptureController
import com.nova.runtime.app.ui.onboarding.ProfileOnboardingScreen
import com.nova.runtime.app.ui.onboarding.ProfileOnboardingViewModel
import com.nova.runtime.ai.model.ModelDownloadPhase
import org.koin.androidx.compose.koinViewModel

@Composable
fun NovaOsScreen(
    viewModel: NovaOsViewModel,
    modifier: Modifier = Modifier,
    onboardingViewModel: ProfileOnboardingViewModel = koinViewModel(),
) {
    val lifecycleState by viewModel.lifecycleState.collectAsState()
    val activityFeed by viewModel.activityFeed.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val voiceStatusMessage by viewModel.voiceStatusMessage.collectAsState()
    val whisperAvailable by viewModel.whisperAvailable.collectAsState()
    val modelDownloadState by viewModel.modelDownloadState.collectAsState()
    val indexingStatus by viewModel.indexingStatus.collectAsState()
    val contentDetail by viewModel.contentDetail.collectAsState()
    val showOnboarding by onboardingViewModel.showOnboarding.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceCapture = remember(scope, viewModel) {
        VoiceCaptureController(context, scope, viewModel)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceCapture.onPermissionGranted(whisperAvailable)
        } else {
            viewModel.reportVoiceError("Microphone permission denied.")
        }
    }

    fun toggleVoiceRecording() {
        voiceCapture.toggle(
            whisperAvailable = whisperAvailable,
            isProcessing = isProcessing,
            isRecordingVoice = isRecordingVoice,
            hasRecordAudioPermission = VoiceCaptureController.hasRecordAudioPermission(context),
            requestRecordAudioPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SystemHeader(
                lifecycleState = lifecycleState,
                indexingStatus = indexingStatus,
            )

            Spacer(modifier = Modifier.height(28.dp))

            CommandBar(
                onCommandSubmit = viewModel::submitCommand,
                onMicToggle = ::toggleVoiceRecording,
                enabled = !isProcessing,
                isRecording = isRecordingVoice,
                voiceStatusMessage = voiceStatusMessage,
            )

            Spacer(modifier = Modifier.height(20.dp))

            ModelDownloadOverlay(
                session = modelDownloadState,
                onRetry = viewModel::retryModelDownloads,
            )

            if (modelDownloadState.showOverlay ||
                modelDownloadState.phase == ModelDownloadPhase.FAILED ||
                modelDownloadState.phase == ModelDownloadPhase.OFFLINE
            ) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            ActivityStream(
                activities = activityFeed,
                onExpandContent = viewModel::showContentDetail,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp),
            )
        }

        ContentDetailSheet(
            state = contentDetail,
            onDismiss = viewModel::dismissContentDetail,
        )

        if (showOnboarding) {
            ProfileOnboardingScreen(viewModel = onboardingViewModel)
        }
    }
}

@Composable
private fun ambientBackgroundBrush(): Brush {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                NovaColors.background,
                NovaColors.surface.copy(alpha = 0.95f),
                NovaColors.background,
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                NovaColors.background,
                NovaColors.accent.copy(alpha = 0.04f),
                NovaColors.background,
            ),
        )
    }
}
