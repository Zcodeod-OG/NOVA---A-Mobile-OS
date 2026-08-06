package com.nova.runtime.kernel.di

import com.nova.runtime.kernel.config.ConfigurationManager
import com.nova.runtime.utils.logging.NovaLogger
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KernelDiModuleTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun kernelModule_providesCoreDependencies() {
        val koin = startKoin { modules(kernelModule) }.koin
        val logger = koin.get<NovaLogger>()
        val configurationManager = koin.get<ConfigurationManager>()

        assertNotNull(logger)
        assertNotNull(configurationManager)
        assertEquals("0.1.0", configurationManager.get("runtime.version"))
    }

    @Test
    fun structuredLogger_recordsEntries() {
        val koin = startKoin { modules(kernelModule) }.koin
        val logger = koin.get<NovaLogger>()
        logger.info("KERNEL", "test message")
        assertEquals(1, logger.entries().size)
    }
}
