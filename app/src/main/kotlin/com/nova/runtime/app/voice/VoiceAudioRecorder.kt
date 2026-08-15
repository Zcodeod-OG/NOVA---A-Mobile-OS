package com.nova.runtime.app.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Captures PCM16 mono audio suitable for on-device Whisper ASR. */
class VoiceAudioRecorder(
    private val scope: CoroutineScope,
) {
    private var audioRecord: AudioRecord? = null
    private val buffer = ByteArrayOutputStream()
    private val recordingActive = AtomicBoolean(false)
    private var readJob: Job? = null

    val isRecording: Boolean
        get() = recordingActive.get()

    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopInternal()
            buffer.reset()

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            require(minBufferSize > 0) { "AudioRecord buffer size unavailable on this device." }

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
            require(record.state == AudioRecord.STATE_INITIALIZED) {
                "Microphone unavailable."
            }

            audioRecord = record
            recordingActive.set(true)
            record.startRecording()

            readJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ByteArray(minBufferSize)
                while (recordingActive.get()) {
                    val read = record.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        synchronized(buffer) {
                            buffer.write(readBuffer, 0, read)
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            }
        }
    }

    suspend fun stop(): ByteArray = withContext(Dispatchers.IO) {
        recordingActive.set(false)
        readJob?.join()
        readJob = null
        stopInternal()
        synchronized(buffer) {
            buffer.toByteArray()
        }
    }

    fun cancel() {
        recordingActive.set(false)
        readJob?.cancel()
        readJob = null
        stopInternal()
        synchronized(buffer) {
            buffer.reset()
        }
    }

    private fun stopInternal() {
        audioRecord?.runCatching {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}
