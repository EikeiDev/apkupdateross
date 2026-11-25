package com.apkupdateross

import com.apkupdateross.repository.isApkMirrorVersionUpdate
import com.apkupdateross.repository.isHuaweiUpdate
import com.apkupdateross.repository.isRuStoreUpdate
import com.apkupdateross.repository.ruStoreQueryVersionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreModdedVersionTest {

    @Test
    fun huaweiAllowsModdedMaxVersionCodeOnlyWithPrivilegedDowngrade() {
        assertTrue(isHuaweiUpdate(200L, "2.0", 199L, "1.9", allowVersionCodeDowngrade = false))
        assertFalse(isHuaweiUpdate(200L, "2.1", Long.MAX_VALUE, "2.0", allowVersionCodeDowngrade = false))
        assertTrue(isHuaweiUpdate(200L, "2.1", Long.MAX_VALUE, "2.0", allowVersionCodeDowngrade = true))
        assertFalse(isHuaweiUpdate(200L, "2.0", Long.MAX_VALUE, "2.0", allowVersionCodeDowngrade = true))
        assertFalse(isHuaweiUpdate(150L, "2.1", 200L, "2.0", allowVersionCodeDowngrade = true))
    }

    @Test
    fun ruStoreClampsModdedQueryCodeAndValidatesUpdate() {
        assertEquals(Long.MAX_VALUE, ruStoreQueryVersionCode(Long.MAX_VALUE, allowDowngrade = false))
        assertEquals(1L, ruStoreQueryVersionCode(Long.MAX_VALUE, allowDowngrade = true))
        assertEquals(42L, ruStoreQueryVersionCode(42L, allowDowngrade = true))

        assertFalse(isRuStoreUpdate(200L, "2.1", Long.MAX_VALUE, "2.0", allowDowngrade = false))
        assertTrue(isRuStoreUpdate(200L, "2.1", Long.MAX_VALUE, "2.0", allowDowngrade = true))
        assertFalse(isRuStoreUpdate(200L, "2.0", Long.MAX_VALUE, "2.0", allowDowngrade = true))
        assertFalse(isRuStoreUpdate(190L, "2.1", 200L, "2.0", allowDowngrade = true))
    }

    @Test
    fun apkMirrorAllowsModdedMaxVersionCodeOnlyWithPrivilegedDowngrade() {
        assertTrue(isApkMirrorVersionUpdate(200L, "2.0", 199L, "1.9", allowDowngrade = false))
        assertFalse(isApkMirrorVersionUpdate(200L, "2.1", Long.MAX_VALUE, "2.0", allowDowngrade = false))
        assertTrue(isApkMirrorVersionUpdate(200L, "2.1", Long.MAX_VALUE, "2.0", allowDowngrade = true))
        assertFalse(isApkMirrorVersionUpdate(200L, "1.9", Long.MAX_VALUE, "2.0", allowDowngrade = true))
        assertFalse(isApkMirrorVersionUpdate(199L, "2.1", 200L, "2.0", allowDowngrade = true))
    }
}
