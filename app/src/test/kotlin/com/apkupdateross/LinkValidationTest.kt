package com.apkupdateross

import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.isSplitPackageDownloadUrl
import com.apkupdateross.data.ui.validationSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkValidationTest {

    @Test
    fun roundedPageSizeIsNotTreatedAsExact() {
        val link = Link.Url(
            link = "https://example.com/app.apk",
            size = 70L * 1024L * 1024L,
            expectedPackageName = "com.example.app"
        )

        assertEquals(0L, link.validationSize())
    }

    @Test
    fun hashedAssetKeepsExactSizeValidation() {
        val link = Link.Url(
            link = "https://example.com/app.apk",
            size = 1234L,
            sha256 = "deadbeef"
        )

        assertEquals(1234L, link.validationSize())
    }

    @Test
    fun apkMirrorDomainIsNotMistakenForApkmExtension() {
        val url = "https://www.apkmirror.com/apk/valve-corporation/steam/steam-3-10-13-release/steam-3-10-13-android-apk-download/"

        assertFalse(url.isSplitPackageDownloadUrl())
    }

    @Test
    fun splitExtensionIsReadFromUrlPath() {
        assertTrue("https://cdn.example.com/app.apkm?token=abc".isSplitPackageDownloadUrl())
        assertTrue("https://cdn.example.com/app.apks#download".isSplitPackageDownloadUrl())
        assertTrue("https://cdn.example.com/app.XAPK".isSplitPackageDownloadUrl())
        assertFalse("https://cdn.example.com/app.apkm.html".isSplitPackageDownloadUrl())
    }
}
