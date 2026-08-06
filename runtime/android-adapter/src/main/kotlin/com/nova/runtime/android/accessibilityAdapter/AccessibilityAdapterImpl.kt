package com.nova.runtime.android.accessibilityAdapter

import android.view.accessibility.AccessibilityNodeInfo
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.nova.runtime.android.internal.AccessibilityOperationTimeoutException

class AccessibilityAdapterImpl(
    private val bridge: AccessibilityServiceBridge,
    private val logger: NovaLogger,
    private val operationTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : AccessibilityAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            AccessibilityOperations.CLICK,
            AccessibilityOperations.INPUT_TEXT,
            AccessibilityOperations.SCROLL,
            AccessibilityOperations.TRAVERSE,
            AccessibilityOperations.GET_ACTIVE_WINDOW,
        )

    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        if (operation !in supportedOperations()) {
            return CapabilityResult.Failure(
                AdapterErrorMapper.invalidOperation(ADAPTER_NAME, operation, supportedOperations()),
            )
        }
        if (!bridge.isConnected()) {
            return CapabilityResult.Failure(AdapterErrorMapper.accessibilityUnavailable())
        }
        return AdapterBoundary.execute(logger, ADAPTER_NAME, operation, traceId) {
            withContext(Dispatchers.Main) {
                val result =
                    withTimeoutOrNull(operationTimeoutMs) {
                        when (operation) {
                            AccessibilityOperations.CLICK -> click(parameters)
                            AccessibilityOperations.INPUT_TEXT -> inputText(parameters)
                            AccessibilityOperations.SCROLL -> scroll(parameters)
                            AccessibilityOperations.TRAVERSE -> traverse(parameters)
                            AccessibilityOperations.GET_ACTIVE_WINDOW -> getActiveWindow()
                            else -> error("unreachable")
                        }
                    }
                result ?: throw AccessibilityOperationTimeoutException(operation)
            }
        }
    }

    private fun click(parameters: Map<String, String>): Map<String, String> {
        val service = bridge.requireService()
        val node =
            findNode(service, parameters)
                ?: throw IllegalArgumentException("Node not found for click")
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clicked) throw IllegalStateException("Click action failed")
        return mapOf("status" to "clicked")
    }

    private fun inputText(parameters: Map<String, String>): Map<String, String> {
        val text = parameters.require("text")
        val service = bridge.requireService()
        val node =
            findNode(service, parameters)
                ?: throw IllegalArgumentException("Node not found for input")
        val args =
            android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text,
                )
            }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!success) throw IllegalStateException("Input action failed")
        return mapOf("status" to "textSet", "length" to text.length.toString())
    }

    private fun scroll(parameters: Map<String, String>): Map<String, String> {
        val direction = parameters["direction"] ?: "forward"
        val action =
            when (direction) {
                "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
        val service = bridge.requireService()
        val root = service.rootInActiveWindow ?: throw IllegalStateException("No active window")
        val scrolled = root.performAction(action)
        if (!scrolled) throw IllegalStateException("Scroll action failed")
        return mapOf("status" to "scrolled", "direction" to direction)
    }

    private fun traverse(parameters: Map<String, String>): Map<String, String> {
        val maxDepth = parameters["maxDepth"]?.toIntOrNull() ?: 3
        val service = bridge.requireService()
        val root = service.rootInActiveWindow ?: throw IllegalStateException("No active window")
        val nodes = mutableListOf<String>()
        traverseNode(root, 0, maxDepth, nodes)
        return mapOf("count" to nodes.size.toString(), "nodes" to nodes.joinToString("|"))
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        output: MutableList<String>,
    ) {
        if (depth > maxDepth) return
        output.add(
            listOf(
                depth,
                node.className?.toString().orEmpty(),
                node.viewIdResourceName.orEmpty(),
                node.text?.toString().orEmpty(),
            ).joinToString(":"),
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, depth + 1, maxDepth, output)
                child.recycle()
            }
        }
    }

    private fun getActiveWindow(): Map<String, String> {
        val service = bridge.requireService()
        val root = service.rootInActiveWindow ?: throw IllegalStateException("No active window")
        return mapOf(
            "packageName" to (root.packageName?.toString() ?: ""),
            "className" to (root.className?.toString() ?: ""),
            "childCount" to root.childCount.toString(),
        )
    }

    private fun findNode(
        service: NovaAccessibilityService,
        parameters: Map<String, String>,
    ): AccessibilityNodeInfo? {
        parameters["viewId"]?.let { viewId ->
            val nodes = service.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)
            return nodes?.firstOrNull()
        }
        parameters["text"]?.let { text ->
            val nodes = service.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
            return nodes?.firstOrNull()
        }
        return service.rootInActiveWindow
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")


    private companion object {
        const val ADAPTER_NAME = "Accessibility"
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}

class AccessibilityAdapterStub : AccessibilityAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(
            AccessibilityOperations.CLICK,
            AccessibilityOperations.INPUT_TEXT,
            AccessibilityOperations.SCROLL,
            AccessibilityOperations.TRAVERSE,
            AccessibilityOperations.GET_ACTIVE_WINDOW,
        )
}
