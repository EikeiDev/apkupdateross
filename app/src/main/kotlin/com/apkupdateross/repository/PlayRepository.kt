package com.apkupdateross.repository

import android.net.Uri
import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.getPackageNames
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.play.PlayClientProvider
import com.aurora.gplayapi.PlayException
import com.aurora.gplayapi.model.App
import com.aurora.gplayapi.model.AppFile
import com.aurora.gplayapi.model.Restriction
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException


class PlayRepository(
    private val clientProvider: PlayClientProvider,
    private val prefs: Prefs,
    private val stringer: Stringer
) {
    companion object {
        private const val TAG = "PlayRepository"
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_DELIVERY_ATTEMPTS = 3
        private const val BASE_RETRY_DELAY_MS = 1_500L
    }

    fun resetSession() {
        Log.i(TAG, "Resetting anonymous Google Play session.")
        clientProvider.resetSession()
    }

    private fun isAllowedPlayApp(app: App): Boolean {
        val ignoreAlpha = prefs.ignoreAlpha.get()
        val ignoreBeta = prefs.ignoreBeta.get()
        val ignorePre = prefs.ignorePreRelease.get()
        if (ignoreBeta && (app.earlyAccess || app.testingProgram?.isSubscribed == true)) return false
        return ReleaseType.from(app.versionName).isAllowed(ignoreAlpha, ignoreBeta, ignorePre)
    }

    private suspend fun <T> withAnonymousRecovery(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: PlayException.Unauthorized) {
            Log.w(TAG, "Anonymous Play session rejected (${e.code}), minting a fresh session and retrying.", e)
            clientProvider.resetSession()
            block()
        }
    }

    suspend fun search(text: String) = flow {
        val query = text.trim()
        if (query.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        if (!query.contains(" ") && query.contains(".")) {
            // Direct package name lookup via anonymous /fdfe/getItems
            val app = try {
                withAnonymousRecovery { clientProvider.playClient.apps.details(query) }
            } catch (_: PlayException.NotFound) {
                null
            }

            if (app == null || app.packageName.isBlank() || app.restriction != Restriction.AVAILABLE || !isAllowedPlayApp(app)) {
                emit(Result.success(emptyList()))
            } else {
                emit(Result.success(listOf(app.toAppUpdate(::getInstallFiles))))
            }
        } else {
            // Anonymous web search on play.google.com, then bulk hydrate via /fdfe/getItems
            val bundle = clientProvider.webPlayClient.search.results(query)
            val candidatePackages = bundle.clusters
                .asSequence()
                .flatMap { it.apps.asSequence() }
                .map { it.packageName }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SEARCH_RESULTS)
                .toList()

            if (candidatePackages.isEmpty()) {
                emit(Result.success(emptyList()))
                return@flow
            }

            val hydratedByPkg = withAnonymousRecovery {
                clientProvider.playClient.apps.details(candidatePackages)
            }.associateBy { it.packageName }

            val updates = candidatePackages
                .mapNotNull { hydratedByPkg[it] }
                .filter { it.restriction == Restriction.AVAILABLE && it.versionCode > 0L }
                .filter(::isAllowedPlayApp)
                .map { it.toAppUpdate(::getInstallFiles) }

            emit(Result.success(updates))
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(Result.failure(error))
        Log.e(TAG, "Error searching for $text.", error)
    }

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val packageNames = apps.getPackageNames()
        if (packageNames.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val timeoutSeconds = prefs.playTimeoutSec.get().coerceIn(5, 300)
        val timeout = timeoutSeconds * 1000L
        val appsByPkg = apps.associateBy { it.packageName }
        val allowDowngrade = prefs.installMode.get() > 0

        withTimeoutOrNull(timeout) {
            val details = withAnonymousRecovery {
                clientProvider.playClient.apps.details(packageNames)
            }

            val updates = details
                .filter { it.restriction == Restriction.AVAILABLE && it.versionCode > 0L }
                .filter {
                    val installed = appsByPkg[it.packageName] ?: return@filter false
                    when {
                        it.versionCode > installed.versionCode -> true
                        allowDowngrade && installed.versionCode > Int.MAX_VALUE.toLong() ->
                            runCatching { Version(it.versionName) > Version(installed.version) }.getOrDefault(false)
                        else -> false
                    }
                }
                .filter(::isAllowedPlayApp)
                .map {
                    val installed = appsByPkg[it.packageName]
                    it.toAppUpdate(
                        ::getInstallFiles,
                        installed?.version.orEmpty(),
                        installed?.versionCode ?: 0L
                    )
                }
            emit(updates)
        } ?: throw IllegalStateException("PlayStore update check timed out after $timeoutSeconds seconds")
    }

    private suspend fun getInstallFiles(app: App): List<AppFile> {
        val offerType = app.offerType.takeIf { it > 0 } ?: 1
        return try {
            resolvePlayFilesWithRetry(
                maxAttempts = MAX_DELIVERY_ATTEMPTS,
                baseDelayMs = BASE_RETRY_DELAY_MS,
                delayFn = { ms -> delay(ms) },
                onInvalidateSession = { clientProvider.resetSession() },
                fetch = {
                    clientProvider.playClient.delivery
                        .resolve(
                            packageName = app.packageName,
                            versionCode = app.versionCode,
                            offerType = offerType,
                            patchFormats = emptyList()
                        )
                        .filter { it.type == AppFile.Type.BASE || it.type == AppFile.Type.SPLIT }
                }
            )
        } catch (e: PlayException.RateLimited) {
            Log.w(TAG, "Google Play rate limit persisted for ${app.packageName}: $e")
            throw IOException(stringer.get(R.string.play_error_rate_limited), e)
        } catch (e: PlayException.AppNotSupported) {
            throw IOException(stringer.get(R.string.play_error_not_supported), e)
        } catch (e: PlayException.AppNotPurchased) {
            throw IOException(stringer.get(R.string.play_error_not_purchased), e)
        } catch (e: PlayException.AppRemoved) {
            throw IOException(stringer.get(R.string.play_error_removed), e)
        } catch (e: PlayException.NoFilesAvailable) {
            throw IOException(stringer.get(R.string.play_error_no_files), e)
        } catch (e: PlayException.NotFound) {
            throw IOException(stringer.get(R.string.play_error_removed), e)
        } catch (e: PlayException) {
            throw IOException(e.message ?: stringer.get(R.string.play_error_no_files), e)
        }
    }
}

