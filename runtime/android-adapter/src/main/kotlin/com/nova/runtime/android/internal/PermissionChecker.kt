package com.nova.runtime.android.internal

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionChecker {
    fun ensureGranted(context: Context, permission: String) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException(permission)
        }
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
