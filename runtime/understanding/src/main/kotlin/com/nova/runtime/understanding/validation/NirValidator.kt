package com.nova.runtime.understanding.validation

import com.nova.runtime.models.Nir
import com.nova.runtime.understanding.nir.DefaultNirGenerator

data class NirValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)

interface NirValidator {
    fun validate(nir: Nir): NirValidationResult
}

class DefaultNirValidator : NirValidator {
    override fun validate(nir: Nir): NirValidationResult {
        val errors = mutableListOf<String>()

        if (nir.version <= 0) {
            errors += "version must be positive"
        }
        if (nir.version != DefaultNirGenerator.NIR_VERSION) {
            errors += "unsupported NIR version ${nir.version}"
        }
        if (nir.goal.isBlank()) {
            errors += "goal must not be blank"
        }
        if (nir.confidence !in 0.0..1.0) {
            errors += "confidence must be between 0.0 and 1.0"
        }
        if (nir.entities.any { it.isBlank() }) {
            errors += "entities must not contain blank values"
        }
        if (nir.requiredCapabilities.isEmpty()) {
            errors += "requiredCapabilities must not be empty"
        }
        if (nir.requiredCapabilities.any { it.isBlank() }) {
            errors += "requiredCapabilities must not contain blank values"
        }

        return NirValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }
}
