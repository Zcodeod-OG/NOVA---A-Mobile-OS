package com.nova.runtime.kernel.config

import com.nova.runtime.kernel.config.KernelConfigKeys.EVENT_BUS_ASYNC
import com.nova.runtime.kernel.config.KernelConfigKeys.LOG_LEVEL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultConfigurationValidatorTest {

    private val validator = DefaultConfigurationValidator()

    @Test
    fun validate_acceptsDefaultKernelConfiguration() {
        val errors = validator.validate(
            RuntimeConfiguration(
                mapOf(
                    KernelConfigKeys.RUNTIME_VERSION to "0.1.0",
                    LOG_LEVEL to "DEBUG",
                    EVENT_BUS_ASYNC to "true",
                ),
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateEntry_rejectsInvalidLogLevel() {
        val error = validator.validateEntry(LOG_LEVEL, "NOT_A_LEVEL")
        assertEquals("CONFIG_INVALID_VALUE", error?.code)
    }

    @Test
    fun validateEntry_acceptsKnownKeys() {
        assertNull(validator.validateEntry(EVENT_BUS_ASYNC, "false"))
    }
}
