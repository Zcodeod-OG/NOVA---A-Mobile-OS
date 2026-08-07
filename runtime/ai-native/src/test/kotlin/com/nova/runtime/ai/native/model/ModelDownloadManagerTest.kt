package com.nova.runtime.ai.native.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelDownloadPhase
import com.nova.runtime.ai.model.ModelLoadConfig
import com.nova.runtime.ai.native.model.AndroidModelLoader
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
class ModelDownloadManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun ensureAllModels_goesOfflineWhenNetworkUnavailable() = runTest {
        val modelsDir = File(context.cacheDir, "nova-model-download-offline")
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()

        val manager = AndroidModelDownloadManager(
            context = context,
            modelLoader = AndroidModelLoader(
                context = context,
                config = ModelLoadConfig(modelsDirectory = modelsDir.absolutePath),
            ),
            connectivity = NetworkAvailabilityChecker { false },
            modelsDirectory = modelsDir,
        )

        manager.ensureAllModels()

        val state = manager.state.value
        assertEquals(ModelDownloadPhase.OFFLINE, state.phase)
        assertFalse(state.readiness.inferenceLightReady)
        assertTrue(state.message?.contains("No network") == true)
    }

    @Test
    fun ensureAllModels_recognizesExistingSmallModels() = runTest {
        val modelsDir = File(context.cacheDir, "nova-model-download-existing")
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()

        File(modelsDir, ModelAssetPaths.EMBEDDING_MODEL).writeBytes(ByteArray(1_100_000) { 1 })
        File(modelsDir, ModelAssetPaths.WHISPER_MODEL).writeBytes(ByteArray(11_000_000) { 2 })

        val manager = AndroidModelDownloadManager(
            context = context,
            modelLoader = AndroidModelLoader(
                context = context,
                config = ModelLoadConfig(modelsDirectory = modelsDir.absolutePath),
            ),
            connectivity = NetworkAvailabilityChecker { false },
            modelsDirectory = modelsDir,
        )

        manager.ensureAllModels()

        val state = manager.state.value
        assertEquals(ModelDownloadPhase.OFFLINE, state.phase)
        assertTrue(state.readiness.coreReady)
        assertTrue(state.readiness.voiceReady)
        assertFalse(state.readiness.inferenceLightReady)
        assertFalse(state.readiness.inferenceFullReady)
    }
}
