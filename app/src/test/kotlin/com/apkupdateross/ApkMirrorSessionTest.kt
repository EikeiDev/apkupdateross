package com.apkupdateross

import com.apkupdateross.util.ApkMirrorSession
import com.apkupdateross.util.extractCookieValue
import com.apkupdateross.util.hasFreshClearanceCookie
import com.apkupdateross.util.isApkMirrorUrl
import com.apkupdateross.util.isCloudflareChallengePage
import com.apkupdateross.util.isLikelyApkMirrorDownloadUrl
import com.apkupdateross.util.mergeCookieHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkMirrorSessionTest {
    @Test
    fun apkMirrorHostMatchingIncludesSubdomainsOnly() {
        assertTrue(isApkMirrorUrl("https://www.apkmirror.com/apk/example"))
        assertTrue(isApkMirrorUrl("https://download.apkmirror.com/file.apk"))
        assertFalse(isApkMirrorUrl("http://www.apkmirror.com/file.apk"))
        assertFalse(isApkMirrorUrl("https://apkmirror.com.example.org/file.apk"))
        assertFalse(isApkMirrorUrl("https://example.org/file.apk"))
    }

    @Test
    fun newerCookieValuesReplaceOlderOnes() {
        assertEquals(
            "session=one; cf_clearance=new; locale=en",
            mergeCookieHeaders(
                "session=one; cf_clearance=old",
                "cf_clearance=new; locale=en"
            )
        )
    }

    @Test
    fun sessionSharesCookiesOnlyWithApkMirror() {
        val session = ApkMirrorSession()
        session.update("session=one; cf_clearance=verified", "Browser UA")

        assertEquals(
            "session=one; cf_clearance=verified",
            session.cookieHeaderFor("https://download.apkmirror.com/file.apk")
        )
        assertNull(session.cookieHeaderFor("https://example.org/file.apk"))
        assertEquals("Browser UA", session.userAgentOr("fallback"))

        session.clearClearance()
        assertEquals("session=one", session.snapshot().cookies)
    }

    @Test
    fun downloadUrlDetectionIgnoresUnrelatedFiles() {
        assertTrue(isLikelyApkMirrorDownloadUrl("https://download.apkmirror.com/file.apk?key=1"))
        assertTrue(
            isLikelyApkMirrorDownloadUrl(
                "https://www.apkmirror.com/wp-content/themes/APKMirror/download.php?id=1"
            )
        )
        assertFalse(isLikelyApkMirrorDownloadUrl("https://www.apkmirror.com/assets/app.js"))
    }

    @Test
    fun freshClearanceRejectsEmptyAndUnchangedCookies() {
        assertNull(extractCookieValue("cf_clearance=; other=1", "cf_clearance"))
        assertEquals("abc123456789", extractCookieValue("foo=bar; cf_clearance=abc123456789", "cf_clearance"))

        assertFalse(hasFreshClearanceCookie("cf_clearance=", previousClearance = null))
        assertFalse(hasFreshClearanceCookie("cf_clearance=short", previousClearance = null))
        assertFalse(hasFreshClearanceCookie("cf_clearance=old_token_12345", previousClearance = "old_token_12345"))
        assertTrue(hasFreshClearanceCookie("cf_clearance=new_token_67890", previousClearance = "old_token_12345"))
        assertTrue(hasFreshClearanceCookie("cf_clearance=new_token_67890", previousClearance = null))
    }

    @Test
    fun detectsCloudflareChallengePages() {
        assertTrue(isCloudflareChallengePage("https://www.apkmirror.com/?__cf_chl_rt_tk=abc", "APKMirror"))
        assertTrue(isCloudflareChallengePage("https://www.apkmirror.com/cdn-cgi/challenge-platform/h/b", "Page"))
        assertTrue(isCloudflareChallengePage("https://www.apkmirror.com/", "Just a moment..."))
        assertTrue(isCloudflareChallengePage("https://www.apkmirror.com/", "Один момент..."))
        assertTrue(isCloudflareChallengePage("https://www.apkmirror.com/", ""))
        assertFalse(isCloudflareChallengePage("https://www.apkmirror.com/apk/example/", "Example App - APKMirror"))
    }
}