internal suspend fun resolvePlayFilesWithRetry(
    maxAttempts: Int = 3,
    baseDelayMs: Long = 1_500L,
    delayFn: suspend (Long) -> Unit = { delay(it) },
    onInvalidateSession: () -> Unit = {},
    fetch: suspend () -> List<AppFile>
): List<AppFile> {
    var lastError: Throwable? = null
    for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
        try {
            val files = fetch()
            if (hasValidPlayDownloadUrls(files)) {
                return files
            }
            lastError = PlayException.NoFilesAvailable(-1)
            if (attempt < maxAttempts) {
                onInvalidateSession()
                delayFn(baseDelayMs * attempt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlayException.Unauthorized) {
            lastError = e
            if (attempt < maxAttempts) {
                onInvalidateSession()
            } else {
                throw e
            }
        } catch (e: PlayException.RateLimited) {
            lastError = e
            if (attempt < maxAttempts) {
                if (attempt == maxAttempts - 1) {
                    onInvalidateSession()
                }
                delayFn(baseDelayMs * attempt)
            } else {
                throw e
            }
        } catch (e: PlayException.ServerError) {
            lastError = e
            if (attempt < maxAttempts) {
                delayFn(baseDelayMs * attempt)
            } else {
                throw e
            }
        } catch (e: PlayException.AppNotSupported) {
            throw e
        } catch (e: PlayException.AppNotPurchased) {
            throw e
        } catch (e: PlayException.AppRemoved) {
            throw e
        } catch (e: PlayException.NotFound) {
            throw e
        } catch (e: PlayException.NoFilesAvailable) {
            throw e
        }
    }
    throw lastError ?: PlayException.NoFilesAvailable(-1)
}

internal fun hasValidPlayDownloadUrls(files: List<AppFile>): Boolean =
    files.isNotEmpty() && files.all { it.url.trim().startsWith("https://", ignoreCase = true) }

fun App.toAppUpdate(
    getInstallFiles: suspend (App) -> List<AppFile>,
    oldVersion: String = "",
    oldVersionCode: Long = 0L
) = AppUpdate(
    displayName,
    packageName,
    versionName,
    oldVersion,
    versionCode,
    oldVersionCode,
    PlaySource,
    Uri.parse(iconArtwork.url),
    Link.Play { getInstallFiles(this) },
    whatsNew = changes,
    releaseType = when {
        earlyAccess || testingProgram?.isSubscribed == true -> ReleaseType.Beta
        else -> ReleaseType.from(versionName)
    },
    isPaid = !isFree,
    sourceUrl = "https://play.google.com/store/apps/details?id=$packageName",
    releaseUrl = "https://play.google.com/store/apps/details?id=$packageName"
)
