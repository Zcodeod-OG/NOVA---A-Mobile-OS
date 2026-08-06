package com.nova.runtime.android.accessibilityAdapter

/** Bridges [NovaAccessibilityService] to adapter implementations. */
class AccessibilityServiceBridge {
    @Volatile
    private var service: NovaAccessibilityService? = null

    fun bind(service: NovaAccessibilityService) {
        this.service = service
    }

    fun unbind() {
        service = null
    }

    fun isConnected(): Boolean = service != null

    fun requireService(): NovaAccessibilityService =
        service ?: throw IllegalStateException("Accessibility service not connected")
}
