package com.nova.runtime.android.accessibilityAdapter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** AIS §4.1 / §5 — activated only after explicit user permission. */
class NovaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        bridge.bind(this)
    }

    override fun onDestroy() {
        bridge.unbind()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        lateinit var bridge: AccessibilityServiceBridge
            private set

        fun installBridge(bridge: AccessibilityServiceBridge) {
            this.bridge = bridge
        }
    }
}
