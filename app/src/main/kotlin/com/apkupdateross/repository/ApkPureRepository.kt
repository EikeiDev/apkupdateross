package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.data.apkpure.AppInfoForUpdate
import com.apkupdateross.data.apkpure.AppUpdateResponse
import com.apkupdateross.data.apkpure.DeviceHeader
import com.apkupdateross.data.apkpure.GetAppUpdate
import com.apkupdateross.data.apkpure.toAppUpdate
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.data.ui.getSignature
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.ApkPureService
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class ApkPureRepository(
    gson: Gson,
    private val service: ApkPureService,
    private val prefs: Prefs
) {

    private val header = gson.toJson(DeviceHeader())

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val info = apps.map { AppInfoForUpdate(it.packageName, it.versionCode) }
        val r = service.getAppUpdate(header, GetAppUpdate(info))
        val updates = r.app_update_response
            .filter { filterSignature(it.sign, apps.getSignature(it.package_name)) }
            .filter { filterAlpha(it) }
            .filter { filterBeta(it) }
            .map { it.toAppUpdate(apps.getApp(it.package_name)) }
        emit(updates)
    }.catch {
        Log.e("ApkPureRepository", it.message, it)
        emit(emptyList())
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
            .filter { filterAlpha(it) }
            .filter { filterBeta(it) }
            .map { it.toAppUpdate(null) }
        emit(Result.success(updates))
    }.catch {
        Log.e("ApkPureRepository", it.message, it)
        emit(Result.failure(it))
    }

    private fun filterAlpha(update: AppUpdateResponse) = when {
        prefs.ignoreAlpha.get() && update.version_name.contains("alpha", true) -> false
        else -> true
    }

    private fun filterBeta(update: AppUpdateResponse) = when {
        prefs.ignoreBeta.get() && update.version_name.contains("beta", true) -> false
        else -> true
    }

    private fun filterSignature(signatures: List<String>, signature: String) = when {
        signatures.isEmpty() -> true
        signatures.contains(signature) -> true
        else -> false
    }

}
