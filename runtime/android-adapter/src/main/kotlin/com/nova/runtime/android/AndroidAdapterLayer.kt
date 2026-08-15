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

/** AIS §3 — aggregates all platform adapters behind a single facade (IAS boundary). */
interface AndroidAdapterLayer {
    val intents: IntentAdapter
    val contacts: ContactsAdapter
    val calendar: CalendarAdapter
    val alarms: AlarmAdapter
    val mediaStore: MediaStoreAdapter
    val notifications: NotificationAdapter
    val accessibility: AccessibilityAdapter
    val storageAccess: StorageAccessAdapter
    val ocr: OcrAdapter

    fun adapterNames(): List<String>
}
