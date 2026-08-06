package com.nova.runtime.capability.resolver

import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.model.CapabilityResolutionRequest
import com.nova.runtime.capability.provider.StubCommunicationProvider
import com.nova.runtime.capability.provider.StubCapabilityProvider
import com.nova.runtime.capability.provider.StubTimeProvider
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class CapabilityProviderResolverTest {

    @Test
    fun resolve_activeProviderMatchingTypeAndOperation() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)

        val result = resolver.resolve(
            CapabilityResolutionRequest(
                capabilityType = "communication",
                operation = "send",
            ),
        )

        assertNotNull(result)
        assertEquals("stub-communication", result.provider.providerId)
    }

    @Test
    fun resolve_suspendedProvider_returnsNull() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        lifecycle.suspend("stub-communication", "1.0.0")
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)

        val result = resolver.resolve(
            CapabilityResolutionRequest(
                capabilityType = "communication",
                operation = "send",
            ),
        )

        assertNull(result)
    }

    @Test
    fun resolve_unsupportedOperation_returnsNull() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)

        val result = resolver.resolve(
            CapabilityResolutionRequest(
                capabilityType = "communication",
                operation = "unsupported-op",
            ),
        )

        assertNull(result)
    }

    @Test
    fun resolve_withProviderIdConstraint_selectsMatchingProvider() = runTest {
        val registry = DefaultCapabilityRegistry(
            listOf(StubCommunicationProvider(), StubTimeProvider()),
        )
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)

        val result = resolver.resolve(
            CapabilityResolutionRequest(
                capabilityType = "time",
                operation = "alarm",
                constraints = mapOf("providerId" to "stub-time"),
            ),
        )

        assertNotNull(result)
        assertEquals("stub-time", result.provider.providerId)
    }

    @Test
    fun resolve_pipelineWhatsAppRequest_matchesQualifiedAndroidProvider() = runTest {
        val qualifiedProvider =
            object : StubCapabilityProvider(
                providerId = "android-communication",
                capabilityType = "communication",
                version = "1.0.0",
                operations = setOf("whatsapp.send_message"),
            ) {}
        val registry = DefaultCapabilityRegistry(listOf(qualifiedProvider))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)

        val result = resolver.resolve(
            CapabilityResolutionRequest(
                capabilityType = "whatsapp",
                operation = "send_message",
            ),
        )

        assertNotNull(result)
        assertEquals("android-communication", result.provider.providerId)
    }

    @Test
    fun resolve_unknownCapabilityType_returnsNull() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)

        assertNull(
            resolver.resolve(
                CapabilityResolutionRequest(
                    capabilityType = "unknown",
                    operation = "execute",
                ),
            ),
        )
    }
}
