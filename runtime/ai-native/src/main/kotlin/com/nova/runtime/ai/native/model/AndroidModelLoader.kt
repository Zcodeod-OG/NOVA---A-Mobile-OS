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
        val target = File(modelsDir, fileName)
        if (target.isFile) {
            return@withContext target.absolutePath
        }
        if (config.copyFromAssetsOnFirstLaunch) {
            copyAssetIfPresent(fileName, target)
        }
        target.absolutePath.takeIf { target.isFile }
    }

    override suspend fun isAvailable(fileName: String): Boolean = resolvePath(fileName) != null

    override suspend fun availabilityReport(): List<ModelAvailability> =
        ModelAssetPaths.ALL_REQUIRED.map { fileName ->
            val target = File(modelsDir, fileName)
            ModelAvailability(
                fileName = fileName,
                path = target.absolutePath,
                available = target.isFile,
                sizeBytes = if (target.isFile) target.length() else 0L,
            )
        }

    override suspend fun ensureModelsFromAssets(fileNames: List<String>): List<ModelAvailability> =
        withContext(Dispatchers.IO) {
            fileNames.forEach { fileName ->
                val target = File(modelsDir, fileName)
                if (!target.isFile) {
                    copyAssetIfPresent(fileName, target)
                }
            }
            availabilityReport().filter { it.fileName in fileNames }
        }

    private fun copyAssetIfPresent(fileName: String, target: File) {
        runCatching {
            context.assets.open("models/$fileName").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
