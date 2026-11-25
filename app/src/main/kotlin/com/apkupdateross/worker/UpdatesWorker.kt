package com.apkupdateross.worker

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.HistoryEventType
import com.apkupdateross.data.ui.InstallHistoryEntry
import com.apkupdateross.data.ui.updateComparator
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.util.AppInstallExecutor
import com.apkupdateross.util.InstallExecutionResult
import com.apkupdateross.util.InstallMethod
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.UpdatesNotification
import com.apkupdateross.util.millisUntilHour
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random


class UpdatesWorker(
    context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context, workerParams), KoinComponent {

    companion object: KoinComponent {
        private const val TAG = "UpdatesWorker"
        private val prefs: Prefs by inject()

        fun cancel(workManager: WorkManager) = workManager.cancelUniqueWork(TAG)

        fun launch(workManager: WorkManager) {
            val networkType = if (prefs.backgroundCheckWifiOnly.get()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
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
    private val installer: SessionInstaller by inject()
    private val installExecutor: AppInstallExecutor by inject()

    override suspend fun doWork(): Result {
        return try {
            val rawUpdates = updatesRepository.updates().lastOrNull() ?: emptyList()
            val ignoredIds = prefs.ignoredVersions.get()
            val priorityOrder = prefs.sourcePriorityOrder.get()
            val pinnedSources = prefs.pinnedAppSources.get()
            val filteredUpdates = rawUpdates.filter { !ignoredIds.contains(it.id) }
            val updates = filteredUpdates
                .groupBy { it.packageName }
                .mapNotNull { (packageName, candidates) ->
                    candidates.sortedWith(updateComparator(priorityOrder, pinnedSources[packageName])).firstOrNull()
                }

            if (updates.isEmpty()) {
                prefs.appendInstallHistory(
                    InstallHistoryEntry(
                        eventType = HistoryEventType.BACKGROUND_CHECK,
                        success = true,
                        details = "0"
                    )
                )
                notification.cancelUpdateNotification()
                notification.cancelBackgroundStatusNotification()
                if (prefs.notifyNoUpdates.get()) notification.showNoUpdatesNotification()
                return Result.success()
            }

            prefs.appendInstallHistory(
                InstallHistoryEntry(
                    eventType = HistoryEventType.BACKGROUND_CHECK,
                    success = true,
                    details = updates.size.toString()
                )
            )
            notification.cancelBackgroundStatusNotification()
            val mode = prefs.installMode.get()
            val autoInstallRequested = prefs.autoUpdateBackground.get() && mode > 0
            val connectionAllowsInstall = !prefs.autoInstallWifiOnly.get() || !isActiveNetworkMetered()
            val shizukuAvailable = mode != 2 || installer.isShizukuAvailable()
            val installedPackages = mutableSetOf<String>()
            var installedCount = 0
            var failedCount = 0

            if (autoInstallRequested && connectionAllowsInstall && shizukuAvailable) {
                for (update in updates) {
                    val outcome = withTimeoutOrNull(5 * 60 * 1000L) {
                        downloadAndInstallSilently(update, mode)
                    } ?: Pair(false, "Timed out")
                    prefs.appendInstallHistory(
                        InstallHistoryEntry(
                            packageName = update.packageName,
                            appName = update.name,
                            version = update.version,
                            sourceName = update.source.name,
                            eventType = HistoryEventType.BACKGROUND_INSTALL,
                            success = outcome.first,
                            details = outcome.second
                        )
                    )
                    if (outcome.first) {
                        installedCount++
                        installedPackages.add(update.packageName)
                    } else {
                        failedCount++
                    }
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

            val remainingUpdates = updates.filterNot { it.packageName in installedPackages }
            if (remainingUpdates.isNotEmpty() && prefs.notifyAvailableUpdates.get()) {
                notification.showUpdateNotification(remainingUpdates.size, remainingUpdates)
            } else {
                notification.cancelUpdateNotification()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error checking updates", e)
            prefs.appendInstallHistory(
                InstallHistoryEntry(
                    eventType = HistoryEventType.BACKGROUND_CHECK,
                    success = false,
                    details = e.message.orEmpty()
                )
            )
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

    private suspend fun downloadAndInstallSilently(update: AppUpdate, mode: Int): Pair<Boolean, String> {
        return try {
            when (val result = installExecutor.execute(
                id = update.id,
                packageName = update.packageName,
                link = update.link,
                method = InstallMethod.fromMode(mode),
                emitProgress = false
            )) {
                InstallExecutionResult.Succeeded -> true to ""
                is InstallExecutionResult.Unavailable -> false to result.message
                is InstallExecutionResult.Failed -> false to result.details
                InstallExecutionResult.Dispatched -> false to ""
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("UpdatesWorker", "downloadAndInstallSilently failed", error)
            false to error.message.orEmpty()
        }
    }

    private fun isRetryable(throwable: Throwable): Boolean {
        val root = throwable.cause ?: throwable
        return root is IOException ||
            root is TimeoutCancellationException ||
            (root is HttpException && root.code() >= 500)
    }
}
