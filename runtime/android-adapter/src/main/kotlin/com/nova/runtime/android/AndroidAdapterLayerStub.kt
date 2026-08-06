package com.nova.runtime.android

class AndroidAdapterLayerStub : AndroidAdapterLayer {
    override fun adapterNames(): List<String> = listOf(
        "Intent",
        "MediaStore",
        "Contacts",
        "Calendar",
        "Alarm",
        "Notification",
        "StorageAccess",
    )
}
