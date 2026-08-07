package com.nova.runtime.ai.native.model

/** Abstraction for checking outbound network availability before model downloads. */
fun interface NetworkAvailabilityChecker {
    fun isInternetAvailable(): Boolean
}
