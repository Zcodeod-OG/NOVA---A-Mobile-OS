package com.nova.runtime.ai.native.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.utils.logging.NovaLogger
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lazy ONNX Runtime session manager with thread-safe init and explicit close.
 * All inference stays on-device — no network calls.
 */
class OnnxSessionManager(
    private val modelLoader: ModelLoader,
    private val fileName: String,
    private val logger: NovaLogger,
    private val moduleTag: String = "ONNX",
) : AutoCloseable {
    private val mutex = Mutex()
    private val environmentRef = AtomicReference<OrtEnvironment?>(null)
    private val sessionRef = AtomicReference<OrtSession?>(null)
    private var loadedPath: String? = null

    private fun environment(): OrtEnvironment =
        environmentRef.get() ?: synchronized(this) {
            environmentRef.get() ?: OrtEnvironment.getEnvironment().also { environmentRef.set(it) }
        }

    val isLoaded: Boolean get() = sessionRef.get() != null

    suspend fun ensureLoaded(): OrtSession? = mutex.withLock {
        sessionRef.get()?.let { return it }

        val path = modelLoader.resolvePath(fileName)
        if (path == null) {
            logger.debug(moduleTag, "Model not available: $fileName")
            return null
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                environment().createSession(path, OrtSession.SessionOptions())
            }.also { session ->
                sessionRef.set(session)
                loadedPath = path
                logger.info(moduleTag, "Loaded ONNX model $fileName from $path")
            }
        }.getOrElse { error ->
            logger.warn(moduleTag, "Failed to load ONNX model $fileName: ${error.message}")
            null
        }
    }

    suspend fun <T> withSession(block: suspend (OrtSession) -> T): T? {
        val session = ensureLoaded() ?: return null
        return block(session)
    }

    override fun close() {
        sessionRef.getAndSet(null)?.close()
        loadedPath = null
    }

    fun loadedModelPath(): String? = loadedPath
}

/** Simple whitespace tokenizer for MVP ONNX embedding models expecting input_ids. */
object SimpleTokenizer {
    private const val MAX_TOKENS = 128
    private const val UNK = 100L
    private const val CLS = 101L
    private const val SEP = 102L

    fun encode(text: String, maxLength: Int = MAX_TOKENS): LongArray {
        val tokens = mutableListOf(CLS)
        text.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(maxLength - 2)
            .forEach { token ->
                tokens += token.hashCode().toLong().let { if (it < 0) -it else it } % 30_000 + 1_000
            }
        tokens += SEP
        return tokens.toLongArray()
    }

    fun attentionMask(inputIds: LongArray): LongArray = LongArray(inputIds.size) { 1L }

    fun tokenTypeIds(inputIds: LongArray): LongArray = LongArray(inputIds.size) { 0L }
}

object OnnxTensorUtils {
    fun createLongTensor(env: OrtEnvironment, values: LongArray, shape: LongArray): OnnxTensor {
        val buffer = LongBuffer.allocate(values.size)
        buffer.put(values)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    fun extractFloatMatrix(output: OnnxTensor): Array<FloatArray> {
        val value = output.value
        return when (value) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val matrix = value as Array<Array<FloatArray>>
                matrix[0]
            }
            is Array<*>? -> emptyArray()
            else -> emptyArray()
        }
    }

    fun extractFloatVector(output: OnnxTensor): FloatArray {
        val value = output.value
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val nested = value as Array<FloatArray>
                nested.firstOrNull() ?: floatArrayOf()
            }
            else -> floatArrayOf()
        }
    }
}

internal fun defaultEmbeddingFileName(): String = ModelAssetPaths.EMBEDDING_MODEL

internal fun defaultLlmLightFileName(): String = ModelAssetPaths.LLM_LIGHT_MODEL

internal fun defaultLlmFullFileName(): String = ModelAssetPaths.LLM_FULL_MODEL

internal fun defaultWhisperFileName(): String = ModelAssetPaths.WHISPER_MODEL
