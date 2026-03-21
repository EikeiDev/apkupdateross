package com.apkupdateross.repository

import android.os.Build
import android.util.Log
import com.apkupdateross.data.fdroid.FdroidApp
import com.apkupdateross.data.fdroid.FdroidData
import com.apkupdateross.data.fdroid.FdroidUpdate
import com.apkupdateross.data.fdroid.toAppUpdate
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.Source
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.data.ui.getVersionCode
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.FdroidService
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.util.jar.JarInputStream


class FdroidRepository(
    private val service: FdroidService,
    private val url: String,
    private val source: Source,
    private val prefs: Prefs
) {
    private val arch = Build.SUPPORTED_ABIS.toSet()
    private val api = Build.VERSION.SDK_INT

    private val cacheMutex = Mutex()
    private var cachedData: FdroidData? = null
    private var cacheTimestamp: Long = 0L
    private val cacheTtlMs = 15 * 60 * 1000L

    fun clearCache() = cacheMutex.tryLock().also { locked ->
        if (locked) try { cachedData = null; cacheTimestamp = 0L } finally { cacheMutex.unlock() }
    }

    private suspend fun getOrFetchData(): FdroidData = cacheMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedData
        if (cached != null && now - cacheTimestamp < cacheTtlMs) return@withLock cached
        val response = service.getJar("${url}index-v1.jar")
        val data = jarToJson(response.byteStream())
        cachedData = data
        cacheTimestamp = now
        data
    }

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val data = getOrFetchData()
        val appNames = apps.map { it.packageName }
        val updates = data.apps
            .asSequence()
            .filter { appNames.contains(it.packageName) }
            .filter { apps.getApp(it.packageName)?.let { app -> filterSignature(app, it) } ?: false }
            .mapNotNull { app -> data.packages[app.packageName]?.firstOrNull()?.let { FdroidUpdate(it, app) } }
            .filter { it.apk.versionCode > apps.getVersionCode(it.app.packageName) }
            .parseUpdates(apps)
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("FdroidRepository", "Error looking for updates.", it)
    }

    suspend fun search(text: String) = flow {
        val data = getOrFetchData()
        val updates = data.apps
            .asSequence()
            .mapNotNull { app -> data.packages[app.packageName]?.firstOrNull()?.let { FdroidUpdate(it, app) } }
            .filter { it.app.name.contains(text, true) || it.app.packageName.contains(text, true) || it.apk.apkName.contains(text, true) }
            .parseUpdates(null)
        emit(Result.success(updates))
    }.catch {
        emit(Result.failure(it))
        Log.e("FdroidRepository", "Error searching.", it)
    }

    private fun Sequence<FdroidUpdate>.parseUpdates(apps: List<AppInstalled>?) = this
        .filter { it.apk.minSdkVersion <= api }
        .filter { filterArch(it) }
        .filter { filterAlpha(it) }
        .filter { filterBeta(it) }
        .map { it.toAppUpdate(apps?.getApp(it.app.packageName), source, url) }
        .toList()

    private fun filterSignature(installed: AppInstalled, update: FdroidApp) = when {
        update.allowedAPKSigningKeys.isEmpty() -> true
        update.allowedAPKSigningKeys.contains(installed.signatureSha256) -> true
        else -> false
    }

    private fun filterAlpha(update: FdroidUpdate) = when {
        prefs.ignoreAlpha.get() && update.apk.versionName.contains("alpha", true) -> false
        else -> true
    }

    private fun filterBeta(update: FdroidUpdate) = when {
        prefs.ignoreBeta.get() && update.apk.versionName.contains("beta", true) -> false
        else -> true
    }

    private fun filterArch(update: FdroidUpdate) = when {
        update.apk.nativecode.isEmpty() -> true
        update.apk.nativecode.intersect(arch).isNotEmpty() -> true
        else -> false
    }

    private fun jarToJson(stream: InputStream): FdroidData {
        val jar = JarInputStream(stream)
        var entry = jar.nextJarEntry
        while (entry != null) {
            if (entry.name == "index-v1.json") {
                val reader = com.google.gson.stream.JsonReader(jar.reader())
                return Gson().fromJson(reader, FdroidData::class.java)
            }
            entry = jar.nextJarEntry
        }
        return FdroidData()
    }

}
