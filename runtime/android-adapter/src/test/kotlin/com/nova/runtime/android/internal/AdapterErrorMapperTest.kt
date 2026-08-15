package com.nova.runtime.android.internal

import com.nova.runtime.models.ErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterErrorMapperTest {
    @Test
    fun permissionDenied_mapsToPermissionCategory() {
        val error = AdapterErrorMapper.permissionDenied("android.permission.READ_CONTACTS")
        assertEquals(ErrorCategory.PERMISSION, error.category)
        assertEquals("ANDROID_PERMISSION_DENIED", error.code)
        assertTrue(error.recoverable)
    }

    @Test
    fun invalidOperation_includesSupportedOperations() {
        val error =
            AdapterErrorMapper.invalidOperation(
                adapter = "Intent",
                operation = "unknown",
                supported = setOf("openApp", "share"),
            )
        assertEquals(ErrorCategory.VALIDATION, error.category)
        assertTrue(error.diagnostics["supported"]?.contains("openApp") == true)
    }

    @Test
    fun accessibilityTimeout_isRecoverable() {
        val error = AdapterErrorMapper.accessibilityTimeout("click")
        assertEquals("ANDROID_ACCESSIBILITY_TIMEOUT", error.code)
        assertTrue(error.recoverable)
    }
}
