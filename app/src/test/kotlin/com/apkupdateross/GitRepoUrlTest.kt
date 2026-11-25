package com.apkupdateross

import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.git.extractSharedRepoUrl
import com.apkupdateross.data.git.parseRepoUrl
import com.apkupdateross.data.ui.isApkDownloadUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitRepoUrlTest {

    @Test
    fun parsesCodebergRepositoryUrls() {
        val parsed = parseRepoUrl("https://codeberg.org/owner/android-app/releases")

        assertEquals(GitProvider.CODEBERG, parsed?.provider)
        assertEquals("owner", parsed?.user)
        assertEquals("android-app", parsed?.repo)
    }

    @Test
    fun removesGitSuffix() {
        val parsed = parseRepoUrl("https://codeberg.org/owner/android-app.git")

        assertEquals("android-app", parsed?.repo)
    }

    @Test
    fun keepsExistingGitHubAndGitLabSupport() {
        assertEquals(
            GitProvider.GITHUB,
            parseRepoUrl("https://github.com/owner/repo")?.provider
        )
        assertEquals(
            GitProvider.GITLAB,
            parseRepoUrl("https://gitlab.com/owner/repo/-/releases")?.provider
        )
    }

    @Test
    fun nestedGitLabNamespaceIsPreserved() {
        val parsed = parseRepoUrl("https://gitlab.com/group/subgroup/android-app/-/releases")

        assertNotNull(parsed)
        assertEquals(GitProvider.GITLAB, parsed?.provider)
        assertEquals("group/subgroup", parsed?.user)
        assertEquals("android-app", parsed?.repo)
    }

    @Test
    fun rejectsInsecureAndLookalikeUrls() {
        assertNull(parseRepoUrl("http://codeberg.org/owner/repo"))
        assertNull(parseRepoUrl("https://codeberg.org.example.com/owner/repo"))
    }

    @Test
    fun apkUrlsMayContainQueryParameters() {
        assertEquals(true, "https://gitlab.com/app.apk?inline=false".isApkDownloadUrl())
        assertEquals(false, "https://gitlab.com/app.apkm?inline=false".isApkDownloadUrl())
    }

    @Test
    fun extractsNormalizedRepositoryUrlFromSharedText() {
        assertEquals(
            "https://github.com/owner/app",
            extractSharedRepoUrl("Check out this app: https://github.com/owner/app/releases/latest!")
        )
        assertEquals(
            "https://gitlab.com/group/sub/app",
            extractSharedRepoUrl("https://gitlab.com/group/sub/app/-/releases")
        )
        assertNull(extractSharedRepoUrl("https://example.com/owner/app"))
    }
}
