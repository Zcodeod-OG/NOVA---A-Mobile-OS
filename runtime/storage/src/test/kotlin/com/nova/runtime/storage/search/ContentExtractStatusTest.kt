package com.nova.runtime.storage.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExtractStatusTest {

    @Test
    fun needsForceExtraction_retriesFailedEmptyAndHtmlBodies() {
        assertTrue(
            ContentExtractStatus.needsForceExtraction(ContentExtractStatus.FAILED, ""),
        )
        assertTrue(
            ContentExtractStatus.needsForceExtraction(ContentExtractStatus.EMPTY, ""),
        )
        assertTrue(
            ContentExtractStatus.needsForceExtraction(ContentExtractStatus.NOT_TRIED, null),
        )
        assertTrue(
            ContentExtractStatus.needsForceExtraction(
                ContentExtractStatus.SUCCESS,
                "<html><body>menu</body></html>",
            ),
        )
        assertFalse(
            ContentExtractStatus.needsForceExtraction(
                ContentExtractStatus.SUCCESS,
                "WEEKLY MESS MENU\nMonday lunch",
            ),
        )
    }

    @Test
    fun debugLabel_surfacesFilenameUsefulExtractStatus() {
        assertEquals(
            "128 chars extracted",
            ContentExtractStatus.debugLabel(ContentExtractStatus.SUCCESS, 128),
        )
        assertEquals(
            "0 chars extracted",
            ContentExtractStatus.debugLabel(ContentExtractStatus.EMPTY, 0),
        )
        assertEquals(
            "extract failed — will retry",
            ContentExtractStatus.debugLabel(ContentExtractStatus.FAILED, 0),
        )
        assertEquals(
            "extract pending",
            ContentExtractStatus.debugLabel(ContentExtractStatus.NOT_TRIED, 0),
        )
        assertNull(ContentExtractStatus.debugLabel(null, 0))
    }
}
