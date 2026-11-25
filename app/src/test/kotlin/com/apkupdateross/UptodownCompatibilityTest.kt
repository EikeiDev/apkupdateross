package com.apkupdateross

import com.apkupdateross.repository.isUptodownVersionNewer
import com.apkupdateross.repository.parseUptodownMinSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun rejectsVersionCodeDowngradeInNormalModeAndAllowsInPrivilegedMode() {
        assertFalse(
            isUptodownVersionNewer(
                remoteVersion = "8.93.2.3699678",
                remoteVersionCode = 852L,
                installedVersion = "8.81.1.3344425",
                installedVersionCode = Long.MAX_VALUE,
                allowVersionCodeDowngrade = false
            )
        )
        assertTrue(
            isUptodownVersionNewer(
                remoteVersion = "8.93.2.3699678",
                remoteVersionCode = 852L,
                installedVersion = "8.81.1.3344425",
                installedVersionCode = Long.MAX_VALUE,
                allowVersionCodeDowngrade = true
            )
        )
        assertTrue(
            isUptodownVersionNewer(
                remoteVersion = "8.93.2.3699678",
                remoteVersionCode = 900L,
                installedVersion = "8.81.1.3344425",
                installedVersionCode = 852L,
                allowVersionCodeDowngrade = false
            )
        )
        assertTrue(
            isUptodownVersionNewer(
                remoteVersion = "8.81.1",
                remoteVersionCode = 900L,
                installedVersion = "8.81.1",
                installedVersionCode = 852L,
                allowVersionCodeDowngrade = false
            )
        )
    }
}
