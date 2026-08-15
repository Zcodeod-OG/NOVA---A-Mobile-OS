package com.nova.runtime.android.accessibilityAdapter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** AIS §4.1 / §5 — activated only after explicit user permission. */
class NovaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedBridge?.bind(this)
    }

    override fun onDestroy() {
        sharedBridge?.unbind()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var sharedBridge: AccessibilityServiceBridge? = null

        fun installBridge(bridge: AccessibilityServiceBridge) {
            sharedBridge = bridge
        }

        fun isEnabled(): Boolean = sharedBridge?.isConnected() == true
    }
}
