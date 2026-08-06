package com.nova.runtime.android.di

import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.AndroidAdapterLayerImpl
import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapter
import com.nova.runtime.android.accessibilityAdapter.AccessibilityAdapterImpl
import com.nova.runtime.android.accessibilityAdapter.AccessibilityServiceBridge
import com.nova.runtime.android.accessibilityAdapter.NovaAccessibilityService
import com.nova.runtime.android.alarmAdapter.AlarmAdapter
import com.nova.runtime.android.alarmAdapter.AlarmAdapterImpl
import com.nova.runtime.android.calendarAdapter.CalendarAdapter
import com.nova.runtime.android.calendarAdapter.CalendarAdapterImpl
import com.nova.runtime.android.contactsAdapter.ContactsAdapter
import com.nova.runtime.android.contactsAdapter.ContactsAdapterImpl
import com.nova.runtime.android.intentAdapter.IntentAdapter
import com.nova.runtime.android.intentAdapter.IntentAdapterImpl
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapter
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreAdapterImpl
import com.nova.runtime.android.notificationAdapter.NotificationAdapter
import com.nova.runtime.android.notificationAdapter.NotificationAdapterImpl
import com.nova.runtime.android.ocrAdapter.OcrAdapter
import com.nova.runtime.android.ocrAdapter.OcrAdapterStub
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapter
import com.nova.runtime.android.storageAccessAdapter.StorageAccessAdapterImpl
import com.nova.runtime.android.mediaStoreAdapter.MediaStoreQueryPortImpl
import com.nova.runtime.storage.search.MediaStoreQueryPort
import com.nova.runtime.utils.logging.NovaLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin DI wiring for Android Adapter Layer — AIS §3, MSP §12. */
val androidAdapterModule = module {
    single { AccessibilityServiceBridge().also(NovaAccessibilityService::installBridge) }
    single<IntentAdapter> { IntentAdapterImpl(androidContext(), get()) }
    single<ContactsAdapter> { ContactsAdapterImpl(androidContext(), get()) }
    single<CalendarAdapter> { CalendarAdapterImpl(androidContext(), get()) }
    single<AlarmAdapter> { AlarmAdapterImpl(androidContext(), get()) }
    single<MediaStoreAdapter> { MediaStoreAdapterImpl(androidContext(), get()) }
    single<MediaStoreQueryPort>(override = true) { MediaStoreQueryPortImpl(get()) }
    single<NotificationAdapter> { NotificationAdapterImpl(androidContext(), get()) }
    single<AccessibilityAdapter> { AccessibilityAdapterImpl(get(), get()) }
    single<StorageAccessAdapter> { StorageAccessAdapterImpl(androidContext(), get()) }
    single<OcrAdapter> { OcrAdapterStub() }
    single<AndroidAdapterLayer> {
        AndroidAdapterLayerImpl(
            intents = get(),
            contacts = get(),
            calendar = get(),
            alarms = get(),
            mediaStore = get(),
            notifications = get(),
            accessibility = get(),
            storageAccess = get(),
            ocr = get(),
        )
    }
}

internal fun androidAdapterTestModule(logger: NovaLogger) = module {
    single { AccessibilityServiceBridge().also(NovaAccessibilityService::installBridge) }
    single<IntentAdapter> { IntentAdapterImpl(androidContext(), logger) }
    single<ContactsAdapter> { ContactsAdapterImpl(androidContext(), logger) }
    single<CalendarAdapter> { CalendarAdapterImpl(androidContext(), logger) }
    single<AlarmAdapter> { AlarmAdapterImpl(androidContext(), logger) }
    single<MediaStoreAdapter> { MediaStoreAdapterImpl(androidContext(), logger) }
    single<MediaStoreQueryPort>(override = true) { MediaStoreQueryPortImpl(get()) }
    single<NotificationAdapter> { NotificationAdapterImpl(androidContext(), logger) }
    single<AccessibilityAdapter> { AccessibilityAdapterImpl(get(), logger) }
    single<StorageAccessAdapter> { StorageAccessAdapterImpl(androidContext(), logger) }
    single<OcrAdapter> { OcrAdapterStub() }
    single<AndroidAdapterLayer> {
        AndroidAdapterLayerImpl(
            intents = get(),
            contacts = get(),
            calendar = get(),
            alarms = get(),
            mediaStore = get(),
            notifications = get(),
            accessibility = get(),
            storageAccess = get(),
            ocr = get(),
        )
    }
}
