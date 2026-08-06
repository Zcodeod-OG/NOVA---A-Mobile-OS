package com.nova.runtime.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NovaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: NovaAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event processing for live screen inspection
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    // --- Autonomous Touch & Gesture Controls ---

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGlobalRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun clickNodeByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val matchingNodes = rootNode.findAccessibilityNodeInfosByText(targetText)

        if (!matchingNodes.isNullOrEmpty()) {
            for (node in matchingNodes) {
                if (node.isClickable) {
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        parent = parent.parent
                    }
                }
            }
        }
        return false
    }

    fun typeTextIntoFocusedField(textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun scrollActiveScreen(scrollDown: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (scrollDown) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return rootNode.performAction(action)
    }
}
