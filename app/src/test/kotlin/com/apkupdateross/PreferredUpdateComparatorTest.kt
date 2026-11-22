package com.apkupdateross

import com.apkupdateross.data.ui.AptoideSource
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.UptodownSource
import com.apkupdateross.data.ui.compareUpdatePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredUpdateComparatorTest {

    @Test
    fun versionNameWinsAcrossSourcesEvenWhenVersionCodeIsSmaller() {
        val comparison = compareUpdatePreference(
            "2.9.0", 9_999_999L, UptodownSource,
            "3.0.0", 30L, AptoideSource
        )

        assertEquals(1, comparison)
    }

    @Test
    fun trustedSourceWinsWhenVersionNamesMatch() {
        val comparison = compareUpdatePreference(
            "3.0.0", 300L, AptoideSource,
            "3.0.0", 200L, PlaySource
        )

        assertEquals(1, comparison)
    }
}
