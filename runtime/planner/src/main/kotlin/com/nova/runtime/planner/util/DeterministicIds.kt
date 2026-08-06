package com.nova.runtime.planner.util

import java.util.UUID

/** Deterministic UUID generation for reproducible planning output. */
object DeterministicIds {
    fun uuid(namespace: String, key: String): UUID =
        UUID.nameUUIDFromBytes("$namespace:$key".toByteArray(Charsets.UTF_8))
}
