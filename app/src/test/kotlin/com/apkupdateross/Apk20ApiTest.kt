package com.apkupdateross

import com.apkupdateross.repository.isApk20Update
import com.apkupdateross.repository.parseApk20AppDetails
import com.apkupdateross.repository.parseApk20SearchResults
import com.apkupdateross.repository.parseApk20Variants
import com.apkupdateross.repository.selectApk20Variant
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Apk20ApiTest {
    @Test
    fun parsesPublicApiSearchResults() {
        val root = JsonParser.parseString(
            """
            {
              "items": [
                {
                  "packageName": "com.google.android.gm",
                  "title": "Gmail",
                  "developerName": "Google LLC",
                  "shortDescription": "Mail",
                  "icon": "https://example.test/gmail.png"
                },
                {"packageName": "not-a-package", "title": "Broken"}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val item = parseApk20SearchResults(root).single()

        assertEquals("com.google.android.gm", item.packageName)
        assertEquals("Gmail", item.name)
        assertEquals("Google LLC", item.author)
    }

    @Test
    fun parsesTupleAndKeepsRealVersionCode() {
        val root = JsonParser.parseString(
            """
            [
              {
                "packageName": "com.google.android.gm",
                "title": "Gmail",
                "descriptionHtml": "New <b>mail</b> features",
                "developer": {"name": "Google LLC"},
                "media": {"icon": {"imageUrl": "https://example.test/icon.png"}},
                "lastVersion": {
                  "packageName": "com.google.android.gm",
                  "versionCode": 66177385,
                  "versionString": "2026.11.09.1019722178.Release",
                  "recentChangesHtml": "Latest <b>fixes</b>"
                }
              },
              [{
                "packageName": "com.google.android.gm",
                "versionCode": 66160196,
                "versionString": "2026.11.02.1020540846.Release"
              }]
            ]
            """.trimIndent()
        )

        val details = parseApk20AppDetails(root)

        assertEquals("Google LLC", details.author)
        assertEquals("New mail features", details.description)
        assertEquals(2, details.versions.size)
        assertEquals(66177385L, details.versions.first().versionCode)
        assertEquals("Latest fixes", details.versions.first().whatsNew)
    }

    @Test
    fun selectsDeviceAbiAndRejectsNewerAndroid() {
        val variants = parseApk20Variants(JsonParser.parseString(
            """
            [
              {"variantId":"arm64","architecture":"arm64-v8a","versionCode":20,"versionString":"2.0","minSdk":24,"isBundle":false,"fileSize":100},
              {"variantId":"arm32","architecture":"armeabi-v7a","versionCode":20,"versionString":"2.0","minSdk":24,"isBundle":false,"fileSize":90},
              {"variantId":"universal","architecture":"universal","versionCode":20,"versionString":"2.0","minSdk":35,"isBundle":true,"fileSize":150}
            ]
            """.trimIndent()
        ))

        val selected = selectApk20Variant(variants, listOf("armeabi-v7a"), 34)

        assertEquals("arm32", selected?.variantId)
        assertNull(selectApk20Variant(variants, listOf("x86"), 34))
    }

    @Test
    fun updateRequiresHigherCodeAndNonOlderVersionName() {
        assertTrue(isApk20Update("2.1", 201, "2.0", 200))
        assertTrue(isApk20Update("2.0", 201, "2.0", 200))
        assertFalse(isApk20Update("2.1", 200, "2.0", 200))
        assertFalse(isApk20Update("1.9", 201, "2.0", 200))
    }
}
