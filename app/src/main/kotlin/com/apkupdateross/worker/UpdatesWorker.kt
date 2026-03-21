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

    override suspend fun doWork(): Result {
        return try {
            val rawUpdates = updatesRepository.updates().lastOrNull() ?: emptyList()
            val ignoredIds = prefs.ignoredVersions.get()
            val filteredUpdates = rawUpdates.filter { !ignoredIds.contains(it.id) }
            val uniqueUpdatesCount = filteredUpdates.distinctBy { it.packageName }.size
            
            if (uniqueUpdatesCount > 0) {
                notification.showUpdateNotification(uniqueUpdatesCount)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdatesWorker", "Error checking updates", e)
            if (isRetryable(e)) Result.retry() else Result.failure()
        }
    }

    private fun isRetryable(throwable: Throwable): Boolean {
        val root = throwable.cause ?: throwable
        return root is IOException ||
            root is TimeoutCancellationException ||
            (root is HttpException && root.code() >= 500)
    }
}
