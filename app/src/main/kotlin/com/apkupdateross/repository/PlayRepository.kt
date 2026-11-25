package com.apkupdateross.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.getPackageNames
import com.apkupdateross.data.ui.getVersion
import com.apkupdateross.data.ui.getVersionCode
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.play.NativeDeviceInfoProvider
import com.apkupdateross.util.play.PlayHttpClient
import com.apkupdateross.util.play.PlayLocales
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.google.gson.Gson
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException


class PlayRepository(
    private val context: Context,
    private val playHttpClient: PlayHttpClient,
    private val gson: Gson,
    private val prefs: Prefs
) {
    companion object {
        const val AUTH_URL = "https://auroraoss.com/api/auth"
        const val DEVICE_PROFILE_VERSION = 1
        private const val MAX_PURCHASE_ATTEMPTS = 3
    }

    private val authLock = Any()

    private fun refreshAuth(): AuthData {
        Log.i("PlayRepository", "Refreshing token.")
        val properties = NativeDeviceInfoProvider(context).getNativeDeviceProperties()
        val playResponse = playHttpClient.postAuth(AUTH_URL, gson.toJson(properties).toByteArray())
        if (playResponse.isSuccessful) {
            val authData = gson.fromJson(String(playResponse.responseBytes), AuthData::class.java).withDeviceLocale()
            prefs.playAuthData.put(authData)
            prefs.playProfileVersion.put(DEVICE_PROFILE_VERSION)
            prefs.lastPlayCheck.put(System.currentTimeMillis())
            return authData
        }
        throw IllegalStateException("Auth not successful (HTTP ${playResponse.code}).")
    }

    private fun forceRefreshAuth(): AuthData = synchronized(authLock) {
        refreshAuth()
    }

    private fun auth(): AuthData = synchronized(authLock) {
        val savedData = prefs.playAuthData.get().withDeviceLocale()
        if (savedData.email.isEmpty()) {
            return@synchronized refreshAuth()
        }
        if (prefs.playProfileVersion.get() != DEVICE_PROFILE_VERSION) {
            Log.i("PlayRepository", "Device profile changed, re-authenticating.")
            return@synchronized refreshAuth()
        }
        if (System.currentTimeMillis() - prefs.lastPlayCheck.get() > 60 * 60 * 1_000) {
            // Update check time
            prefs.lastPlayCheck.put(System.currentTimeMillis())
            Log.i("PlayRepository", "Checking token validity.")

            // 1h has passed check if token still works
            val app = runCatching {
                AppDetailsHelper(savedData)
                    .using(playHttpClient)
                    .getAppByPackageName("com.google.android.gm")
            }.getOrElse {
                return@synchronized refreshAuth()
            }

            if (app.packageName.isEmpty()) {
                return@synchronized refreshAuth()
            }
            Log.i("PlayRepository", "Token still valid.")
        }
        savedData.withDeviceLocale()
    }

    private fun isAllowedPlayApp(app: App): Boolean {
        val ignoreAlpha = prefs.ignoreAlpha.get()
        val ignoreBeta = prefs.ignoreBeta.get()
        val ignorePre = prefs.ignorePreRelease.get()
        if (ignoreBeta && (app.earlyAccess || app.testingProgram?.isSubscribed == true)) return false
        return ReleaseType.from(app.versionName.orEmpty()).isAllowed(ignoreAlpha, ignoreBeta, ignorePre)
    }

    private fun AuthData.withDeviceLocale() = apply {
        runCatching { locale = PlayLocales.preferredLocale(context) }
            .onFailure { Log.e("PlayRepository", "Could not set session locale.", it) }
    }

    suspend fun search(text: String) = flow {
        if (text.contains(" ") || !text.contains(".")) {
            // Normal Search
            val authData = auth()
            val updates = SearchHelper(authData)
                .using(playHttpClient)
                .searchResults(text)
                .appList
                .take(10)
                .filter(::isAllowedPlayApp)
                .map { it.toAppUpdate(::getInstallFiles) }
            emit(Result.success(updates))
        } else {
            // Package Name Search
            val authData = auth()
            val app = AppDetailsHelper(authData)
                .using(playHttpClient)
                .getAppByPackageName(text)

            if (app.packageName.isBlank() || !isAllowedPlayApp(app)) {
                emit(Result.success(emptyList()))
            } else {
                val update = app.toAppUpdate(::getInstallFiles)
                emit(Result.success(listOf(update)))
            }
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("PlayRepository", "Error searching for $text.", it)
    }

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val timeoutSeconds = prefs.playTimeoutSec.get().coerceIn(5, 300)
        val timeout = timeoutSeconds * 1000L
        val appsByPkg = apps.associateBy { it.packageName }
        val allowDowngrade = prefs.installMode.get() > 0
        withTimeoutOrNull(timeout) {
            val authData = auth()
            val details = AppDetailsHelper(authData)
                .using(playHttpClient)
                .getAppByPackageName(apps.getPackageNames())

            val updates = details
                .filter {
                    val installed = appsByPkg[it.packageName] ?: return@filter false
                    when {
                        it.versionCode.toLong() > installed.versionCode -> true
                        allowDowngrade && installed.versionCode > Int.MAX_VALUE.toLong() ->
                            runCatching { Version(it.versionName.orEmpty()) > Version(installed.version) }.getOrDefault(false)
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

    private fun getInstallFiles(app: App): List<File> {
        var currentAuth = auth()
        var lastResponseCode = 0
        repeat(MAX_PURCHASE_ATTEMPTS) { attempt ->
            val files = runCatching {
                PurchaseHelper(currentAuth)
                    .using(playHttpClient)
                    .purchase(app.packageName, app.versionCode, app.offerType)
                    .filter { it.type == File.FileType.BASE || it.type == File.FileType.SPLIT }
            }.getOrElse { error ->
                lastResponseCode = playHttpClient.responseCode.value
                Log.w("PlayRepository", "Play purchase failed (attempt ${attempt + 1}, HTTP $lastResponseCode)", error)
                if (attempt + 1 < MAX_PURCHASE_ATTEMPTS) {
                    currentAuth = forceRefreshAuth()
                    return@repeat
                }
                throw error
            }
            lastResponseCode = playHttpClient.responseCode.value
            if (hasValidPlayDownloadUrls(files)) {
                return files
            }
            Log.w(
                "PlayRepository",
                "Play delivery returned invalid/empty download URLs for ${app.packageName} " +
                    "(attempt ${attempt + 1}, HTTP $lastResponseCode, files=${files.size}); refreshing anonymous account"
            )
            if (attempt + 1 < MAX_PURCHASE_ATTEMPTS) {
                currentAuth = forceRefreshAuth()
            }
        }
        if (lastResponseCode == 429) {
            throw IOException("Google Play rate limit (HTTP 429)")
        }
        throw IOException("Google Play did not return download links (HTTP $lastResponseCode)")
    }

}

internal fun hasValidPlayDownloadUrls(files: List<File>): Boolean =
    files.isNotEmpty() && files.all { it.url.trim().startsWith("https://", ignoreCase = true) }

fun App.toAppUpdate(
    getInstallFiles: (App) -> List<File>,
    oldVersion: String = "",
    oldVersionCode: Long = 0L
) = AppUpdate(
    displayName,
    packageName,
    versionName,
    oldVersion,
    versionCode.toLong(),
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
