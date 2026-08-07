package com.nova.runtime.ai.native.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Checks whether the device has an internet-capable network. */
class AndroidNetworkConnectivity(context: Context) : NetworkAvailabilityChecker {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
