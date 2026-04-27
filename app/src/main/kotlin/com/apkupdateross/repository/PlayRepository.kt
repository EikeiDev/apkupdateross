package com.apkupdateross.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.getPackageNames
import com.apkupdateross.data.ui.getVersion
import com.apkupdateross.data.ui.getVersionCode
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.play.NativeDeviceInfoProvider
import com.apkupdateross.util.play.PlayHttpClient
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class PlayRepository(
    private val context: Context,
    private val playHttpClient: PlayHttpClient,
    private val gson: Gson,
    private val prefs: Prefs
) {
    companion object {
        const val AUTH_URL = "https://auroraoss.com/api/auth"
    }

    private fun refreshAuth(): AuthData {
        Log.i("PlayRepository", "Refreshing token.")
        val properties = NativeDeviceInfoProvider(context).getNativeDeviceProperties()
        val playResponse = playHttpClient.postAuth(AUTH_URL, gson.toJson(properties).toByteArray())
        if (playResponse.isSuccessful) {
            val authData = gson.fromJson(String(playResponse.responseBytes), AuthData::class.java)
            prefs.playAuthData.put(authData)
            return authData
        }
        throw IllegalStateException("Auth not successful.")
    }

    private fun auth(): AuthData {
        val savedData = prefs.playAuthData.get()
        if (savedData.email.isEmpty()) {
            return refreshAuth()
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
                return refreshAuth()
            }

            if (app.packageName.isEmpty()) {
                return refreshAuth()
            }
            Log.i("PlayRepository", "Token still valid.")
        }
        return savedData
    }

    suspend fun search(text: String) = flow {
        val ignoreAlpha = prefs.ignoreAlpha.get()
        val ignoreBeta = prefs.ignoreBeta.get()
        val ignorePre = prefs.ignorePreRelease.get()

        if (text.contains(" ") || !text.contains(".")) {
            // Normal Search
            val authData = auth()
            val updates = SearchHelper(authData)
                .using(playHttpClient)
                .searchResults(text)
                .appList
                .take(10)
                .filter { app ->
                    val vName = app.versionName ?: ""
                    if (ignoreAlpha && vName.contains("alpha", true)) return@filter false
                    if (ignoreBeta && vName.contains("beta", true)) return@filter false
                    if (ignorePre && vName.contains("pre", true)) return@filter false

                    if (ignoreBeta && app.earlyAccess) return@filter false
                    if (ignoreBeta && app.testingProgram?.isSubscribed == true) return@filter false

                    true
                }
                .map { it.toAppUpdate(::getInstallFiles) }
            emit(Result.success(updates))
        } else {
            // Package Name Search
            val authData = auth()
            val app = AppDetailsHelper(authData)
                .using(playHttpClient)
                .getAppByPackageName(text)
            
            val vName = app.versionName ?: ""
            if ((ignoreAlpha && vName.contains("alpha", true)) ||
                (ignoreBeta && vName.contains("beta", true)) ||
                (ignorePre && vName.contains("pre", true)) ||
                (ignoreBeta && app.earlyAccess) ||
                (ignoreBeta && app.testingProgram?.isSubscribed == true)) {
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
        val authData = auth()
        val details = AppDetailsHelper(authData)
            .using(playHttpClient)
            .getAppByPackageName(apps.getPackageNames())
        val ignoreAlpha = prefs.ignoreAlpha.get()
        val ignoreBeta = prefs.ignoreBeta.get()
        val ignorePre = prefs.ignorePreRelease.get()

        val updates = details
            .filter { it.versionCode > apps.getVersionCode(it.packageName) }
            .filter { app ->
                val vName = app.versionName ?: ""
                if (ignoreAlpha && vName.contains("alpha", true)) return@filter false
                if (ignoreBeta && vName.contains("beta", true)) return@filter false
                if (ignorePre && vName.contains("pre", true)) return@filter false

                if (ignoreBeta && app.earlyAccess) return@filter false
                if (ignoreBeta && app.testingProgram?.isSubscribed == true) return@filter false

                true
            }
            .map {
                it.toAppUpdate(
                    ::getInstallFiles,
                    apps.getVersion(it.packageName),
                    apps.getVersionCode(it.packageName)
                )
            }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("PlayRepository", "Error looking for updates.", it)
    }

    private fun getInstallFiles(app: App) = PurchaseHelper(auth())
        .using(playHttpClient)
        .purchase(app.packageName, app.versionCode, app.offerType)
        .filter { it.type == File.FileType.BASE || it.type == File.FileType.SPLIT }

}

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
    isPaid = !isFree,
    sourceUrl = "https://play.google.com/store/apps/details?id=$packageName",
    releaseUrl = "https://play.google.com/store/apps/details?id=$packageName"
)
