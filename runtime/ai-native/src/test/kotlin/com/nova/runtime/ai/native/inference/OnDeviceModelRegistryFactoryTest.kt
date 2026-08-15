package com.nova.runtime.ai.native.inference

import com.nova.runtime.ai.model.FileSystemModelLoader
import com.nova.runtime.ai.model.ModelLoadConfig
import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OnDeviceModelRegistryFactoryTest {

    @Test
    fun create_registersAllTiersWithOnnxIds() {
        val registry = OnDeviceModelRegistryFactory(
            modelLoader = FileSystemModelLoader(
                ModelLoadConfig(modelsDirectory = "/tmp/nova-on-device-models"),
            ),
            logger = NoOpRuntimeLogger(),
        ).create()

        assertEquals(
            setOf(InferenceTier.DETERMINISTIC, InferenceTier.LIGHT, InferenceTier.FULL),
            registry.registeredTiers(),
        )
        assertEquals("deterministic-v1", registry.lookup(InferenceTier.DETERMINISTIC)?.modelId)
        assertNotNull(registry.lookup(InferenceTier.LIGHT)?.modelId?.startsWith("onnx-"))
        assertNotNull(registry.lookup(InferenceTier.FULL)?.modelId?.startsWith("onnx-"))
    }
}
