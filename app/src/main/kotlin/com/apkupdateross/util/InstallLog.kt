package com.apkupdateross.util

import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.InstallFailureReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil


class InstallLog {

    private val status = MutableSharedFlow<AppInstallStatus>(extraBufferCapacity = 10)
    private val progress = MutableSharedFlow<AppInstallProgress>(extraBufferCapacity = 64)
    private val currentInstallId = AtomicInteger(NO_INSTALL_ID)
    private val latestProgress = ConcurrentHashMap<Int, AppInstallProgress>()

    fun status() = status.asSharedFlow()
    fun progress() = progress.asSharedFlow()

    fun markActiveSessionInstall(id: Int) {
        currentInstallId.set(id)
    }

    fun cancelCurrentInstall(): Boolean {
        val id = currentInstallId.getAndSet(NO_INSTALL_ID)
        latestProgress.remove(id)
        return id != NO_INSTALL_ID && status.tryEmit(
            AppInstallStatus(
                success = false,
                id = id,
                snack = false,
                reason = InstallFailureReason.CANCELLED
            )
        )
    }

    fun emitStatus(newStatus: AppInstallStatus): Boolean {
        currentInstallId.compareAndSet(newStatus.id, NO_INSTALL_ID)
        latestProgress.remove(newStatus.id)
        return status.tryEmit(newStatus)
    }

    fun emitProgress(newProgress: AppInstallProgress): Boolean {
        val normalized = if (newProgress.isExplicitReset()) {
            latestProgress.remove(newProgress.id)
            newProgress
        } else {
            latestProgress.compute(newProgress.id) { _, previous ->
                monotonicInstallProgress(previous, newProgress)
            } ?: newProgress
        }
        return progress.tryEmit(normalized)
    }

    private companion object {
        const val NO_INSTALL_ID = -1
    }

}

internal fun monotonicInstallProgress(
    previous: AppInstallProgress?,
    next: AppInstallProgress
): AppInstallProgress {
    if (previous == null) return next

    val previousProgress = previous.progress ?: return next
    val previousTotal = previous.total ?: return next
    val nextTotal = next.total ?: previousTotal
    val nextProgress = next.progress ?: previousProgress
    if (previousTotal <= 0L || nextTotal <= 0L) {
        return next.copy(progress = nextProgress, total = nextTotal)
    }

    val previousRatio = (previousProgress.toDouble() / previousTotal)
        .coerceIn(0.0, 1.0)
    val boundedNextProgress = nextProgress.coerceIn(0L, nextTotal)
    val nextRatio = boundedNextProgress.toDouble() / nextTotal
    if (nextRatio >= previousRatio) {
        return next.copy(progress = boundedNextProgress, total = nextTotal)
    }

    val clampedProgress = ceil(previousRatio * nextTotal)
        .toLong()
        .coerceIn(0L, nextTotal)
    return next.copy(progress = clampedProgress, total = nextTotal)
}

private fun AppInstallProgress.isExplicitReset(): Boolean = progress == 0L && total == null
