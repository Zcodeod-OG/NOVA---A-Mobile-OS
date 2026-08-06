package com.nova.runtime.inference.registry

import com.nova.runtime.inference.model.InferenceModel
import com.nova.runtime.inference.tier.InferenceTier

interface ModelRegistry {
    fun register(model: InferenceModel)
    fun lookup(tier: InferenceTier): InferenceModel?
    fun lookupByCapability(capability: String): InferenceModel?
    fun registeredTiers(): Set<InferenceTier>
    fun allModels(): List<InferenceModel>
}

class DefaultModelRegistry(
    initialModels: List<InferenceModel> = emptyList(),
) : ModelRegistry {

    private val byTier = linkedMapOf<InferenceTier, InferenceModel>()

    init {
        initialModels.forEach { register(it) }
    }

    override fun register(model: InferenceModel) {
        byTier[model.tier] = model
    }

    override fun lookup(tier: InferenceTier): InferenceModel? = byTier[tier]

    override fun lookupByCapability(capability: String): InferenceModel? =
        byTier.values.firstOrNull { capability in it.capabilities }

    override fun registeredTiers(): Set<InferenceTier> = byTier.keys.toSet()

    override fun allModels(): List<InferenceModel> = byTier.values.toList()
}
