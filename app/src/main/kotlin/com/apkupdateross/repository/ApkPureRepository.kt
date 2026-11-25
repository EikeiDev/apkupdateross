package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.data.apkpure.AppInfoForUpdate
import com.apkupdateross.data.apkpure.AppUpdateResponse
import com.apkupdateross.data.apkpure.DeviceHeader
import com.apkupdateross.data.apkpure.GetAppUpdate
import com.apkupdateross.data.apkpure.toAppUpdate
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.ApkPureService
import com.google.gson.Gson
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class ApkPureRepository(
    gson: Gson,
    private val service: ApkPureService,
    private val prefs: Prefs
) {

    private val header = gson.toJson(DeviceHeader())

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val allowDowngrade = prefs.installMode.get() > 0
        val appsByPackage = apps.associateBy { it.packageName }
        val info = apps.map { app ->
            val requestCode = if (allowDowngrade && app.versionCode > Int.MAX_VALUE.toLong()) 1L else app.versionCode
            AppInfoForUpdate(app.packageName, requestCode)
        }
        val r = service.getAppUpdate(header, GetAppUpdate(info))
        val updates = r.app_update_response
            .mapNotNull { item ->
                val installed = appsByPackage[item.package_name] ?: return@mapNotNull null
                if (!filterSignature(item.sign, installed.signature)) return@mapNotNull null
                if (!filterRelease(item)) return@mapNotNull null
                val isNewer = when {
                    item.version_code > installed.versionCode -> true
                    allowDowngrade && installed.versionCode > Int.MAX_VALUE.toLong() ->
                        runCatching { Version(item.version_name) > Version(installed.version) }.getOrDefault(false)
                    else -> false
                }
                if (!isNewer) return@mapNotNull null
                item.toAppUpdate(installed)
            }
        emit(updates)
    }

    suspend fun search(text: String) = flow {
        val response = service.search(header, text)
        val info = response.data.data.mapNotNull { d ->
            d.data.firstOrNull()?.takeIf { !it.ad }?.app_info?.let {
                AppInfoForUpdate(it.package_name, 0L, false)
            }
        }
        val r = service.getAppUpdate(header, GetAppUpdate(info))
        val updates = r.app_update_response
            .filter(::filterRelease)
            .map { it.toAppUpdate(null) }
        emit(Result.success(updates))
    }.catch {
        Log.e("ApkPureRepository", it.message, it)
        emit(Result.failure(it))
    }

    private fun filterRelease(update: AppUpdateResponse): Boolean =
        ReleaseType.from(update.version_name, update.label).isAllowed(
            ignoreAlpha = prefs.ignoreAlpha.get(),
            ignoreBeta = prefs.ignoreBeta.get(),
            ignorePreRelease = prefs.ignorePreRelease.get()
        )

    private fun filterSignature(signatures: List<String>, signature: String) = when {
        signatures.isEmpty() -> true
        signatures.contains(signature) -> true
        else -> false
    }

}
