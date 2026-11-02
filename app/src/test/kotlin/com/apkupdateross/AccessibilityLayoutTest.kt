package com.apkupdateross

import com.apkupdateross.ui.component.accessibleColumnCount
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityLayoutTest {
    @Test
    fun normalFontScaleKeepsRequestedColumns() {
        assertEquals(4, accessibleColumnCount(4, 320, 1f))
    }

    @Test
    fun largeFontScaleReducesColumnsOnNarrowScreens() {
        assertEquals(2, accessibleColumnCount(4, 320, 1.3f))
        assertEquals(1, accessibleColumnCount(4, 320, 1.5f))
        assertEquals(1, accessibleColumnCount(4, 320, 2f))
    }

    @Test
    fun largeFontScaleStillUsesAvailableLandscapeWidth() {
        assertEquals(4, accessibleColumnCount(6, 720, 1.5f))
    }
}
