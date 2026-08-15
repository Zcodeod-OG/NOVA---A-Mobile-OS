package com.nova.runtime.ai.model

/**
 * Platform-agnostic model file resolution and availability checks.
 *
 * [isAvailable] and [availabilityReport] must not load ONNX sessions or copy large assets;
 * use [resolvePath] for lazy first-use resolution (including optional asset copy).
 */
interface ModelLoader {
    suspend fun resolvePath(fileName: String): String?

    suspend fun isAvailable(fileName: String): Boolean

    suspend fun availabilityReport(): List<ModelAvailability>

    /** Copies bundled assets for [fileNames] only when missing on disk. Not for startup use. */
    suspend fun ensureModelsFromAssets(fileNames: List<String> = ModelAssetPaths.ALL_REQUIRED): List<ModelAvailability>
}

/** JVM/test implementation — models must exist at configured directory. */
class FileSystemModelLoader(
    private val config: ModelLoadConfig,
) : ModelLoader {

    override suspend fun resolvePath(fileName: String): String? {
        val path = "${config.modelsDirectory.trimEnd('/')}/$fileName"
        val file = java.io.File(path)
        return path.takeIf { file.isFile }
    }

    override suspend fun isAvailable(fileName: String): Boolean = resolvePath(fileName) != null

    override suspend fun availabilityReport(): List<ModelAvailability> =
        ModelAssetPaths.ALL_REQUIRED.map { fileName ->
            val path = "${config.modelsDirectory.trimEnd('/')}/$fileName"
            val file = java.io.File(path)
            ModelAvailability(
                fileName = fileName,
                path = path,
                available = file.isFile,
                sizeBytes = if (file.isFile) file.length() else 0L,
            )
        }

    override suspend fun ensureModelsFromAssets(fileNames: List<String>): List<ModelAvailability> =
        availabilityReport().filter { it.fileName in fileNames }
}
