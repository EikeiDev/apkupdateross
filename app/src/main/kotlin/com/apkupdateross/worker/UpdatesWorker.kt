package com.apkupdateross.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.util.UpdatesNotification
import com.apkupdateross.util.millisUntilHour
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.lastOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.SessionInstaller
import kotlinx.coroutines.withTimeoutOrNull


class UpdatesWorker(
    context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context, workerParams), KoinComponent {

    companion object: KoinComponent {
        private const val TAG = "UpdatesWorker"
        private val prefs: Prefs by inject()

        fun cancel(workManager: WorkManager) = workManager.cancelUniqueWork(TAG)

        fun launch(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(getDays(), TimeUnit.DAYS)
                .setInitialDelay(
                    millisUntilHour(prefs.alarmHour.get()) + randomDelay(),
                    TimeUnit.MILLISECONDS
                ).build()
            workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun randomDelay() = if (prefs.useApkMirror.get())
            Random.nextLong(0, 59 * 60 * 1_000)
        else
            Random.nextLong(-5 * 60 * 1_000, 5 * 60 * 1_000)

        private fun getDays() = when(prefs.alarmFrequency.get()) {
            0 -> 1L
            1 -> 3L
            2 -> 7L
            else -> 1L
        }
    }

    private val updatesRepository: UpdatesRepository by inject()
    private val notification: UpdatesNotification by inject()
    private val downloader: Downloader by inject()
    private val installer: SessionInstaller by inject()

    override suspend fun doWork(): Result {
        return try {
            val rawUpdates = updatesRepository.updates().lastOrNull() ?: emptyList()
            val ignoredIds = prefs.ignoredVersions.get()
            val filteredUpdates = rawUpdates.filter { !ignoredIds.contains(it.id) }
            val uniqueUpdatesCount = filteredUpdates.distinctBy { it.packageName }.size
            
            if (uniqueUpdatesCount > 0) {
                if (prefs.autoUpdateBackground.get() && prefs.installMode.get() > 0) {
                    val mode = prefs.installMode.get()
                    val shizuku = installer.isShizukuAvailable()
                    if ((mode == 1) || (mode == 2 && shizuku)) {
                        var remainingCount = uniqueUpdatesCount
                        val distinctUpdates = filteredUpdates.distinctBy { it.packageName }
                        for (update in distinctUpdates) {
                            val success = withTimeoutOrNull(5 * 60 * 1000L) { // 5 minutes per app max
                                try {
                                    downloadAndInstallSilently(update, mode)
                                } catch (e: Exception) {
                                    Log.e("UpdatesWorker", "Failed to auto-update ${update.packageName}", e)
                                    false
                                }
                            } ?: false
                            
                            if (success) {
                                remainingCount--
                                // Also remove from ignored/updates state so it won't be shown again if UI opens? 
                                // Not strictly necessary as next repository fetch won't return it
                            } else {
                                notification.showUpdateFailedNotification(update.name)
                            }
                        }
                        if (remainingCount > 0) {
                            notification.showUpdateNotification(remainingCount)
                        }
                    } else {
                        notification.showUpdateNotification(uniqueUpdatesCount)
                    }
                } else {
                    notification.showUpdateNotification(uniqueUpdatesCount)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdatesWorker", "Error checking updates", e)
            if (isRetryable(e)) Result.retry() else Result.failure()
        }
    }

    private suspend fun downloadAndInstallSilently(update: AppUpdate, mode: Int): Boolean {
        return runCatching {
            when (val link = update.link) {
                Link.Empty -> false
                is Link.Play -> {
                    val files = link.getInstallFiles()
                    val total = files.sumOf { it.size }
                    val streams = files.map { downloader.downloadStream(update.id, it.url) ?: throw Exception("Failed to open stream") }
                    if (mode == 1) throw Exception("Root Play install not supported natively in worker")
                    else installer.shizukuInstall(update.id, update.packageName, streams, total)
                    downloader.cleanup(update.id)
                    true
                }
                is Link.Url -> {
                    val result = downloader.downloadWithSize(update.id, link.link) ?: throw Exception("Download failed")
                    if (mode == 1) {
                        val file = java.io.File(applicationContext.cacheDir, "${java.util.UUID.randomUUID()}.apk")
                        result.stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                        val success = installer.rootInstall(file)
                        downloader.cleanup(update.id)
                        success
                    } else {
                        val total = if (link.size > 0) link.size else result.contentLength
                        installer.shizukuInstall(update.id, update.packageName, result.stream, total)
                        downloader.cleanup(update.id)
                        true
                    }
                }
                is Link.Xapk -> {
                    val result = downloader.downloadWithSize(update.id, link.link) ?: throw Exception("Download failed")
                    if (mode == 1) {
                        val file = java.io.File(applicationContext.cacheDir, "${java.util.UUID.randomUUID()}.xapk")
                        result.stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                        val success = installer.rootInstallXapk(file)
                        downloader.cleanup(update.id)
                        success
                    } else {
                        installer.shizukuInstallXapk(update.id, update.packageName, result.stream, result.contentLength)
                        downloader.cleanup(update.id)
                        true
                    }
                }
                else -> false
            }
        }.getOrElse {
            downloader.cleanup(update.id)
            Log.e("UpdatesWorker", "downloadAndInstallSilently failed", it)
            false
        }
    }

    private fun isRetryable(throwable: Throwable): Boolean {
        val root = throwable.cause ?: throwable
        return root is IOException ||
            root is TimeoutCancellationException ||
            (root is HttpException && root.code() >= 500)
    }
}
