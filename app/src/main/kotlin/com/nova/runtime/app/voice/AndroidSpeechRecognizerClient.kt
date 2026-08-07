package com.nova.runtime.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Uses the platform speech recognizer when ONNX Whisper is unavailable.
 * Requires network on most devices (Google speech services).
 */
class AndroidSpeechRecognizerClient(
    private val context: Context,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeResult: CompletableDeferred<Result<String>>? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listenUntilDone(): Result<String> = withContext(Dispatchers.Main) {
        if (!isAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Speech recognition is not available on this device."),
            )
        }

        release()

        val deferred = CompletableDeferred<Result<String>>()
        activeResult = deferred

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    complete(
                        Result.failure(
                            IllegalStateException(speechErrorMessage(error)),
                        ),
                    )
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (text.isNullOrBlank()) {
                        complete(Result.failure(IllegalStateException("No speech detected. Try again.")))
                    } else {
                        complete(Result.success(text))
                    }
                }
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)

        withTimeoutOrNull(LISTEN_TIMEOUT_MS) { deferred.await() }
            ?: Result.failure(IllegalStateException("Speech recognition timed out. Try again."))
    }.also {
        release()
        activeResult = null
    }

    fun stopEarly() {
        speechRecognizer?.stopListening()
    }

    fun cancel() {
        activeResult?.complete(Result.failure(IllegalStateException("Voice input cancelled.")))
        speechRecognizer?.cancel()
        release()
        activeResult = null
    }

    private fun complete(result: Result<String>) {
        activeResult?.complete(result)
        release()
    }

    private fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error. Check permissions and try again."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "Network required for speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Speak clearly and try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy. Try again."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap MIC and try again."
        else -> "Speech recognition failed (code $error)."
    }

    companion object {
        private const val LISTEN_TIMEOUT_MS = 30_000L
    }
}
