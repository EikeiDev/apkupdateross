package com.apkupdateross

import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FdroidRepoUrlTest {

    @Test
    fun addsRequiredTrailingSlash() {
        assertEquals(
            "https://example.org/fdroid/repo/",
            normalizeFdroidRepoUrl("  https://example.org/fdroid/repo  ")
        )
    }

    @Test
    fun rejectsCleartextAndQueryUrls() {
        assertNull(normalizeFdroidRepoUrl("http://example.org/repo/"))
        assertNull(normalizeFdroidRepoUrl("https://example.org/repo/?token=secret"))
    }
}
