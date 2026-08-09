package com.nova.runtime.models

/**
 * Canonical capability operation identifiers for NOVA user features.
 * Format: `{capabilityType}.{operation}` — parsed by [NovaCapabilityOperationResolver].
 */
object NovaCapabilityOperations {
    const val WHATSAPP_SEND_MESSAGE = "whatsapp.send_message"
    const val SEARCH_PHOTOS = "search.photos"
    const val SEARCH_DOCUMENTS = "search.documents"
    const val ALARM_CREATE = "alarm.create"
    const val CALENDAR_CREATE = "calendar.create"
    const val CONTACTS_SEARCH = "contacts.search"
    const val SEARCH_SEMANTIC = "search.semantic"
    const val SHARE_FILE = "share.file"
    const val DEVICE_OPEN_APP = "device.open_app"
    const val DEVICE_APP_SEARCH = "device.app_search"

    val ALL: Set<String> = setOf(
        WHATSAPP_SEND_MESSAGE,
        SEARCH_PHOTOS,
        SEARCH_DOCUMENTS,
        ALARM_CREATE,
        CALENDAR_CREATE,
        CONTACTS_SEARCH,
        SEARCH_SEMANTIC,
        SHARE_FILE,
        DEVICE_OPEN_APP,
        DEVICE_APP_SEARCH,
    )

    fun forIntent(intentType: String): String? = when (intentType) {
        "send_document_whatsapp" -> WHATSAPP_SEND_MESSAGE
        "send_whatsapp_message", "send_message" -> WHATSAPP_SEND_MESSAGE
        "search_photos" -> SEARCH_PHOTOS
        "search_documents", "document_question" -> SEARCH_DOCUMENTS
        "set_alarm", "set_reminder" -> ALARM_CREATE
        "create_calendar_event", "manage_calendar" -> CALENDAR_CREATE
        "lookup_contact" -> CONTACTS_SEARCH
        "semantic_search" -> SEARCH_SEMANTIC
        "share_file" -> SHARE_FILE
        "open_application" -> DEVICE_OPEN_APP
        "app_action_search" -> DEVICE_APP_SEARCH
        else -> null
    }
}

data class ResolvedCapabilityOperation(
    val capabilityType: String,
    val operation: String,
    val qualifiedName: String,
)

object NovaCapabilityOperationResolver {
    fun resolve(qualifiedName: String): ResolvedCapabilityOperation {
        val dotIndex = qualifiedName.indexOf('.')
        require(dotIndex > 0 && dotIndex < qualifiedName.lastIndex) {
            "Invalid capability operation: $qualifiedName"
        }
        return ResolvedCapabilityOperation(
            capabilityType = qualifiedName.substring(0, dotIndex),
            operation = qualifiedName.substring(dotIndex + 1),
            qualifiedName = qualifiedName,
        )
    }
}
