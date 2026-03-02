package com.apkupdateross.repository

import android.os.Build
import android.util.Log
import com.apkupdateross.data.rustore.RuStoreDownloadRequest
import com.apkupdateross.data.rustore.toAppUpdate
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.service.RuStoreService
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class RuStoreRepository(
    private val service: RuStoreService,
    private val prefs: com.apkupdateross.prefs.Prefs
) {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val updates = coroutineScope {
            apps.map { app ->
                async {
                    runCatching {
                        val response = service.getAppDetails(app.packageName)
                        if (response.code == "OK" && response.body != null) {
                            val details = response.body
                            if (Version(details.versionName) > Version(app.version)) {
                                val downloadUrl = getDownloadUrl(details.appId, details.minSdkVersion)
                                details.toAppUpdate(app, downloadUrl)
                            } else null
                        } else null
                    }.onFailure {
                        Log.e("RuStoreRepository", "Error checking update for ${app.packageName}", it)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("RuStoreRepository", "Error looking for updates.", it)
    }

    suspend fun search(text: String) = flow {
        val response = service.searchApps(text)
        if (response.code == "OK" && response.body != null) {
            val updates = coroutineScope {
                response.body.content.map { searchApp ->
                    async {
                        runCatching {
                            val detailsResponse = service.getAppDetails(searchApp.packageName)
                            if (detailsResponse.code == "OK" && detailsResponse.body != null) {
                                val details = detailsResponse.body
                                val downloadUrl = getDownloadUrl(details.appId, details.minSdkVersion)
                                details.toAppUpdate(null, downloadUrl)
                            } else null
                        }.onFailure {
                            Log.e("RuStoreRepository", "Error fetching details for ${searchApp.packageName}", it)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            emit(Result.success(updates))
        } else {
            emit(Result.success(emptyList()))
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("RuStoreRepository", "Error searching.", it)
    }

    private suspend fun getDownloadUrl(appId: Long, minSdkVersion: Int): String {
        return runCatching {
            val request = RuStoreDownloadRequest(
                appId = appId,
                sdkVersion = Build.VERSION.SDK_INT.coerceAtLeast(minSdkVersion),
                supportedAbis = Build.SUPPORTED_ABIS.toList()
            )
            val response = service.getDownloadLink(request)
            if (response.code == "OK" && response.body != null) {
                response.body.downloadUrls.firstOrNull()?.url ?: ""
            } else ""
        }.getOrElse {
            Log.e("RuStoreRepository", "Error getting download link for appId=$appId", it)
            ""
        }
    }

}
