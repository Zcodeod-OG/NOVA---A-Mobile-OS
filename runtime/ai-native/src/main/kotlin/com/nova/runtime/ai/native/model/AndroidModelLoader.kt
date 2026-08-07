package com.nova.runtime.ai.native.model

import android.content.Context
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelAvailability
import com.nova.runtime.ai.model.ModelLoadConfig
import com.nova.runtime.ai.model.ModelLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android model loader — resolves files from app filesDir/models with optional asset copy. */
class AndroidModelLoader(
    private val context: Context,
    private val config: ModelLoadConfig = ModelLoadConfig(
        modelsDirectory = File(context.filesDir, "models").absolutePath,
        copyFromAssetsOnFirstLaunch = true,
    ),
) : ModelLoader {

    private val modelsDir: File
        get() = File(config.modelsDirectory).also { it.mkdirs() }

    override suspend fun resolvePath(fileName: String): String? = withContext(Dispatchers.IO) {
        localModelFile(fileName)?.absolutePath ?: run {
            if (config.copyFromAssetsOnFirstLaunch) {
                copyAssetIfPresent(fileName, localModelFile(fileName) ?: File(modelsDir, fileName))
            }
            localModelFile(fileName)?.absolutePath
        }
    }

    override suspend fun isAvailable(fileName: String): Boolean = withContext(Dispatchers.IO) {
        localModelFile(fileName) != null
    }

    override suspend fun availabilityReport(): List<ModelAvailability> = withContext(Dispatchers.IO) {
        ModelAssetPaths.ALL_REQUIRED.map { fileName ->
            val target = File(modelsDir, fileName)
            ModelAvailability(
                fileName = fileName,
                path = target.absolutePath,
                available = target.isFile,
                sizeBytes = if (target.isFile) target.length() else 0L,
            )
        }
    }

    override suspend fun ensureModelsFromAssets(fileNames: List<String>): List<ModelAvailability> =
        withContext(Dispatchers.IO) {
            fileNames.forEach { fileName ->
                if (localModelFile(fileName) == null) {
                    copyAssetIfPresent(fileName, File(modelsDir, fileName))
                }
            }
            availabilityReport().filter { it.fileName in fileNames }
        }

    private fun localModelFile(fileName: String): File? {
        val target = File(modelsDir, fileName)
        return target.takeIf { it.isFile }
    }

    private fun copyAssetIfPresent(fileName: String, target: File) {
        runCatching {
            context.assets.open("models/$fileName").use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
