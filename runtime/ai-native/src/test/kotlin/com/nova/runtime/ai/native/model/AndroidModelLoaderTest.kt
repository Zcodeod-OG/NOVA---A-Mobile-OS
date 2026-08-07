package com.nova.runtime.ai.native.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoadConfig
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidModelLoaderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun isAvailable_doesNotCopyFromAssetsWhenMissing() = runTest {
        val modelsDir = File(context.cacheDir, "nova-models-no-copy")
        modelsDir.deleteRecursively()
        val loader = AndroidModelLoader(
            context = context,
            config = ModelLoadConfig(
                modelsDirectory = modelsDir.absolutePath,
                copyFromAssetsOnFirstLaunch = true,
            ),
        )

        assertFalse(loader.isAvailable(ModelAssetPaths.EMBEDDING_MODEL))
        assertFalse(File(modelsDir, ModelAssetPaths.EMBEDDING_MODEL).exists())
    }

    @Test
    fun availabilityReport_listsAllModelsWithoutCopyingAssets() = runTest {
        val modelsDir = File(context.cacheDir, "nova-models-report")
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
        File(modelsDir, ModelAssetPaths.EMBEDDING_MODEL).writeText("stub")
        val loader = AndroidModelLoader(
            context = context,
            config = ModelLoadConfig(
                modelsDirectory = modelsDir.absolutePath,
                copyFromAssetsOnFirstLaunch = true,
            ),
        )

        val report = loader.availabilityReport()

        assertEquals(ModelAssetPaths.ALL_REQUIRED.size, report.size)
        assertTrue(report.first { it.fileName == ModelAssetPaths.EMBEDDING_MODEL }.available)
        assertFalse(report.first { it.fileName == ModelAssetPaths.LLM_LIGHT_MODEL }.available)
        assertFalse(File(modelsDir, ModelAssetPaths.LLM_LIGHT_MODEL).exists())
    }

    @Test
    fun resolvePath_copiesFromAssetsOnFirstUse() = runTest {
        val assetPath = "models/${ModelAssetPaths.EMBEDDING_MODEL}"
        if (runCatching { context.assets.open(assetPath).close() }.isFailure) {
            return@runTest
        }

        val modelsDir = File(context.cacheDir, "nova-models-resolve")
        modelsDir.deleteRecursively()
        val loader = AndroidModelLoader(
            context = context,
            config = ModelLoadConfig(
                modelsDirectory = modelsDir.absolutePath,
                copyFromAssetsOnFirstLaunch = true,
            ),
        )

        val resolved = loader.resolvePath(ModelAssetPaths.EMBEDDING_MODEL)

        assertTrue(resolved != null)
        assertTrue(File(resolved!!).isFile)
    }
}
