package com.nova.runtime.android.capability.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNameResolverTest {

    private class FakeCatalog(private val apps: List<InstalledApp>) : InstalledAppCatalog {
        override fun installedApps(): List<InstalledApp> = apps
        override fun isInstalled(packageName: String): Boolean =
            apps.any { it.packageName == packageName }
    }

    private val catalog = FakeCatalog(
        listOf(
            InstalledApp("YouTube", "com.google.android.youtube"),
            InstalledApp("Chrome", "com.android.chrome"),
            InstalledApp("WhatsApp", "com.whatsapp"),
            InstalledApp("Google Maps", "com.google.android.apps.maps"),
            InstalledApp("Calculator", "com.coloros.calculator"),
            InstalledApp("My Bank App", "com.example.mybank"),
        ),
    )

    private val resolver = AppNameResolver(catalog)

    @Test
    fun resolvesKnownAliasWhenInstalled() {
        assertEquals("com.google.android.youtube", resolver.resolvePackage("youtube"))
        assertEquals("com.whatsapp", resolver.resolvePackage("WhatsApp"))
    }

    @Test
    fun resolvesByExactLabelIgnoringCase() {
        assertEquals("com.coloros.calculator", resolver.resolvePackage("calculator"))
    }

    @Test
    fun resolvesByLabelPrefix() {
        assertEquals("com.example.mybank", resolver.resolvePackage("my bank"))
    }

    @Test
    fun resolvesByFuzzyContains() {
        assertEquals("com.example.mybank", resolver.resolvePackage("bank"))
    }

    @Test
    fun stripsTheAndAppSuffix() {
        assertEquals("com.google.android.youtube", resolver.resolvePackage("the youtube app"))
    }

    @Test
    fun resolvesAliasWithMultipleWords() {
        assertEquals("com.google.android.apps.maps", resolver.resolvePackage("google maps"))
    }

    @Test
    fun fallsBackToPackageNameMatch() {
        assertEquals("com.whatsapp", resolver.resolvePackage("whats app"))
    }

    @Test
    fun returnsNullForUnknownApp() {
        assertNull(resolver.resolvePackage("nonexistent app xyz"))
    }

    @Test
    fun returnsNullForBlankName() {
        assertNull(resolver.resolvePackage("   "))
    }
}
