package com.nova.runtime.kernel.config

import com.nova.runtime.error.NovaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryConfigurationManagerTest {

    @Test
    fun get_returnsConfiguredValue() {
        val manager = InMemoryConfigurationManager(mapOf("runtime.version" to "0.1.0"))
        assertEquals("0.1.0", manager.get(KernelConfigKeys.RUNTIME_VERSION))
    }

    @Test
    fun require_throwsForMissingKey() {
        val manager = InMemoryConfigurationManager()
        assertFailsWith<NovaException> {
            manager.require("missing.key")
        }
    }

    @Test
    fun set_updatesValue() {
        val manager = InMemoryConfigurationManager()
        manager.set("runtime.log.level", "INFO")
        assertEquals("INFO", manager.require("runtime.log.level"))
    }

    @Test
    fun snapshot_returnsImmutableCopy() {
        val manager = InMemoryConfigurationManager(mapOf("a" to "1"))
        manager.set("b", "2")
        val snapshot = manager.snapshot()
        assertEquals("1", snapshot.getString("a"))
        assertEquals("2", snapshot.getString("b"))
    }
}
