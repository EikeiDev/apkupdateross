package com.apkupdateross.repository

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.apkmirror.AppExistsRequest
import com.apkupdateross.data.apkmirror.AppExistsResponseApk
import com.apkupdateross.data.apkmirror.AppExistsResponseData
import com.apkupdateross.data.apkmirror.toAppUpdate
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.data.ui.getPackageNames
import com.apkupdateross.data.ui.getSignature
import com.apkupdateross.data.ui.getVersionCode
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.ApkMirrorService
import com.apkupdateross.util.combine
import com.apkupdateross.util.orFalse
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

private const val USER_AGENT = "APKUpdater-v3.0.3"

class ApkMirrorRepository(
    private val service: ApkMirrorService,
    private val prefs: Prefs,
    packageManager: PackageManager
) {

    private val arch = when {
        Build.SUPPORTED_ABIS.contains("x86") -> "x86"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm"
        else -> "arm"
    }

    private val api = Build.VERSION.SDK_INT

    suspend fun updates(apps: List<AppInstalled>) = flow {
        apps.chunked(100)
            .map { appExists(it.getPackageNames()) }
            .combine { all -> emit(parseUpdates(all.flatMap { it }, apps)) }
            .collect()
    }

    suspend fun search(text: String) = flow {
        val baseUrl = "https://www.apkmirror.com"
        val searchQuery = "/?post_type=app_release&searchtype=app&s="
        val doc = withContext(Dispatchers.IO) {
            Jsoup
                .connect("$baseUrl$searchQuery$text")
                .userAgent(USER_AGENT)
                .referrer(baseUrl)
                .timeout(15000)
                .get()
        }
        val row = doc.select("div.appRow")
        val aLinks = row.select("a.byDeveloper")
        val titles = row.select("h5.appRowTitle")
        val images = row.select("img")
        
        val count = minOf(aLinks.size, titles.size, images.size)
        if (count <= 1 || aLinks.isEmpty() || titles.isEmpty() || images.isEmpty()) {
             emit(Result.success(emptyList()))
             return@flow
        }

        val result = (1 until count).map { i ->
            val detailsLink = titles[i].selectFirst("a")?.attr("href") ?: ""
            val pseudoPackage = if (detailsLink.isNotBlank()) {
                detailsLink.trim('/').replace('/', '.')
            } else {
                aLinks[i].text() + "_" + i
            }
            AppUpdate(
                name = titles[i].attr("title").ifBlank { titles[i].text() },
                link = Link.Empty,
                iconUri = Uri.parse("$baseUrl${images[i].attr("src")}".replace("=32", "=128")),
                version = "",
                oldVersion = "",
                versionCode = 0L,
                oldVersionCode = 0L,
                source = ApkMirrorSource,
                packageName = pseudoPackage,
                sourceUrl = if (detailsLink.startsWith("http")) detailsLink else "$baseUrl$detailsLink"
            )
        }
        emit(Result.success(result))
    }.catch {
        emit(Result.failure(it))
        Log.e("ApkMirrorRepository", "Error searching.", it)
    }

    private fun appExists(apps: List<String>) = flow {
        emit(service.appExists(AppExistsRequest(apps, buildIgnoreList())).data)
    }.catch {
        emit(emptyList())
        Log.e("ApkMirrorRepository", "Error getting updates.", it)
    }

    private fun parseUpdates(updates: List<AppExistsResponseData>, apps: List<AppInstalled>)
    = updates
        .filter { it.exists == true }
        .mapNotNull { data ->
            data.apks
                .asSequence()
                .filter { filterSignature(it, apps.getSignature(data.pname))}
                .filter { filterArch(it) }
                .filter { it.versionCode > apps.getVersionCode(data.pname) }
                .filter { filterMinApi(it) }
                .filter { filterAndroidTv(it) }
                .filter { filterWearOS(it) }
                .maxByOrNull { it.versionCode }
                ?.toAppUpdate(apps.getApp(data.pname)!!, data.release, data.app.link)
        }

    private fun filterSignature(apk: AppExistsResponseApk, signature: String?) = when {
        apk.signaturesSha1.isNullOrEmpty() -> true
        apk.signaturesSha1.contains(signature) -> true
        else -> false
    }

    private fun filterArch(app: AppExistsResponseApk) = when {
        app.arches.isEmpty() -> true
        app.arches.contains("universal") || app.arches.contains("noarch") -> true
        app.arches.find { a -> Build.SUPPORTED_ABIS.contains(a) } != null -> true
        app.arches.find { a -> a.contains(arch) } != null -> true
        else -> false
    }

    private fun filterAndroidTv(apk: AppExistsResponseApk): Boolean {
        return (apk.capabilities?.contains("leanback_standalone").orFalse()
                || apk.capabilities?.contains("leanback").orFalse())
    }

    private fun filterWearOS(apk: AppExistsResponseApk): Boolean {
        // For the moment filter out all standalone Wear OS apps
        if (apk.capabilities?.contains("wear_standalone").orFalse()) {
            return false
        }
        return true
    }

    private fun filterMinApi(apk: AppExistsResponseApk) = runCatching {
        when {
            apk.minapi.toInt() > api -> false
            else -> true
        }
    }.getOrDefault(true)

    private fun buildIgnoreList() = mutableListOf<String>().apply {
        if (prefs.ignoreAlpha.get()) add("alpha")
        if (prefs.ignoreBeta.get()) add("beta")
    }

}
