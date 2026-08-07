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

/** Stubs kept in production for capabilities without Android providers yet. */
private val PRODUCTION_STUB_PROVIDER_IDS = setOf(
    "stub-knowledge",
    "stub-device",
    "stub-notifications",
)

fun productionOnlyStubProviders(): List<CapabilityProvider> =
    defaultStubProviders().filter { it.providerId in PRODUCTION_STUB_PROVIDER_IDS }

/**
 * Production provider set: real Android providers for wired capabilities plus
 * non-overlapping stubs for features not yet backed by Android adapters.
 */
fun productionCapabilityProviders(
    context: Context,
    adapters: AndroidAdapterLayer,
    logger: NovaLogger,
): List<CapabilityProvider> =
    androidCapabilityProviders(context, adapters, logger) + productionOnlyStubProviders()
