package com.apkupdateross

import com.apkupdateross.repository.UptodownRemoteVersion
import com.apkupdateross.repository.parseUptodownSearchResults
import com.apkupdateross.repository.parseUptodownVersions
import com.apkupdateross.repository.selectLatestUptodownVersion
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UptodownApiTest {
    @Test
    fun parsesCurrentSearchResponseAndSkipsInvalidPackages() {
        val response = JsonParser.parseString(
            """
            {
              "items": [
                {
                  "id": 17018,
                  "name": "Gmail",
                  "authorName": "Google LLC",
                  "description": "Mail",
                  "packagename": "com.google.android.gm",
                  "iconURL": "https://example.test/icon"
                },
                {"id": 2, "name": "Broken", "packagename": "not-a-package"}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val results = parseUptodownSearchResults(response)

        assertEquals(1, results.size)
        assertEquals("17018", results.single().appId)
        assertEquals("com.google.android.gm", results.single().packageName)
    }

    @Test
    fun parsesRealVersionCodeAndCompatibilityMetadata() {
        val response = JsonParser.parseString(
            """
            {
              "success": 1,
              "data": [{
                "fileID": 1239057589,
                "version": "2026.11.02.1020540846.Release",
                "sizeInBytes": 217622031,
                "minSDKVersion": "Android + 7.0",
                "abi": "arm64-v8a",
                "abis": ["arm64-v8a", "armeabi-v7a"],
                "versionCode": 66160196,
                "sha256": "b623783b05a5ec9d47b4c047fde5499909d071a462bdc1ef10e4365b1416152c",
                "sdkVersion": 24,
                "fileType": "apk"
              }]
            }
            """.trimIndent()
        ).asJsonObject

        val version = parseUptodownVersions(response).single()

        assertEquals("1239057589", version.fileId)
        assertEquals(66160196L, version.versionCode)
        assertEquals(24, version.minSdk)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), version.arches)
    }

    @Test
    fun arm32DeviceDoesNotReceiveNewerArm64OnlyBuild() {
        val versions = listOf(
            remoteVersion("2.0", 200, listOf("arm64-v8a")),
            remoteVersion("1.9", 190, listOf("armeabi-v7a"))
        )

        val selected = selectLatestUptodownVersion(versions, listOf("armeabi-v7a"), 34)

        assertNotNull(selected)
        assertEquals(190L, selected?.versionCode)
    }

    @Test
    fun buildRequiringNewerAndroidIsRejected() {
        val versions = listOf(remoteVersion("2.0", 200, emptyList(), minSdk = 35))

        assertNull(selectLatestUptodownVersion(versions, listOf("arm64-v8a"), 34))
    }

    private fun remoteVersion(
        version: String,
        code: Long,
        arches: List<String>,
        minSdk: Int = 24
    ) = UptodownRemoteVersion(
        fileId = code.toString(),
        version = version,
        versionCode = code,
        size = code,
        minSdk = minSdk,
        arches = arches,
        fileType = "apk",
        sha256 = null
    )
}
