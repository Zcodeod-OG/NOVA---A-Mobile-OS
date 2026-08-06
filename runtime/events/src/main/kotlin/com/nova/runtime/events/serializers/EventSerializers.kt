package com.nova.runtime.events.serializers

/** EMS §20 — event serialization stubs (Sprint 1+). */
interface EventSerializers {
    fun serialize(payload: Any): String
    fun deserialize(type: String, data: String): Any?
}
