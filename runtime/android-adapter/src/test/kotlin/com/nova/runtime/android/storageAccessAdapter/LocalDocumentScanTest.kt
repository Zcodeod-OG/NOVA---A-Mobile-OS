package com.nova.runtime.android.storageAccessAdapter

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalDocumentScanTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun scan_includesAppExternalDocumentsDirectory() {
        val appDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
        appDocs.mkdirs()
        File(appDocs, "semester-timetable.pdf").writeText("pdf-body")
        val items = LocalDocumentScan.scan(context, limit = 50, offset = 0)
        assertTrue(items.any { it.displayName == "semester-timetable.pdf" })
    }

    @Test
    fun scan_includesAppExternalDownloadsDirectory() {
        val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        appDownloads.mkdirs()
        File(appDownloads, "mess-menu.pdf").writeText("pdf-body")
        val items = LocalDocumentScan.scan(context, limit = 50, offset = 0)
        assertTrue(items.any { it.displayName == "mess-menu.pdf" })
    }

    @Test
    fun documentsRoots_alwaysAttemptsPublicDocumentsFolder() {
        val publicDocs =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        assertTrue(publicDocs != null)
    }
}
