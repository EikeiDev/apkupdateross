package com.apkupdateross

import com.apkupdateross.repository.parseUptodownMinSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UptodownCompatibilityTest {

    @Test
    fun parsesAndroid12LRequirement() {
        assertEquals(32, parseUptodownMinSdk("Android + 12L"))
    }

    @Test
    fun parsesCommonDecimalRequirements() {
        assertEquals(25, parseUptodownMinSdk("Android 7.1+"))
        assertEquals(27, parseUptodownMinSdk("Android + 8.1"))
    }

    @Test
    fun unknownRequirementStaysUnknown() {
        assertNull(parseUptodownMinSdk("Varies with device"))
    }
}
