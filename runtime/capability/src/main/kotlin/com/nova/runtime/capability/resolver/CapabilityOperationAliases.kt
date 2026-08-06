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
        val keys = linkedSetOf(LookupKey(capabilityType, operation))

        when (qualified) {
            NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE ->
                keys.add(LookupKey("communication", NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE))
            NovaCapabilityOperations.CONTACTS_SEARCH ->
                keys.add(LookupKey("communication", NovaCapabilityOperations.CONTACTS_SEARCH))
            NovaCapabilityOperations.ALARM_CREATE ->
                keys.add(LookupKey("time", NovaCapabilityOperations.ALARM_CREATE))
            NovaCapabilityOperations.CALENDAR_CREATE ->
                keys.add(LookupKey("time", NovaCapabilityOperations.CALENDAR_CREATE))
            NovaCapabilityOperations.SHARE_FILE ->
                keys.add(LookupKey("media", NovaCapabilityOperations.SHARE_FILE))
            NovaCapabilityOperations.SEARCH_PHOTOS ->
                keys.add(LookupKey("search.photos", "search"))
            NovaCapabilityOperations.SEARCH_DOCUMENTS ->
                keys.add(LookupKey("search.documents", "search"))
            NovaCapabilityOperations.SEARCH_SEMANTIC ->
                keys.add(LookupKey("search.semantic", "search"))
        }

        if (operation.contains('.')) {
            keys.add(LookupKey(capabilityType, operation))
        } else if (qualified != operation) {
            keys.add(LookupKey(capabilityType, qualified))
        }

        return keys.toList()
    }
}
