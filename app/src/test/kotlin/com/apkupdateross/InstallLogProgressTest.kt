package com.apkupdateross

import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.util.monotonicInstallProgress
import org.junit.Assert.assertEquals
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
}
