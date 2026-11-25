package com.apkupdateross

import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.monotonicInstallProgress
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallLogProgressTest {

    @Test
    fun completedDownloadDoesNotRestartDuringInstallation() {
        val previous = AppInstallProgress(id = 7, progress = 100L, total = 100L)
        val installer = AppInstallProgress(id = 7, progress = 94L, total = 100L)

        assertEquals(previous, monotonicInstallProgress(previous, installer))
    }

    @Test
    fun progressRatioDoesNotRegressWhenPhaseTotalChanges() {
        val download = AppInstallProgress(id = 7, progress = 50L, total = 100L)
        val installer = AppInstallProgress(id = 7, progress = 20L, total = 200L)

        assertEquals(
            AppInstallProgress(id = 7, progress = 100L, total = 200L),
            monotonicInstallProgress(download, installer)
        )
    }

    @Test
    fun forwardProgressIsPreserved() {
        val previous = AppInstallProgress(id = 7, progress = 25L, total = 100L)
        val next = AppInstallProgress(id = 7, progress = 80L, total = 100L)

        assertEquals(next, monotonicInstallProgress(previous, next))
    }

    @Test
    fun emitProgressDoesNotHijackActiveSessionCancel() = runBlocking {
        val installLog = InstallLog()
        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) { installLog.status().first() }
        }

        installLog.markActiveSessionInstall(10)
        installLog.emitProgress(AppInstallProgress(id = 20, progress = 50L, total = 100L))

        assertTrue(installLog.cancelCurrentInstall())
        val status: AppInstallStatus = deferred.await()
        assertEquals(10, status.id)
        assertFalse(status.success)
        assertFalse(status.snack)
        assertEquals(InstallFailureReason.CANCELLED, status.reason)
        assertFalse(installLog.cancelCurrentInstall())
    }
}
