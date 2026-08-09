package com.nova.runtime.ai.native.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.nova.runtime.ai.model.LocalLlmEngine
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MediaPipe LLM Inference backed by a local Gemma `.task` model.
 * Used only for grounded RAG over retrieved document text — never as a free-form oracle.
 */
class MediaPipeLocalLlmEngine(
    private val context: Context,
    private val modelLoader: ModelLoader,
    private val modelFileName: String = ModelAssetPaths.GEMMA_TASK_MODEL,
) : LocalLlmEngine {
    private val mutex = Mutex()
    private val inferenceRef = AtomicReference<LlmInference?>(null)

    override fun isAvailable(): Boolean {
        val file = modelFile()
        if (file.isFile && file.length() >= ModelAssetPaths.GEMMA_TASK_MIN_BYTES) return true
        return hasBundledAsset()
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String? =
        withContext(Dispatchers.Default) {
            if (prompt.isBlank()) return@withContext null
            modelLoader.resolvePath(modelFileName)
            if (!modelFile().isFile || modelFile().length() < ModelAssetPaths.GEMMA_TASK_MIN_BYTES) {
                return@withContext null
            }
            mutex.withLock {
                runCatching {
                    val llm = ensureInference() ?: return@runCatching null
                    llm.generateResponse(prompt).trim().takeIf { it.isNotBlank() }
                }.onFailure { e ->
                    Log.w(TAG, "MediaPipe generate failed: ${e.message}")
                    closeInference()
                }.getOrNull()
            }
        }

    private fun ensureInference(): LlmInference? {
        inferenceRef.get()?.let { return it }
        val path = modelFile().absolutePath
        return runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .build()
            val created = LlmInference.createFromOptions(context.applicationContext, options)
            inferenceRef.set(created)
            Log.i(TAG, "MediaPipe LLM ready path=$path")
            created
        }.onFailure { e ->
            Log.w(TAG, "MediaPipe LLM init failed: ${e.message}")
        }.getOrNull()
    }

    private fun closeInference() {
        val current = inferenceRef.getAndSet(null) ?: return
        runCatching { current.close() }
    }

    private fun modelFile(): File = File(File(context.filesDir, "models"), modelFileName)

    private fun hasBundledAsset(): Boolean =
        runCatching {
            context.assets.open("models/$modelFileName").use { }
            true
        }.getOrDefault(false)

    companion object {
        private const val TAG = "NOVA/MediaPipeLLM"
    }
}
