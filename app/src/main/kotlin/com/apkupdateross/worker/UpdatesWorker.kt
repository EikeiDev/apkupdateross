package com.apkupdateross.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.util.UpdatesNotification
import com.apkupdateross.util.millisUntilHour
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.lastOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.isSplitPackageDownloadUrl
import com.apkupdateross.data.ui.resolvedUrl
import com.apkupdateross.data.ui.totalSize
import com.apkupdateross.data.ui.validationPackageName
import com.apkupdateross.data.ui.validationSize
import com.apkupdateross.data.ui.preferredUpdateComparator
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
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .apply {
                    if (prefs.autoUpdateBackground.get()) setRequiresBatteryNotLow(true)
                }
                .build()
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(getDays(), TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(
                    (millisUntilHour(prefs.alarmHour.get()) + randomDelay()).coerceAtLeast(0L),
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
                        val distinctUpdates = filteredUpdates
                            .groupBy { it.packageName }
                            .mapNotNull { (_, candidates) -> candidates.sortedWith(preferredUpdateComparator).firstOrNull() }
                        for (update in distinctUpdates) {
                            val success = withTimeoutOrNull(5 * 60 * 1000L) { // 5 minutes per app max
                                try {
                                    downloadAndInstallSilently(update, mode)
                                } catch (e: CancellationException) {
                                    throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("UpdatesWorker", "Error checking updates", e)
            if (isRetryable(e)) Result.retry() else Result.failure()
        }
    }

    private suspend fun downloadAndInstallSilently(update: AppUpdate, mode: Int): Boolean {
        return try {
            when (val link = update.link) {
                Link.Empty -> false
                is Link.BrowserDownload -> false
                is Link.Play -> {
                    val files = link.getInstallFiles()
                    if (files.count { it.type == com.aurora.gplayapi.data.models.File.FileType.BASE } != 1) {
                        throw Exception("Google Play APK set must contain exactly one base APK")
                    }
                    val total = files.sumOf { it.size }
                    val results = files.map { file ->
                        val result = downloader.downloadFileWithSize(update.id, file.url)
                            ?: throw Exception("Failed to download ${file.name}")
                        installer.validateApkFile(
                            result.file,
                            update.packageName.takeIf {
                                file.type == com.aurora.gplayapi.data.models.File.FileType.BASE
                            },
                            null,
                            file.size.coerceAtLeast(0L)
                        )
                        result
                    }
                    if (mode == 1) {
                        installer.rootInstallApks(results.map { it.file }, update.packageName)
                    } else {
                        installer.shizukuInstall(
                            update.id,
                            update.packageName,
                            results.map { it.file.inputStream() },
                            total
                        )
                        true
                    }
                }
                is Link.Url -> {
                    val url = link.resolvedUrl()
                    val result = downloader.downloadFileWithSize(update.id, url) ?: throw Exception("Download failed")
                    val isSplitPackage = link.isSplitPackage(url, result.url) || installer.isSplitPackageFile(result.file)
                    if (isSplitPackage) {
                        installer.validateSplitPackageFile(
                            result.file,
                            link.validationPackageName(update.packageName) ?: update.packageName,
                            link.sha256,
                            link.validationSize()
                        )
                    } else {
                        installer.validateApkFile(
                            result.file,
                            link.validationPackageName(update.packageName) ?: update.packageName,
                            link.sha256,
                            link.validationSize()
                        )
                    }
                    if (mode == 1) {
                        val success = if (isSplitPackage) {
                            installer.rootInstallXapk(result.file, update.packageName)
                        } else {
                            installer.rootInstall(result.file, update.packageName)
                        }
                        success
                    } else {
                        val total = link.totalSize(result.contentLength, result.file.length())
                        if (isSplitPackage) {
                            installer.shizukuInstallXapk(update.id, update.packageName, result.file, total)
                        } else {
                            installer.shizukuInstall(update.id, update.packageName, result.file.inputStream(), total)
                        }
                        true
                    }
                }
                is Link.Xapk -> {
                    val result = downloader.downloadFileWithSize(update.id, link.link) ?: throw Exception("Download failed")
                    installer.validateSplitPackageFile(result.file, update.packageName)
                    if (mode == 1) {
                        installer.rootInstallXapk(result.file, update.packageName)
                    } else {
                        installer.shizukuInstallXapk(
                            update.id,
                            update.packageName,
                            result.file,
                            result.file.length()
                        )
                        true
                    }
                }
                else -> false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("UpdatesWorker", "downloadAndInstallSilently failed", error)
            false
        } finally {
            downloader.cleanup(update.id)
        }
    }

    private fun isRetryable(throwable: Throwable): Boolean {
        val root = throwable.cause ?: throwable
        return root is IOException ||
            root is TimeoutCancellationException ||
            (root is HttpException && root.code() >= 500)
    }
}

private fun Link.Url.isSplitPackage(requestUrl: String, resolvedUrl: String): Boolean =
    link.isSplitPackageDownloadUrl() ||
        requestUrl.isSplitPackageDownloadUrl() ||
        resolvedUrl.isSplitPackageDownloadUrl()
