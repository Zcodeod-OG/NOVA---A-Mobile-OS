package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.capability.provider.defaultStubProviders
import com.nova.runtime.utils.logging.NovaLogger

/** Android-backed providers merged with non-overlapping MVP stubs. */
fun androidCapabilityProviders(
    context: Context,
    adapters: AndroidAdapterLayer,
    logger: NovaLogger,
): List<CapabilityProvider> =
    listOf(
        AndroidCommunicationProvider(context, logger, adapters),
        AndroidTimeProvider(context, logger, adapters),
        AndroidMediaProvider(context, logger, adapters),
    )

/**
 * Production provider set: real Android providers for wired capabilities plus
 * remaining stub providers for features owned by other agents.
 */
fun productionCapabilityProviders(
    context: Context,
    adapters: AndroidAdapterLayer,
    logger: NovaLogger,
): List<CapabilityProvider> =
    androidCapabilityProviders(context, adapters, logger) + defaultStubProviders()
