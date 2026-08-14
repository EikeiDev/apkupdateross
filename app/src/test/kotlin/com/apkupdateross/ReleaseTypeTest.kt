package com.apkupdateross

import com.apkupdateross.data.ui.ReleaseType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseTypeTest {
    @Test
    fun stableIsDefault() {
        assertEquals(ReleaseType.Stable, ReleaseType.from("1.2.3"))
    }

    @Test
    fun detectsAlphaFromVersion() {
        assertEquals(ReleaseType.Alpha, ReleaseType.from("1.2.3-alpha01"))
    }

    @Test
    fun detectsBetaFromVersion() {
        assertEquals(ReleaseType.Beta, ReleaseType.from("v2.0-beta.3"))
    }

    @Test
    fun detectsPreReleaseFromCandidateVersion() {
        assertEquals(ReleaseType.PreRelease, ReleaseType.from("3.0.0-rc1"))
    }

    @Test
    fun explicitPreReleaseFlagFallsBackToPreRelease() {
        assertEquals(ReleaseType.PreRelease, ReleaseType.from("4.0.0", explicitPreRelease = true))
    }

    @Test
    fun explicitPreReleaseStillPrefersSpecificKeyword() {
        assertEquals(ReleaseType.Beta, ReleaseType.from("4.0.0-beta", explicitPreRelease = true))
    }

    @Test
    fun doesNotDetectAlphaInsideRegularWord() {
        assertEquals(ReleaseType.Stable, ReleaseType.from("1.0.0", "Alphabet Notes"))
    }
}
