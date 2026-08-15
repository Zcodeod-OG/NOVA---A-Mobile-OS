package com.nova.runtime.inference.tier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InferenceTierTest {

    @Test
    fun fromLevel_returnsMatchingTier() {
        assertEquals(InferenceTier.DETERMINISTIC, InferenceTier.fromLevel(0))
        assertEquals(InferenceTier.LIGHT, InferenceTier.fromLevel(1))
        assertEquals(InferenceTier.FULL, InferenceTier.fromLevel(2))
    }

    @Test
    fun fromLevel_unknown_returnsNull() {
        assertNull(InferenceTier.fromLevel(99))
    }

    @Test
    fun fromLevelOrDefault_fallsBackToDeterministic() {
        assertEquals(InferenceTier.DETERMINISTIC, InferenceTier.fromLevelOrDefault(99))
    }
}
