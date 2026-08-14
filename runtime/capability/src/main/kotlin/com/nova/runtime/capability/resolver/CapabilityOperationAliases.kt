package com.nova.runtime.capability.resolver

import com.nova.runtime.models.NovaCapabilityOperations

/**
 * Maps pipeline-style capability requests onto provider registration keys.
 * Pipeline uses [NovaCapabilityOperations] short form (`whatsapp` + `send_message`);
 * Android providers use parent types with qualified operations (`communication` + `whatsapp.send_message`);
 * search providers use dotted types with a single `search` operation (`search.photos` + `search`).
 */
object CapabilityOperationAliases {
    data class LookupKey(
        val capabilityType: String,
        val operation: String,
    )

    fun variants(capabilityType: String, operation: String): List<LookupKey> {
        val qualified = "$capabilityType.$operation"
        val pipelineKey = LookupKey(capabilityType, operation)
        val androidKeys = mutableListOf<LookupKey>()
        val extraKeys = mutableListOf<LookupKey>()

        when (qualified) {
            NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE ->
                androidKeys += LookupKey("communication", NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE)
            NovaCapabilityOperations.CONTACTS_SEARCH ->
                androidKeys += LookupKey("communication", NovaCapabilityOperations.CONTACTS_SEARCH)
            NovaCapabilityOperations.ALARM_CREATE ->
                androidKeys += LookupKey("time", NovaCapabilityOperations.ALARM_CREATE)
            NovaCapabilityOperations.CALENDAR_CREATE ->
                androidKeys += LookupKey("time", NovaCapabilityOperations.CALENDAR_CREATE)
            NovaCapabilityOperations.CALENDAR_READ ->
                androidKeys += LookupKey("time", NovaCapabilityOperations.CALENDAR_READ)
            NovaCapabilityOperations.SHARE_FILE ->
                androidKeys += LookupKey("media", NovaCapabilityOperations.SHARE_FILE)
            NovaCapabilityOperations.SEARCH_PHOTOS ->
                androidKeys += LookupKey("search.photos", "search")
            NovaCapabilityOperations.SEARCH_DOCUMENTS ->
                androidKeys += LookupKey("search.documents", "search")
            NovaCapabilityOperations.SEARCH_SEMANTIC ->
                androidKeys += LookupKey("search.semantic", "search")
            NovaCapabilityOperations.DEVICE_OPEN_APP ->
                androidKeys += LookupKey("device", NovaCapabilityOperations.DEVICE_OPEN_APP)
            NovaCapabilityOperations.DEVICE_APP_SEARCH ->
                androidKeys += LookupKey("device", NovaCapabilityOperations.DEVICE_APP_SEARCH)
            NovaCapabilityOperations.EMAIL_READ ->
                androidKeys += LookupKey("email", NovaCapabilityOperations.EMAIL_READ)
            NovaCapabilityOperations.EMAIL_SEARCH ->
                androidKeys += LookupKey("email", NovaCapabilityOperations.EMAIL_SEARCH)
        }

        if (operation.contains('.')) {
            extraKeys += LookupKey(capabilityType, operation)
        } else if (qualified != operation) {
            extraKeys += LookupKey(capabilityType, qualified)
        }

        // Prefer Android-mapped keys first so real providers beat overlapping stubs.
        return (androidKeys + extraKeys + listOf(pipelineKey)).distinct()
    }
}
