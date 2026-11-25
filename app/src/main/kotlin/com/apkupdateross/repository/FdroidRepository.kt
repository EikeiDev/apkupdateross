package com.apkupdateross.repository

import android.os.Build
import android.util.Log
import com.apkupdateross.data.fdroid.FdroidApp
import com.apkupdateross.data.fdroid.FdroidData
import com.apkupdateross.data.fdroid.FdroidUpdate
import com.apkupdateross.data.fdroid.toAppUpdate
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.Source
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.FdroidService
import com.apkupdateross.util.AbiMatcher
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.util.jar.JarInputStream


class FdroidRepository(
    private val service: FdroidService,
    url: String,
    private val source: Source,
    private val prefs: Prefs,
    private val gson: Gson
) {
    private val url = url.trim().trimEnd('/') + "/"
    private val api = Build.VERSION.SDK_INT

    private val cacheMutex = Mutex()
    private var cachedData: FdroidData? = null
    @Volatile
    private var cacheTimestamp: Long = 0L
    private val cacheTtlMs = 15 * 60 * 1000L

    fun clearCache() {
        cacheTimestamp = 0L
        if (cacheMutex.tryLock()) {
            try {
                cachedData = null
            } finally {
                cacheMutex.unlock()
            }
        }
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
        val appsByPackage = apps.associateBy { it.packageName }
        val updates = data.apps
            .asSequence()
            .mapNotNull { fdroidApp ->
                val installed = appsByPackage[fdroidApp.packageName] ?: return@mapNotNull null
                if (!filterSignature(installed, fdroidApp)) return@mapNotNull null
                val latest = data.latestCompatibleUpdate(fdroidApp) ?: return@mapNotNull null
                if (latest.apk.versionCode <= installed.versionCode) return@mapNotNull null
                latest.toAppUpdate(installed, source, url)
            }
            .toList()
        emit(updates)
    }

    suspend fun search(text: String) = flow {
        val data = getOrFetchData()
        val updates = data.apps
            .asSequence()
            .mapNotNull { app -> data.latestCompatibleUpdate(app) }
            .filter { it.app.name.contains(text, true) || it.app.packageName.contains(text, true) || it.apk.apkName.contains(text, true) }
            .map { it.toAppUpdate(null, source, url) }
            .toList()
        emit(Result.success(updates))
    }.catch {
        emit(Result.failure(it))
        Log.e("FdroidRepository", "Error searching.", it)
    }

    private fun FdroidData.latestCompatibleUpdate(app: FdroidApp): FdroidUpdate? =
        packages[app.packageName]
            .orEmpty()
            .asSequence()
            .map { FdroidUpdate(it, app) }
            .filter { it.apk.minSdkVersion <= api }
            .filter(::filterArch)
            .filter(::filterRelease)
            .maxByOrNull { it.apk.versionCode }

    private fun filterSignature(installed: AppInstalled, update: FdroidApp) = when {
        update.allowedAPKSigningKeys.isEmpty() -> true
        update.allowedAPKSigningKeys.contains(installed.signatureSha256) -> true
        else -> false
    }

    private fun filterRelease(update: FdroidUpdate): Boolean =
        ReleaseType.from(update.apk.versionName, update.app.name).isAllowed(
            ignoreAlpha = prefs.ignoreAlpha.get(),
            ignoreBeta = prefs.ignoreBeta.get(),
            ignorePreRelease = prefs.ignorePreRelease.get()
        )

    private fun filterArch(update: FdroidUpdate) = when {
        update.apk.nativecode.isEmpty() -> true
        AbiMatcher.isCompatible(update.apk.nativecode, Build.SUPPORTED_ABIS.toList()) -> true
        else -> false
    }

    private fun jarToJson(stream: InputStream): FdroidData {
        stream.use { input ->
            JarInputStream(input).use { jar ->
                var entry = jar.nextJarEntry
                while (entry != null) {
                    if (entry.name == "index-v1.json") {
                        val reader = JsonReader(jar.reader())
                        return gson.fromJson(reader, FdroidData::class.java)
                    }
                    entry = jar.nextJarEntry
                }
            }
        }
        return FdroidData()
    }

}
