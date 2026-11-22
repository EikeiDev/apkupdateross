package com.apkupdateross

import com.apkupdateross.util.copyToWithProgress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DownloaderProgressTest {

    @Test
    fun reportsNetworkCopyProgressFromZeroToTotal() {
        val payload = ByteArray(32) { it.toByte() }
        val output = ByteArrayOutputStream()
        val reports = mutableListOf<Pair<Long, Long>>()
        var now = 0L

        val copied = ByteArrayInputStream(payload).copyToWithProgress(
            output = output,
            total = payload.size.toLong(),
            onProgress = { downloaded, total -> reports += downloaded to total },
            bufferSize = 4,
            nanoTime = {
                now += 200_000_000L
                now
            }
        )

        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
        assertEquals(0L to payload.size.toLong(), reports.first())
        assertEquals(payload.size.toLong() to payload.size.toLong(), reports.last())
        assertTrue(reports.zipWithNext().all { (first, second) -> first.first <= second.first })
    }
}
