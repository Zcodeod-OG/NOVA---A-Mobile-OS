package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration

/** DSS §9 — version tracking and migration registry. */
object StorageMigrations {
    const val VERSION_1 = 1
    const val VERSION_2 = 2
    const val VERSION_3 = 3
    const val VERSION_4 = 4
    const val VERSION_5 = 5
    const val VERSION_6 = 6
    const val CURRENT_VERSION = VERSION_6

    val ALL: Array<Migration> = arrayOf(
        Migration_1_2,
        Migration_2_3,
        Migration_3_4,
        Migration_4_5,
        Migration_5_6,
    )
}
