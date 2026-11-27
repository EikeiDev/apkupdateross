package com.apkupdateross

import com.apkupdateross.repository.hasValidPlayDownloadUrls
import com.apkupdateross.repository.resolvePlayFilesWithRetry
import com.aurora.gplayapi.PlayException
import com.aurora.gplayapi.model.AppFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlayDeliveryValidationTest {

    @Test
    fun rejectsEmptyOrMissingDownloadUrls() {
        assertFalse(hasValidPlayDownloadUrls(emptyList()))
        assertFalse(hasValidPlayDownloadUrls(listOf(AppFile(name = "base.apk", url = ""))))
        assertFalse(hasValidPlayDownloadUrls(listOf(AppFile(name = "base.apk", url = "http://play.googleapis.com/base.apk"))))
        assertFalse(
            hasValidPlayDownloadUrls(
                listOf(
                    AppFile(name = "base.apk", url = "https://play.googleapis.com/base.apk"),
                    AppFile(name = "split.apk", url = "")
                )
            )
        )
    }

    @Test
    fun acceptsValidHttpsDownloadUrls() {
        assertTrue(
            hasValidPlayDownloadUrls(
                listOf(
                    AppFile(name = "base.apk", url = "https://play.googleapis.com/base.apk"),
                    AppFile(name = "split.apk", url = "https://play.googleapis.com/split.apk")
                )
            )
        )
    }

    @Test
    fun retriesOnUnauthorizedAndInvalidatesSession() = runBlocking {
        var attempts = 0
        var invalidations = 0
        val expected = listOf(AppFile(name = "base.apk", url = "https://play.googleapis.com/base.apk"))

        val files = resolvePlayFilesWithRetry(
            maxAttempts = 3,
            baseDelayMs = 10L,
            delayFn = {},
            onInvalidateSession = { invalidations++ },
            fetch = {
                attempts++
                if (attempts == 1) throw PlayException.Unauthorized(401)
                expected
            }
        )

        assertEquals(expected, files)
        assertEquals(2, attempts)
        assertEquals(1, invalidations)
    }

    @Test
    fun retriesWithBackoffOnRateLimitAndInvalidatesBeforeFinalAttempt() = runBlocking {
        var attempts = 0
        var invalidations = 0
        val delays = mutableListOf<Long>()
        val expected = listOf(AppFile(name = "base.apk", url = "https://play.googleapis.com/base.apk"))

        val files = resolvePlayFilesWithRetry(
            maxAttempts = 3,
            baseDelayMs = 100L,
            delayFn = { delays += it },
            onInvalidateSession = { invalidations++ },
            fetch = {
                attempts++
                if (attempts < 3) throw PlayException.RateLimited(429)
                expected
            }
        )

        assertEquals(expected, files)
        assertEquals(3, attempts)
        assertEquals(1, invalidations)
        assertEquals(listOf(100L, 200L), delays)
    }

    @Test
    fun failsFastOnNonRetryablePlayErrors() = runBlocking {
        var attempts = 0
        var invalidations = 0

        try {
            resolvePlayFilesWithRetry(
                maxAttempts = 3,
                baseDelayMs = 100L,
                delayFn = {},
                onInvalidateSession = { invalidations++ },
                fetch = {
                    attempts++
                    throw PlayException.AppNotSupported(2)
                }
            )
            fail("Expected AppNotSupported")
        } catch (_: PlayException.AppNotSupported) {
            assertEquals(1, attempts)
            assertEquals(0, invalidations)
        }
    }
}
