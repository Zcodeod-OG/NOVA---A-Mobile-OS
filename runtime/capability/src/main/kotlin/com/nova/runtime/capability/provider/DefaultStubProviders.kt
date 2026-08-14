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
        operations = setOf(
            "execute", "query", "configure", "rollback",
            "open_app", "device.open_app", "app_search", "device.app_search",
        ),
    )

class StubTimeProvider :
    StubCapabilityProvider(
        providerId = "stub-time",
        capabilityType = "time",
        version = "1.0.0",
        operations = setOf(
            "schedule",
            "alarm",
            "calendar",
            "query",
            "rollback",
            com.nova.runtime.models.NovaCapabilityOperations.ALARM_CREATE,
            com.nova.runtime.models.NovaCapabilityOperations.CALENDAR_READ,
            com.nova.runtime.models.NovaCapabilityOperations.CALENDAR_CREATE,
        ),
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

/**
 * Matches real DocumentSearchCapabilityProvider (`search.documents` + `search`) and
 * also accepts the legacy pipeline op name (`documents`) used when NIR resolves
 * `search.documents` into capabilityType=search / operation=documents.
 */
class StubDocumentSearchProvider :
    StubCapabilityProvider(
        providerId = "stub-search-documents",
        capabilityType = "search.documents",
        version = "1.0.0",
        operations = setOf("search", "documents", "rollback"),
        permissions = setOf("nova.search"),
        description = "Stub document search provider",
    )

class StubPhotoSearchProvider :
    StubCapabilityProvider(
        providerId = "stub-search-photos",
        capabilityType = "search.photos",
        version = "1.0.0",
        operations = setOf("search", "photos", "rollback"),
        permissions = setOf("nova.search"),
        description = "Stub photo search provider",
    )

class StubSemanticSearchProvider :
    StubCapabilityProvider(
        providerId = "stub-search-semantic",
        capabilityType = "search.semantic",
        version = "1.0.0",
        operations = setOf("search", "semantic", "rollback"),
        permissions = setOf("nova.search"),
        description = "Stub semantic search provider",
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
        StubDocumentSearchProvider(),
        StubPhotoSearchProvider(),
        StubSemanticSearchProvider(),
        StubWhatsappProvider(),
        StubAlarmProvider(),
        StubCalendarProvider(),
        StubContactsProvider(),
        StubShareProvider(),
    )
