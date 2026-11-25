package com.apkupdateross

import com.apkupdateross.util.safeDownloadFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNameTest {

    @Test
    fun keepsExtensionAndFitsUtf8FileSystemLimit() {
        val name = ("Приложение😀".repeat(40) + ".apks").safeDownloadFileName()

        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(name.endsWith(".apks"))
    }

    @Test
    fun replacesUnsafeCharacters() {
        assertEquals("bad_name_.apk", "bad/name?.apk".safeDownloadFileName())
    }
}
