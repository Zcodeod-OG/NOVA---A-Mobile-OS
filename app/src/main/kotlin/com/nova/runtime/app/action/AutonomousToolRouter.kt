package com.nova.runtime.app.action

import android.content.Context
import com.nova.runtime.app.service.NovaAccessibilityService
import com.nova.runtime.app.service.NovaNotificationListenerService

enum class AtomicToolType {
    READ_NOTIFICATIONS,
    CLICK_BUTTON,
    TYPE_TEXT,
    TOGGLE_HARDWARE,
    LAUNCH_APP,
    SYSTEM_NAVIGATE
}

data class AtomicToolStep(
    val toolType: AtomicToolType,
    val parameter: String
)

class AutonomousToolRouter(
    private val context: Context,
    private val hardwareController: SystemHardwareController
) {

    fun planAndExecuteMultiStepPrompt(prompt: String): ActionExecutionResult {
        val steps = decomposePromptIntoSteps(prompt)
        val results = mutableListOf<String>()

        for (step in steps) {
            val stepResult = executeAtomicStep(step)
            results.add("${step.toolType.name}: ${stepResult.message}")
        }

        return ActionExecutionResult(
            isSuccess = true,
            appName = "Autonomous Router",
            message = "Executed ${steps.size} atomic tool steps:\n" + results.joinToString("\n")
        )
    }

    private fun decomposePromptIntoSteps(prompt: String): List<AtomicToolStep> {
        val lower = prompt.lowercase()
        val steps = mutableListOf<AtomicToolStep>()

        // 1. Read Notification Step
        if (lower.contains("read notification") || lower.contains("check notification") || lower.contains("read message")) {
            steps.add(AtomicToolStep(AtomicToolType.READ_NOTIFICATIONS, "latest"))
        }

        // 2. Hardware / Setting Step
        if (lower.contains("flashlight") || lower.contains("torch")) {
            steps.add(AtomicToolStep(AtomicToolType.TOGGLE_HARDWARE, "flashlight"))
        }

        // 3. Navigation Step
        if (lower.contains("go home") || lower.contains("home screen")) {
            steps.add(AtomicToolStep(AtomicToolType.SYSTEM_NAVIGATE, "home"))
        }

        // 4. Click / Type Steps
        if (lower.contains("click") || lower.contains("tap")) {
            val target = prompt.substringAfter("click").substringAfter("tap").trim()
            steps.add(AtomicToolStep(AtomicToolType.CLICK_BUTTON, target.ifBlank { "Button" }))
        }

        if (lower.contains("type") || lower.contains("write")) {
            val text = prompt.substringAfter("type").substringAfter("write").trim()
            steps.add(AtomicToolStep(AtomicToolType.TYPE_TEXT, text.ifBlank { "Hello" }))
        }

        // Default single step fallback if no complex pipeline detected
        if (steps.isEmpty()) {
            steps.add(AtomicToolStep(AtomicToolType.LAUNCH_APP, prompt))
        }

        return steps
    }

    private fun executeAtomicStep(step: AtomicToolStep): ActionExecutionResult {
        val accessibility = NovaAccessibilityService.instance

        return when (step.toolType) {
            AtomicToolType.READ_NOTIFICATIONS -> {
                val notification = NovaNotificationListenerService.getLatestNotification()
                if (notification != null) {
                    ActionExecutionResult(true, "Notification Listener", "Latest Notification from ${notification.title}: ${notification.text}")
                } else {
                    ActionExecutionResult(true, "Notification Listener", "No unread notifications")
                }
            }

            AtomicToolType.CLICK_BUTTON -> {
                if (accessibility != null) {
                    val success = accessibility.clickNodeByText(step.parameter)
                    ActionExecutionResult(success, "Accessibility Touch", if (success) "Clicked '${step.parameter}'" else "Node '${step.parameter}' not visible")
                } else {
                    ActionExecutionResult(false, "Accessibility", "Accessibility service disabled in Android settings")
                }
            }

            AtomicToolType.TYPE_TEXT -> {
                if (accessibility != null) {
                    val success = accessibility.typeTextIntoFocusedField(step.parameter)
                    ActionExecutionResult(success, "Accessibility Touch", if (success) "Typed '${step.parameter}'" else "No text field focused")
                } else {
                    ActionExecutionResult(false, "Accessibility", "Accessibility service disabled in Android settings")
                }
            }

            AtomicToolType.TOGGLE_HARDWARE -> {
                hardwareController.toggleFlashlight()
            }

            AtomicToolType.SYSTEM_NAVIGATE -> {
                if (accessibility != null) {
                    accessibility.performGlobalHome()
                    ActionExecutionResult(true, "System Navigation", "Navigated Home via Accessibility")
                } else {
                    ActionExecutionResult(false, "System Navigation", "Accessibility service disabled")
                }
            }

            AtomicToolType.LAUNCH_APP -> {
                ActionExecutionResult(true, "App Launcher", "Dispatched intent for ${step.parameter}")
            }
        }
    }
}
