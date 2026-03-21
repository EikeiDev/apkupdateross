package com.apkupdateross.repository

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkupdateross.data.rustore.RuStoreDownloadRequest
import com.apkupdateross.data.rustore.toAppUpdate
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.prefs.RuStore404Entry
import com.apkupdateross.service.RuStoreService
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicBoolean


class RuStoreRepository(
    private val service: RuStoreService,
    private val prefs: Prefs
) {

    companion object {
        private const val BASE_BATCH_SIZE = 1
        private const val BOOSTED_BATCH_SIZE = 2
        private const val MIN_BATCH_DELAY_MS = 300L
        private const val MAX_BATCH_DELAY_MS = 1200L
        private const val DELAY_STEP_MS = 150L
        private const val MAX_RATE_LIMIT_RETRIES = 1
        private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        private const val RUSTORE_404_TTL_MS = 48 * 60 * 60 * 1000L
    }

    @Volatile
    private var currentBatchDelayMs = MIN_BATCH_DELAY_MS
    @Volatile
    private var lastRateLimitTimestamp = 0L

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val ignoredPackages = getActiveRuStore404Set()
        val filteredApps = apps.filterNot {
            val ignore = ignoredPackages.contains(it.packageName)
            if (ignore) {
                Log.d("RuStoreRepository", "Skipping ${it.packageName} due to cached 404")
            }
            ignore
        }

        val updates = processAppsInBatches(filteredApps) { app, markRateLimit ->
            retryOnRateLimit {
                var hitRateLimit = false
                val result = runCatching {
                    val response = service.getAppDetails(app.packageName)
                    if (response.code == "OK" && response.body != null) {
                        clearRuStore404(app.packageName)
                        val details = response.body
                        if (Version(details.versionName) > Version(app.version)) {
                            val downloadUrl = getDownloadUrl(details.appId, details.minSdkVersion)
                            details.toAppUpdate(app, downloadUrl)
                        } else null
                    } else null
                }.onFailure { exception ->
                    if (exception is HttpException && exception.code() == 429) {
                        hitRateLimit = true
                        Log.w("RuStoreRepository", "Rate limit hit for ${app.packageName}, skipping")
                        markRateLimit()
                    } else if (exception is HttpException && exception.code() == 404) {
                        markRuStore404(app.packageName)
                        Log.w("RuStoreRepository", "RuStore returned 404 for ${app.packageName}, caching")
                    } else {
                        Log.e("RuStoreRepository", "Error checking update for ${app.packageName}", exception)
                    }
                }.getOrNull()

                AttemptResult(result, hitRateLimit)
            }
        }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("RuStoreRepository", "Error looking for updates.", it)
    }

    suspend fun search(text: String) = flow {
        val response = service.searchApps(text)
        if (response.code == "OK" && response.body != null) {
            val ignoredPackages = getActiveRuStore404Set()
            val updates = processAppsInBatches(response.body.content.filterNot {
                val ignore = ignoredPackages.contains(it.packageName)
                if (ignore) {
                    Log.d("RuStoreRepository", "Skipping ${it.packageName} in search due to cached 404")
                }
                ignore
            }.take(10)) { searchApp, markRateLimit ->
                retryOnRateLimit {
                    var hitRateLimit = false
                    val result = runCatching {
                        val detailsResponse = service.getAppDetails(searchApp.packageName)
                        if (detailsResponse.code == "OK" && detailsResponse.body != null) {
                            clearRuStore404(searchApp.packageName)
                            val details = detailsResponse.body
                            val downloadUrl = getDownloadUrl(details.appId, details.minSdkVersion)
                            details.toAppUpdate(null, downloadUrl)
                        } else null
                    }.onFailure { exception ->
                        if (exception is HttpException && exception.code() == 429) {
                            hitRateLimit = true
                            Log.w("RuStoreRepository", "Rate limit hit for ${searchApp.packageName}, skipping")
                            markRateLimit()
                        } else if (exception is HttpException && exception.code() == 404) {
                            markRuStore404(searchApp.packageName)
                            Log.w("RuStoreRepository", "RuStore returned 404 for ${searchApp.packageName}, caching")
                        } else {
                            Log.e("RuStoreRepository", "Error fetching details for ${searchApp.packageName}", exception)
                        }
                    }.getOrNull()

                    AttemptResult(result, hitRateLimit)
                }
            }
            emit(Result.success(updates))
        } else {
            emit(Result.success(emptyList()))
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("RuStoreRepository", "Error searching.", it)
    }

    private suspend fun <T, R> processAppsInBatches(
        items: List<T>,
        batchSize: Int = BASE_BATCH_SIZE,
        processor: suspend (T, () -> Unit) -> R?
    ): List<R> = coroutineScope {
        val results = mutableListOf<R>()
        val effectiveBatchSize = determineBatchSize(batchSize)
        val batches = items.chunked(effectiveBatchSize)
        batches.forEachIndexed { index, batch ->
            val chunkRateLimitHit = AtomicBoolean(false)

            val batchResults = batch.map { item ->
                async {
                    processor(item) {
                        chunkRateLimitHit.set(true)
                        markRateLimit()
                    }
                }
            }.awaitAll()

            results.addAll(batchResults.filterNotNull())

            if (chunkRateLimitHit.get()) {
                increaseDelay()
            } else {
                decreaseDelay()
            }

            if (index < batches.lastIndex) {
                delay(currentBatchDelayMs)
            }
        }
        results
    }

    private suspend fun getDownloadUrl(appId: Long, minSdkVersion: Int): String {
        return retryOnRateLimit {
            var hitRateLimit = false
            val result = runCatching {
                val request = RuStoreDownloadRequest(
                    appId = appId,
                    sdkVersion = Build.VERSION.SDK_INT.coerceAtLeast(minSdkVersion),
                    supportedAbis = Build.SUPPORTED_ABIS.toList()
                )
                val response = service.getDownloadLink(request)
                if (response.code == "OK" && response.body != null) {
                    clearRuStore404(request.appId.toString())
                    response.body.downloadUrls.firstOrNull()?.url ?: ""
                } else {
                    Log.w("RuStoreRepository", "Download link response not OK for appId=$appId")
                    ""
                }
            }.onFailure { exception ->
                if (exception is HttpException && exception.code() == 429) {
                    hitRateLimit = true
                    Log.w("RuStoreRepository", "Rate limit hit getting download link for appId=$appId")
                    markRateLimit()
                } else if (exception is HttpException && exception.code() == 404) {
                    markRuStore404(appId)
                    Log.w("RuStoreRepository", "Download link 404 for appId=$appId, caching")
                } else {
                    Log.e("RuStoreRepository", "Error getting download link for appId=$appId", exception)
                }
            }.getOrDefault("")

            AttemptResult(result.takeIf { it.isNotEmpty() }, hitRateLimit)
        } ?: ""
    }

    private data class AttemptResult<R>(val value: R?, val hitRateLimit: Boolean)

    private suspend fun <R> retryOnRateLimit(
        maxRetries: Int = MAX_RATE_LIMIT_RETRIES,
        attempt: suspend () -> AttemptResult<R>
    ): R? {
        var retries = 0
        while (true) {
            val result = attempt()
            if (result.value != null) {
                return result.value
            }

            if (result.hitRateLimit && retries < maxRetries) {
                delay(currentBatchDelayMs)
                retries++
                continue
            }

            return null
        }
    }

    @Synchronized
    private fun increaseDelay() {
        val newDelay = (currentBatchDelayMs + DELAY_STEP_MS).coerceAtMost(MAX_BATCH_DELAY_MS)
        if (newDelay != currentBatchDelayMs) {
            currentBatchDelayMs = newDelay
            Log.i("RuStoreRepository", "Increased batch delay to ${currentBatchDelayMs}ms")
        }
    }

    @Synchronized
    private fun decreaseDelay() {
        val newDelay = (currentBatchDelayMs - DELAY_STEP_MS).coerceAtLeast(MIN_BATCH_DELAY_MS)
        if (newDelay != currentBatchDelayMs) {
            currentBatchDelayMs = newDelay
            Log.i("RuStoreRepository", "Decreased batch delay to ${currentBatchDelayMs}ms")
        }
    }

    private fun markRateLimit() {
        increaseDelay()
        lastRateLimitTimestamp = SystemClock.elapsedRealtime()
    }

    private fun determineBatchSize(base: Int): Int {
        return if (shouldUseParallelBatches()) BOOSTED_BATCH_SIZE else base
    }

    private fun shouldUseParallelBatches(): Boolean {
        val sinceLastRateLimit = SystemClock.elapsedRealtime() - lastRateLimitTimestamp
        return currentBatchDelayMs == MIN_BATCH_DELAY_MS && sinceLastRateLimit > RATE_LIMIT_COOLDOWN_MS
    }

    private fun getActiveRuStore404Set(): Set<String> {
        val now = System.currentTimeMillis()
        val entries = prefs.ruStore404Packages.get()
        val filtered = entries.filter { now - it.timestamp <= RUSTORE_404_TTL_MS }
        if (filtered.size != entries.size) {
            prefs.ruStore404Packages.put(filtered)
        }
        return filtered.map { it.packageName }.toSet()
    }

    private fun markRuStore404(identifier: Any) {
        val packageName = identifier.toString()
        val now = System.currentTimeMillis()
        updateRuStore404Packages { list ->
            val without = list.filterNot { it.packageName == packageName }
            without + RuStore404Entry(packageName, now)
        }
    }

    private fun clearRuStore404(packageName: String) {
        updateRuStore404Packages { list ->
            val filtered = list.filterNot { it.packageName == packageName }
            if (filtered.size == list.size) list else filtered
        }
    }

    @Synchronized
    private fun updateRuStore404Packages(transform: (List<RuStore404Entry>) -> List<RuStore404Entry>) {
        val current = prefs.ruStore404Packages.get()
        val updated = transform(current)
        if (updated !== current) {
            prefs.ruStore404Packages.put(updated)
        }
    }

}
