package com.nova.runtime.inference.registry

import com.nova.runtime.inference.model.DeterministicInferenceModel
import com.nova.runtime.inference.model.LightweightInferenceModel
import com.nova.runtime.inference.model.RuleEngineInferenceModel
import com.nova.runtime.inference.tier.InferenceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultModelRegistryTest {

    @Test
    fun registerAndLookup_byTier() {
        val registry = DefaultModelRegistry(
            initialModels = listOf(
                DeterministicInferenceModel(),
                RuleEngineInferenceModel(),
                LightweightInferenceModel(),
            ),
        )

        assertEquals(setOf(InferenceTier.DETERMINISTIC, InferenceTier.LIGHT, InferenceTier.FULL), registry.registeredTiers())
        assertEquals("deterministic-v1", registry.lookup(InferenceTier.DETERMINISTIC)?.modelId)
        assertEquals("rule-engine-v1", registry.lookup(InferenceTier.LIGHT)?.modelId)
        assertEquals("lightweight-v1", registry.lookup(InferenceTier.FULL)?.modelId)
    }

    @Test
    fun lookupByCapability_returnsFirstMatch() {
        val registry = DefaultModelRegistry(
            initialModels = listOf(
                DeterministicInferenceModel(),
                RuleEngineInferenceModel(),
            ),
        )

        val model = registry.lookupByCapability("intent_hint")
        assertNotNull(model)
        assertEquals(InferenceTier.LIGHT, model.tier)
    }

    @Test
    fun register_replacesExistingTierModel() {
        val registry = DefaultModelRegistry()
        val first = DeterministicInferenceModel()
        val replacement = DeterministicInferenceModel()

        registry.register(first)
        registry.register(replacement)

        assertEquals(1, registry.allModels().size)
        assertEquals(replacement, registry.lookup(InferenceTier.DETERMINISTIC))
    }

    @Test
    fun lookup_unknownTier_returnsNull() {
        val registry = DefaultModelRegistry()
        assertNull(registry.lookup(InferenceTier.FULL))
    }
}
