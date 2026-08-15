package com.nova.runtime.capability.model

/** Descriptor metadata for a capability contract — IAS §6. */
data class CapabilityMetadata(
    val name: String,
    val version: String,
    val capabilityType: String,
    val description: String = "",
    val supportedOperations: Set<String> = emptySet(),
    val requiredPermissions: Set<String> = emptySet(),
    val tags: Map<String, String> = emptyMap(),
) {
    val key: String get() = "$name:$version"
}
