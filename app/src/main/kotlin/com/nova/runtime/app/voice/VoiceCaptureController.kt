package com.nova.runtime.app.voice

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nova.runtime.app.ui.NovaOsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class VoiceCaptureController(
    context: Context,
    private val scope: CoroutineScope,
    private val viewModel: NovaOsViewModel,
) {
    private val appContext = context.applicationContext
    private val voiceRecorder = VoiceAudioRecorder(scope)
    private val platformSpeechRecognizer = AndroidSpeechRecognizerClient(appContext)

    fun toggle(
        whisperAvailable: Boolean,
        isProcessing: Boolean,
        isRecordingVoice: Boolean,
        hasRecordAudioPermission: Boolean,
        requestRecordAudioPermission: () -> Unit,
    ) {
        if (isProcessing) return

        if (isRecordingVoice) {
            stopRecording(whisperAvailable)
            return
        }

        if (!hasRecordAudioPermission) {
            requestRecordAudioPermission()
            return
        }

        startCapture(whisperAvailable)
    }

    fun onPermissionGranted(whisperAvailable: Boolean) {
        startCapture(whisperAvailable)
    }

    private fun stopRecording(whisperAvailable: Boolean) {
        if (whisperAvailable) {
            scope.launch {
                val audio = voiceRecorder.stop()
                viewModel.submitVoiceCommand(audio)
            }
        } else {
            platformSpeechRecognizer.stopEarly()
        }
    }

    private fun startCapture(whisperAvailable: Boolean) {
        if (whisperAvailable) {
            scope.launch {
                voiceRecorder.start().onSuccess {
                    viewModel.setVoiceRecording(true)
                }.onFailure {
                    viewModel.reportVoiceError("Microphone unavailable.")
                }
            }
            return
        }

        if (!platformSpeechRecognizer.isAvailable) {
            viewModel.reportVoiceError(
                "Speech recognition unavailable. Download whisper-tiny.onnx for offline voice, or type your command.",
            )
            return
        }

        scope.launch {
            viewModel.setVoiceRecording(
                active = true,
                listeningHint = "Device speech recognition (explicit fallback) — tap REC when done.",
            )
            platformSpeechRecognizer.listenUntilDone()
                .onSuccess { transcript ->
                    viewModel.setVoiceRecording(false)
                    viewModel.submitPlatformSpeechTranscript(transcript)
                }
                .onFailure { error ->
                    viewModel.setVoiceRecording(false)
                    viewModel.reportVoiceError(error.message ?: "Voice recognition failed.")
                }
        }
    }

    companion object {
        fun hasRecordAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
