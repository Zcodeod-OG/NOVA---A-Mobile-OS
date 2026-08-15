package com.nova.runtime.android

import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapterStub
import com.nova.runtime.android.alarmAdapter.AlarmAdapterStub
import com.nova.runtime.android.calendarAdapter.CalendarAdapterStub
import com.nova.runtime.android.contactsAdapter.ContactsAdapterStub
import com.nova.runtime.android.intentAdapter.IntentAdapterStub
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapterStub
import com.nova.runtime.android.notificationAdapter.NotificationAdapterStub
import com.nova.runtime.android.ocrAdapter.OcrAdapterStub
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapterStub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAdapterLayerImplTest {
    private val layer =
        AndroidAdapterLayerImpl(
            intents = IntentAdapterStub(),
            contacts = ContactsAdapterStub(),
            calendar = CalendarAdapterStub(),
            alarms = AlarmAdapterStub(),
            mediaStore = MediaStoreAdapterStub(),
            notifications = NotificationAdapterStub(),
            accessibility = AccessibilityAdapterStub(),
            storageAccess = StorageAccessAdapterStub(),
            ocr = OcrAdapterStub(),
        )

    @Test
    fun adapterNames_listsAllAdapters() {
        val names = layer.adapterNames()
        assertEquals(9, names.size)
        assertTrue(names.contains("Accessibility"))
        assertTrue(names.contains("StorageAccess"))
        assertTrue(names.contains("OCR"))
    }

    @Test
    fun facadeExposesAllAdapters() {
        assertTrue(layer.intents.supportedOperations().isNotEmpty())
        assertTrue(layer.contacts.supportedOperations().isNotEmpty())
        assertTrue(layer.calendar.supportedOperations().isNotEmpty())
        assertTrue(layer.alarms.supportedOperations().isNotEmpty())
        assertTrue(layer.mediaStore.supportedOperations().isNotEmpty())
        assertTrue(layer.notifications.supportedOperations().isNotEmpty())
        assertTrue(layer.accessibility.supportedOperations().isNotEmpty())
        assertTrue(layer.storageAccess.supportedOperations().isNotEmpty())
    }
}
