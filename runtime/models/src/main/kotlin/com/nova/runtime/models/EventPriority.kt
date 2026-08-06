package com.nova.runtime.models

/**
 * Event priority per EMS §8. Affects scheduling only, not semantics.
 */
enum class EventPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    IDLE,
}
