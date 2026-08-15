package com.nova.runtime.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.storage.database.NovaDatabase
import com.nova.runtime.storage.database.NovaDatabaseProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class StorageRobolectricTest {
    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected fun createInMemoryDatabase(): NovaDatabase = NovaDatabaseProvider.createInMemory(context)
}
