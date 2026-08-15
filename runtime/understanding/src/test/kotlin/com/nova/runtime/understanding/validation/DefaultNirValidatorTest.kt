package com.nova.runtime.understanding.validation

import com.nova.runtime.models.Nir
import com.nova.runtime.understanding.nir.DefaultNirGenerator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultNirValidatorTest {

    private val validator = DefaultNirValidator()

    @Test
    fun validate_acceptsWellFormedNir() {
        val result = validator.validate(validNir())

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validate_rejectsBlankGoal() {
        val result = validator.validate(validNir(goal = "  "))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("goal") })
    }

    @Test
    fun validate_rejectsInvalidConfidence() {
        val result = validator.validate(validNir(confidence = 1.5))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("confidence") })
    }

    @Test
    fun validate_rejectsEmptyRequiredCapabilities() {
        val result = validator.validate(validNir(requiredCapabilities = emptyList()))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("requiredCapabilities") })
    }

    @Test
    fun validate_rejectsUnsupportedVersion() {
        val result = validator.validate(validNir(version = 99))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("version") })
    }

    private fun validNir(
        version: Int = DefaultNirGenerator.NIR_VERSION,
        goal: String = "set_reminder",
        confidence: Double = 0.8,
        requiredCapabilities: List<String> = listOf("time"),
    ): Nir = Nir(
        version = version,
        goal = goal,
        entities = listOf("tomorrow"),
        constraints = mapOf("modality" to "text"),
        context = mapOf("sessionId" to "test"),
        requiredCapabilities = requiredCapabilities,
        confidence = confidence,
    )
}
