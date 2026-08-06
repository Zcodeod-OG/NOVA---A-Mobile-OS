package com.nova.runtime.capability.provider

/** MVP stub providers per TDD §15 — no Android integration. */
class StubCommunicationProvider :
    StubCapabilityProvider(
        providerId = "stub-communication",
        capabilityType = "communication",
        version = "1.0.0",
        operations = setOf("send", "read", "search", "rollback"),
        permissions = setOf("nova.communication"),
    )

class StubKnowledgeProvider :
    StubCapabilityProvider(
        providerId = "stub-knowledge",
        capabilityType = "knowledge",
        version = "1.0.0",
        operations = setOf("query", "search", "summarize", "rollback"),
    )

class StubMediaProvider :
    StubCapabilityProvider(
        providerId = "stub-media",
        capabilityType = "media",
        version = "1.0.0",
        operations = setOf("search", "open", "share", "rollback"),
        permissions = setOf("nova.media.read"),
    )

class StubDeviceProvider :
    StubCapabilityProvider(
        providerId = "stub-device",
        capabilityType = "device",
        version = "1.0.0",
        operations = setOf("execute", "query", "configure", "rollback"),
    )

class StubTimeProvider :
    StubCapabilityProvider(
        providerId = "stub-time",
        capabilityType = "time",
        version = "1.0.0",
        operations = setOf("schedule", "alarm", "calendar", "query", "rollback"),
        permissions = setOf("nova.time"),
    )

class StubNotificationProvider :
    StubCapabilityProvider(
        providerId = "stub-notifications",
        capabilityType = "notifications",
        version = "1.0.0",
        operations = setOf("send", "dismiss", "query", "rollback"),
    )

fun defaultStubProviders(): List<CapabilityProvider> =
    listOf(
        StubCommunicationProvider(),
        StubKnowledgeProvider(),
        StubMediaProvider(),
        StubDeviceProvider(),
        StubTimeProvider(),
        StubNotificationProvider(),
    )
