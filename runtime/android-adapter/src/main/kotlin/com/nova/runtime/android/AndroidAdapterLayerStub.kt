package com.nova.runtime.android

import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapter
import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapterStub
import com.nova.runtime.android.alarmAdapter.AlarmAdapter
import com.nova.runtime.android.alarmAdapter.AlarmAdapterStub
import com.nova.runtime.android.calendarAdapter.CalendarAdapter
import com.nova.runtime.android.calendarAdapter.CalendarAdapterStub
import com.nova.runtime.android.contactsAdapter.ContactsAdapter
import com.nova.runtime.android.contactsAdapter.ContactsAdapterStub
import com.nova.runtime.android.intentAdapter.IntentAdapter
import com.nova.runtime.android.intentAdapter.IntentAdapterStub
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapter
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapterStub
import com.nova.runtime.android.notificationAdapter.NotificationAdapter
import com.nova.runtime.android.notificationAdapter.NotificationAdapterStub
import com.nova.runtime.android.ocrAdapter.OcrAdapter
import com.nova.runtime.android.ocrAdapter.OcrAdapterStub
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapter
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapterStub

/** Test / offline stub registry — no Android API access. */
class AndroidAdapterLayerStub : AndroidAdapterLayer {
    override val intents: IntentAdapter = IntentAdapterStub()
    override val contacts: ContactsAdapter = ContactsAdapterStub()
    override val calendar: CalendarAdapter = CalendarAdapterStub()
    override val alarms: AlarmAdapter = AlarmAdapterStub()
    override val mediaStore: MediaStoreAdapter = MediaStoreAdapterStub()
    override val notifications: NotificationAdapter = NotificationAdapterStub()
    override val accessibility: AccessibilityAdapter = AccessibilityAdapterStub()
    override val storageAccess: StorageAccessAdapter = StorageAccessAdapterStub()
    override val ocr: OcrAdapter = OcrAdapterStub()

    override fun adapterNames(): List<String> =
        listOf(
            "Intent",
            "MediaStore",
            "Contacts",
            "Calendar",
            "Alarm",
            "Notification",
            "Accessibility",
            "StorageAccess",
            "OCR",
        )
}
