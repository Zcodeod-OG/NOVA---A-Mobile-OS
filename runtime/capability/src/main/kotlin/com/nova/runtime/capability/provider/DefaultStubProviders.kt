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

class StubSearchProvider :
    StubCapabilityProvider(
        providerId = "stub-search",
        capabilityType = "search",
        version = "1.0.0",
        operations = setOf("photos", "documents", "semantic", "rollback"),
        permissions = setOf("nova.search"),
        description = "Stub unified search provider",
    )

class StubWhatsappProvider :
    StubCapabilityProvider(
        providerId = "stub-whatsapp",
        capabilityType = "whatsapp",
        version = "1.0.0",
        operations = setOf("send_message", "rollback"),
        permissions = setOf("nova.communication.whatsapp"),
        description = "Stub WhatsApp messaging provider",
    )

class StubAlarmProvider :
    StubCapabilityProvider(
        providerId = "stub-alarm",
        capabilityType = "alarm",
        version = "1.0.0",
        operations = setOf("create", "cancel", "rollback"),
        permissions = setOf("nova.time.alarm"),
        description = "Stub alarm provider",
    )

class StubCalendarProvider :
    StubCapabilityProvider(
        providerId = "stub-calendar",
        capabilityType = "calendar",
        version = "1.0.0",
        operations = setOf("create", "read", "modify", "delete", "rollback"),
        permissions = setOf("nova.time.calendar"),
        description = "Stub calendar provider",
    )

class StubContactsProvider :
    StubCapabilityProvider(
        providerId = "stub-contacts",
        capabilityType = "contacts",
        version = "1.0.0",
        operations = setOf("search", "retrieve", "rollback"),
        permissions = setOf("nova.contacts.read"),
        description = "Stub contacts provider",
    )

class StubShareProvider :
    StubCapabilityProvider(
        providerId = "stub-share",
        capabilityType = "share",
        version = "1.0.0",
        operations = setOf("file", "rollback"),
        permissions = setOf("nova.share"),
        description = "Stub file sharing provider",
    )

fun defaultStubProviders(): List<CapabilityProvider> =
    listOf(
        StubCommunicationProvider(),
        StubKnowledgeProvider(),
        StubMediaProvider(),
        StubDeviceProvider(),
        StubTimeProvider(),
        StubNotificationProvider(),
        StubSearchProvider(),
        StubWhatsappProvider(),
        StubAlarmProvider(),
        StubCalendarProvider(),
        StubContactsProvider(),
        StubShareProvider(),
    )
