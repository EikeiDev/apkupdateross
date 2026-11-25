package com.apkupdateross

import com.apkupdateross.data.github.GitHubRelease
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CodebergReleaseModelTest {

    @Test
    fun parsesForgejoReleaseAndAsset() {
        val json = """
            {
              "name": "Version 1.2.3",
              "tag_name": "v1.2.3",
              "draft": false,
              "prerelease": false,
              "body": "Changes",
              "author": { "login": "owner", "avatar_url": "https://codeberg.org/avatar.png" },
              "assets": [
                {
                  "size": 12345,
                  "browser_download_url": "https://codeberg.org/owner/app/releases/download/v1.2.3/app-arm64-v8a.apk"
                }
              ]
            }
        """.trimIndent()

        val release = Gson().fromJson(json, GitHubRelease::class.java)

        assertEquals("v1.2.3", release.tag_name)
        assertFalse(release.draft)
        assertEquals(12345L, release.assets.single().size)
    }
}
