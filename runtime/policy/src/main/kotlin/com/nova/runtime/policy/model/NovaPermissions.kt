package com.nova.runtime.policy.model

/** Abstract permission identifiers — Android mapping deferred to adapter layer. */
object NovaPermissions {
    const val CONTACTS_READ = "contacts.read"
    const val CONTACTS_WRITE = "contacts.write"
    const val LOCATION = "location"
    const val MICROPHONE = "microphone"
    const val CAMERA = "camera"
    const val SMS_SEND = "sms.send"
    const val CALL = "call"
    const val STORAGE_READ = "storage.read"
    const val CALENDAR_WRITE = "calendar.write"
}
