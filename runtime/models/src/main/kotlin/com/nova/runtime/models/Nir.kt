package com.nova.runtime.models

/** NOVA Intermediate Representation — IAS §3, TDD §8 */
data class Nir(
    val version: Int,
    val goal: String,
    val entities: List<String>,
    val constraints: Map<String, String>,
    val context: Map<String, String>,
    val requiredCapabilities: List<String>,
    val confidence: Double,
)
