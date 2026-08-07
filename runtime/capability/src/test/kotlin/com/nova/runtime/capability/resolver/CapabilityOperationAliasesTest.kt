package com.nova.runtime.capability.resolver

import com.nova.runtime.models.NovaCapabilityOperations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapabilityOperationAliasesTest {

    @Test
    fun variants_whatsappMessage_includesAndroidCommunicationKey() {
        val variants = CapabilityOperationAliases.variants("whatsapp", "send_message")
        assertTrue(
            variants.contains(
                CapabilityOperationAliases.LookupKey(
                    "communication",
                    NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE,
                ),
            ),
        )
    }

    @Test
    fun variants_searchPhotos_includesDottedSearchProviderKey() {
        val variants = CapabilityOperationAliases.variants("search", "photos")
        assertTrue(
            variants.contains(
                CapabilityOperationAliases.LookupKey("search.photos", "search"),
            ),
        )
    }

    @Test
    fun variants_alarmCreate_includesTimeProviderKey() {
        val variants = CapabilityOperationAliases.variants("alarm", "create")
        assertTrue(
            variants.contains(
                CapabilityOperationAliases.LookupKey("time", NovaCapabilityOperations.ALARM_CREATE),
            ),
        )
    }

    @Test
    fun variants_prefersAndroidMappedKeyFirst() {
        val variants = CapabilityOperationAliases.variants("whatsapp", "send_message")
        assertEquals(
            CapabilityOperationAliases.LookupKey(
                "communication",
                NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE,
            ),
            variants.first(),
        )
    }
}
