package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration

/** DSS §9 — version tracking and migration registry. */
object StorageMigrations {
    const val VERSION_1 = 1
    const val VERSION_2 = 2
    const val CURRENT_VERSION = VERSION_2

    val ALL: Array<Migration> = arrayOf(Migration_1_2)
}
