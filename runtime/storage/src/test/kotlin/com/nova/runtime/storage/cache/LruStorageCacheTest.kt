package com.nova.runtime.storage.cache

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruStorageCacheTest {
    @Test
    fun evictsLeastImportantAndOldestEntry() =
        runTest {
            val cache = LruStorageCache<String, String>(maxSize = 2)

            cache.put("a", "alpha", importance = 1)
            cache.put("b", "beta", importance = 5)
            cache.put("c", "gamma", importance = 3)

            assertNull(cache.get("a"))
            assertEquals("beta", cache.get("b"))
            assertEquals("gamma", cache.get("c"))
        }
}
