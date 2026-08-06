package com.nova.runtime.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class AndroidAdapterRobolectricTest {
    protected val context: Context = ApplicationProvider.getApplicationContext()
}
