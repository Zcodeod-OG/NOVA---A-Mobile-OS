package com.nova.runtime.android

/** AIS §3 — aggregates platform adapters (stub registry). */
interface AndroidAdapterLayer {
    fun adapterNames(): List<String>
}
