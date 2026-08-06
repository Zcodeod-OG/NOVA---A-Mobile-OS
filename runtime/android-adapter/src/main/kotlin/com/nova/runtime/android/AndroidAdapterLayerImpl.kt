package com.nova.runtime.android

import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapter
import com.nova.runtime.android.alarmAdapter.AlarmAdapter
import com.nova.runtime.android.calendarAdapter.CalendarAdapter
import com.nova.runtime.android.contactsAdapter.ContactsAdapter
import com.nova.runtime.android.intentAdapter.IntentAdapter
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapter
import com.nova.runtime.android.notificationAdapter.NotificationAdapter
import com.nova.runtime.android.ocrAdapter.OcrAdapter
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapter

class AndroidAdapterLayerImpl(
    override val intents: IntentAdapter,
    override val contacts: ContactsAdapter,
    override val calendar: CalendarAdapter,
    override val alarms: AlarmAdapter,
    override val mediaStore: MediaStoreAdapter,
    override val notifications: NotificationAdapter,
    override val accessibility: AccessibilityAdapter,
    override val storageAccess: StorageAccessAdapter,
    override val ocr: OcrAdapter,
) : AndroidAdapterLayer {
    override fun adapterNames(): List<String> =
        listOf(
            "Intent",
            "Contacts",
            "Calendar",
            "Alarm",
            "MediaStore",
            "Notification",
            "Accessibility",
            "StorageAccess",
            "OCR",
        )
}
