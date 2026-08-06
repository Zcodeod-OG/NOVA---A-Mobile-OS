package com.nova.runtime.android

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class AndroidAdapterRobolectricTest {
    protected val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun grantTestPermissions() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        ShadowApplication.getInstance().grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }
}
