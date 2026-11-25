package com.apkupdateross

import com.apkupdateross.util.PRIVILEGED_INSTALL_FLAGS
import com.apkupdateross.util.containsNestedApkFiles
import com.apkupdateross.util.downloadCacheFileName
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionInstallerPolicyTest {

    @Test
    fun privilegedInstallAllowsReplacementAndDowngrade() {
        assertEquals("-r -d", PRIVILEGED_INSTALL_FLAGS)
    }

    @Test
    fun downloadedApkCacheFileKeepsApkExtension() {
        assertEquals("cache_42_token.apk", downloadCacheFileName(42, "token"))
    }

    @Test
    fun detectsSplitArchiveByNestedApkInsteadOfUrl() {
        val archive = createZip("base.apk")
        try {
            assertTrue(archive.containsNestedApkFiles())
        } finally {
            archive.delete()
        }
    }

    @Test
    fun doesNotTreatStandaloneApkAsSplitArchive() {
        val archive = createZip("AndroidManifest.xml")
        try {
            assertFalse(archive.containsNestedApkFiles())
        } finally {
            archive.delete()
        }
    }

    @Test
    fun doesNotTreatStandaloneApkWithEmbeddedCompanionAsSplitArchive() {
        val archive = createZip("AndroidManifest.xml", "res/raw/FileTransfer.apk")
        try {
            assertFalse(archive.containsNestedApkFiles())
        } finally {
            archive.delete()
        }
    }

    private fun createZip(vararg entryNames: String): File =
        File.createTempFile("apkupdater-test-", ".zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entryNames.forEach { entryName ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(byteArrayOf(1, 2, 3))
                    zip.closeEntry()
                }
            }
        }
}
