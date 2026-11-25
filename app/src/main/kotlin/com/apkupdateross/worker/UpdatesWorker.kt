package com.apkupdateross.worker

import android.content.Context
import android.net.ConnectivityManager
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
            val updates = filteredUpdates
                .groupBy { it.packageName }
                .mapNotNull { (_, candidates) -> candidates.sortedWith(preferredUpdateComparator).firstOrNull() }

            if (updates.isEmpty()) {
                notification.cancelUpdateNotification()
                notification.cancelBackgroundStatusNotification()
                if (prefs.notifyNoUpdates.get()) notification.showNoUpdatesNotification()
                return Result.success()
            }

            notification.cancelBackgroundStatusNotification()
            val mode = prefs.installMode.get()
            val autoInstallRequested = prefs.autoUpdateBackground.get() && mode > 0
            val connectionAllowsInstall = !prefs.autoInstallWifiOnly.get() || !isActiveNetworkMetered()
            val shizukuAvailable = mode != 2 || installer.isShizukuAvailable()
            var installedCount = 0
            var failedCount = 0

            if (autoInstallRequested && connectionAllowsInstall && shizukuAvailable) {
                for (update in updates) {
                    val success = withTimeoutOrNull(5 * 60 * 1000L) {
                        try {
                            downloadAndInstallSilently(update, mode)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.e(TAG, "Failed to auto-update ${update.packageName}", error)
                            false
                        }
                    } ?: false
                    if (success) installedCount++ else failedCount++
                }
            } else if (autoInstallRequested && !shizukuAvailable && prefs.notifyBackgroundErrors.get()) {
                notification.showAutoInstallUnavailableNotification()
            }

            if (installedCount > 0 && prefs.notifyAutoInstallSuccess.get()) {
                notification.showAutoInstallSuccessNotification(installedCount)
            }
            if (failedCount > 0 && prefs.notifyBackgroundErrors.get()) {
                notification.showAutoInstallFailedNotification(failedCount)
            }

            val remainingCount = updates.size - installedCount
            if (remainingCount > 0 && prefs.notifyAvailableUpdates.get()) {
                notification.showUpdateNotification(remainingCount)
            } else {
                notification.cancelUpdateNotification()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error checking updates", e)
            val retryable = isRetryable(e)
            if (prefs.notifyBackgroundErrors.get() && (!retryable || runAttemptCount >= 2)) {
                notification.showCheckFailedNotification()
            }
            if (retryable) Result.retry() else Result.failure()
        }
    }

    private fun isActiveNetworkMetered(): Boolean {
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.isActiveNetworkMetered
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
                    val result = downloader.downloadFileWithSize(
                        update.id,
                        url,
                        requestHeaders = link.requestHeaders
                    ) ?: throw Exception("Download failed")
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
