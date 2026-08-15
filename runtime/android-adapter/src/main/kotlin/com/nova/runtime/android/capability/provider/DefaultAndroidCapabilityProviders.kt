package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.email.GmailOAuthManager
import com.nova.runtime.android.email.GmailSyncService
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.capability.provider.defaultStubProviders
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.utils.logging.NovaLogger

/** Android-backed providers merged with non-overlapping MVP stubs. */
fun androidCapabilityProviders(
    context: Context,
    adapters: AndroidAdapterLayer,
    logger: NovaLogger,
    oauthManager: GmailOAuthManager,
    gmailSyncService: GmailSyncService,
    messageRepository: MessageRepository,
): List<CapabilityProvider> =
    listOf(
        AndroidCommunicationProvider(context, logger, adapters),
        AndroidEmailProvider(
            context = context,
            logger = logger,
            oauthManager = oauthManager,
            gmailSyncService = gmailSyncService,
            messageRepository = messageRepository,
        ),
        AndroidTimeProvider(context, logger, adapters),
        AndroidMediaProvider(context, logger, adapters),
        AndroidDeviceProvider(context, logger, adapters),
    )

/** Stubs kept in production for capabilities without Android providers yet. */
private val PRODUCTION_STUB_PROVIDER_IDS = setOf(
    "stub-knowledge",
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
    oauthManager: GmailOAuthManager,
    gmailSyncService: GmailSyncService,
    messageRepository: MessageRepository,
): List<CapabilityProvider> =
    androidCapabilityProviders(
        context,
        adapters,
        logger,
        oauthManager,
        gmailSyncService,
        messageRepository,
    ) + productionOnlyStubProviders()
