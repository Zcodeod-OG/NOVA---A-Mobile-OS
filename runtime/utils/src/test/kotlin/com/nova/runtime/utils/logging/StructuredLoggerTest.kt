package com.nova.runtime.utils.logging

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class StructuredLoggerTest {

    @Test
    fun info_recordsEntryWithTraceId() {
        val logger = StructuredLogger(minLevel = LogLevel.INFO)
        val traceId = UUID.randomUUID()
        logger.info("KERNEL", "started", traceId, 12)

        val entry = logger.entries().single()
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("KERNEL", entry.module)
        assertEquals(traceId, entry.traceId)
        assertEquals(12L, entry.durationMs)
    }

    @Test
    fun debug_isFilteredByMinLevel() {
        val logger = StructuredLogger(minLevel = LogLevel.WARN)
        logger.debug("KERNEL", "hidden")
        assertEquals(0, logger.entries().size)
    }
}
