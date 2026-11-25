package com.apkupdateross

import com.apkupdateross.repository.hasValidPlayDownloadUrls
import com.aurora.gplayapi.data.models.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayDeliveryValidationTest {

    @Test
    fun rejectsEmptyOrMissingDownloadUrls() {
        assertFalse(hasValidPlayDownloadUrls(emptyList()))
        assertFalse(hasValidPlayDownloadUrls(listOf(File(name = "base.apk", url = ""))))
        assertFalse(hasValidPlayDownloadUrls(listOf(File(name = "base.apk", url = "http://play.googleapis.com/base.apk"))))
        assertFalse(
            hasValidPlayDownloadUrls(
                listOf(
                    File(name = "base.apk", url = "https://play.googleapis.com/base.apk"),
                    File(name = "split.apk", url = "")
                )
            )
        )
    }

    @Test
    fun acceptsValidHttpsDownloadUrls() {
        assertTrue(
            hasValidPlayDownloadUrls(
                listOf(
                    File(name = "base.apk", url = "https://play.googleapis.com/base.apk"),
                    File(name = "split.apk", url = "https://play.googleapis.com/split.apk")
                )
            )
        )
    }
}
