package com.apkupdateross.repository

import android.net.Uri
import android.util.Log
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.HuaweiSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.versionCodeFromTag
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.UUID


class HuaweiRepository(
    client: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs
) {

    private val appInfoClient = client
    private val redirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val identityId = UUID.randomUUID().toString().replace("-", "")
    private var cachedInterfaceCode: String? = null

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val updates = coroutineScope {
            apps.chunked(UPDATE_BATCH_SIZE).flatMap { batch ->
                batch.map { app ->
                    async(Dispatchers.IO) { checkUpdate(app) }
                }.awaitAll().filterNotNull()
            }
        }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("HuaweiRepository", "Error looking for updates.", it)
    }

    suspend fun search(text: String) = flow {
        val exactUpdate = findAppInfo(text)?.let { toSearchUpdate(it) }
        val searchUpdates = searchAppInfos(text).mapNotNull { toSearchUpdate(it) }
        emit(Result.success((listOfNotNull(exactUpdate) + searchUpdates).distinctBy { it.packageName }))
    }.catch {
        emit(Result.failure(it))
        Log.e("HuaweiRepository", "Error searching.", it)
    }

    private fun checkUpdate(app: AppInstalled): AppUpdate? {
        val info = requestAppInfo(packageName = app.packageName) ?: return null
        if (!info.pkgName.equals(app.packageName, ignoreCase = true)) return null
        if (!info.isNewerThan(app)) return null

        val releaseType = ReleaseType.from(info.version.orEmpty(), info.name)
        if (!shouldInclude(releaseType)) return null

        val downloadUrl = resolveDownloadUrl(info) ?: return null
        return info.toAppUpdate(app, downloadUrl, releaseType)
    }

    private fun toSearchUpdate(info: HuaweiAppInfo): AppUpdate? {
        if (info.pkgName.isNullOrBlank()) return null

        val releaseType = ReleaseType.from(info.version.orEmpty(), info.name)
        if (!shouldInclude(releaseType)) return null

        val downloadUrl = resolveDownloadUrl(info)
        return info.toAppUpdate(null, downloadUrl, releaseType)
    }

    private fun findAppInfo(text: String): HuaweiAppInfo? {
        val query = text.trim()
        if (query.isEmpty()) return null

        extractAppId(query)?.let { appId ->
            requestAppInfo(appId = appId)?.let { return it }
        }

        val packageName = extractPackageName(query) ?: return null
        return requestAppInfo(packageName = packageName)
    }

    private fun searchAppInfos(text: String): List<HuaweiAppInfo> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()

        val candidates = searchZones().firstNotNullOfOrNull { zone ->
            val zoneCandidates = requestSearchPage(query, zone = zone)?.searchCandidates().orEmpty()
            zoneCandidates.takeIf { it.isNotEmpty() }?.also { cacheSearchZone(zone) }
        }.orEmpty().take(MAX_SEARCH_DETAILS)

        val infos = candidates.mapNotNull { candidate ->
            requestAppInfo(appId = candidate.appId, packageName = candidate.packageName)
        }.distinctBy { it.pkgName?.lowercase(Locale.ROOT) ?: it.appId.orEmpty() }
        return infos
    }

    private fun HuaweiSearchResponse.searchCandidates(): List<HuaweiSearchCandidate> =
        layoutData.orEmpty()
            .flatMap { it.dataList.orEmpty() }
            .mapNotNull { it.toSearchCandidate() }
            .distinctBy { it.packageName?.lowercase(Locale.ROOT) ?: it.appId.orEmpty() }

    private fun searchZones(): List<String> =
        configuredRegion()?.let { listOf(it) } ?: listOf(
            prefs.huaweiCachedRegion.get(),
            currentZone(),
            languageDefaultZone(),
            "RU",
            "UA",
            "DE",
            "GB"
        )
            .filter { it.isNotBlank() }
            .distinct()

    private fun cacheSearchZone(zone: String) {
        if (configuredRegion() == null && zone.isNotBlank() && prefs.huaweiCachedRegion.get() != zone) {
            prefs.huaweiCachedRegion.put(zone)
        }
    }

    private fun requestSearchPage(
        text: String,
        zone: String,
        retryOnExpiredCode: Boolean = true
    ): HuaweiSearchResponse? {
        val interfaceCode = requestInterfaceCode() ?: run {
            return null
        }
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("method", "internal.getTabDetail")
            .addQueryParameter("serviceType", "20")
            .addQueryParameter("reqPageNum", "1")
            .addQueryParameter("uri", "searchApp|$text")
            .addQueryParameter("maxResults", "25")
            .addQueryParameter("version", "10.0.0")
            .addQueryParameter("locale", currentLocale())
            .addQueryParameter("zone", zone)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://appgallery.huawei.com")
            .header("Referer", "https://appgallery.huawei.com/")
            .header("User-Agent", USER_AGENT)
            .header("Interface-Code", "${interfaceCode}_${System.currentTimeMillis()}")
            .header("Identity-Id", identityId)
            .get()
            .build()

        return appInfoClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 && retryOnExpiredCode) {
                    cachedInterfaceCode = null
                    return requestSearchPage(text, zone, retryOnExpiredCode = false)
                }
                return null
            }

            val raw = response.body?.string().orEmpty()
            val searchResponse = runCatching {
                gson.fromJson(raw, HuaweiSearchResponse::class.java)
            }.getOrNull() ?: return null

            if (searchResponse.rtnCode == EXPIRED_INTERFACE_CODE && retryOnExpiredCode) {
                cachedInterfaceCode = null
                return requestSearchPage(text, zone, retryOnExpiredCode = false)
            }
            if (searchResponse.rtnCode != null && searchResponse.rtnCode != 0) {
                return null
            }
            searchResponse
        }
    }

    private fun requestInterfaceCode(): String? {
        cachedInterfaceCode?.let { return it }

        val payload = mapOf(
            "params" to emptyMap<String, Any>(),
            "locale" to currentLocale(),
            "zone" to preferredRegion()
        )
        val request = Request.Builder()
            .url(INTERFACE_CODE_URL)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Origin", "https://appgallery.huawei.com")
            .header("Referer", "https://appgallery.huawei.com/")
            .header("User-Agent", USER_AGENT)
            .header("Interface-Code", "null_${System.currentTimeMillis()}")
            .header("Identity-Id", identityId)
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return appInfoClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            response.body?.string()
                ?.trim()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?.also { cachedInterfaceCode = it }
        }
    }

    private fun requestAppInfo(
        appId: String? = null,
        packageName: String? = null
    ): HuaweiAppInfo? {
        if (appId.isNullOrBlank() && packageName.isNullOrBlank()) return null

        val payload = mutableMapOf<String, Any>(
            "locale" to currentLocale(),
            "zone" to preferredRegion()
        )
        appId?.takeIf { it.isNotBlank() }?.let { payload["appId"] = it }
        packageName?.takeIf { it.isNotBlank() }?.let { payload["pkgName"] = it }

        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(APP_INFO_URL)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://appgallery.huawei.com")
            .header("Referer", "https://appgallery.huawei.com/")
            .post(body)
            .build()

        return appInfoClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string().orEmpty()
            val info = runCatching { gson.fromJson(raw, HuaweiAppInfo::class.java) }.getOrNull() ?: return null
            if (info.rtnCode != null && info.rtnCode != 0) return null
            if (info.appId.isNullOrBlank() || info.pkgName.isNullOrBlank()) return null
            info
        }
    }

    private fun HuaweiSearchItem.toSearchCandidate(): HuaweiSearchCandidate? {
        val candidateAppId = (appId ?: appid ?: extractAppId(detailId.orEmpty()))
            ?.takeIf { it.isNotBlank() }
        val candidatePackage = (pkgName ?: packageName)
            ?.takeIf { it.isNotBlank() && packageRegex.matches(it) }

        if (candidateAppId.isNullOrBlank() && candidatePackage.isNullOrBlank()) return null
        return HuaweiSearchCandidate(candidateAppId, candidatePackage)
    }

    private fun resolveDownloadUrl(info: HuaweiAppInfo): String? {
        val appId = info.appId?.takeIf { it.isNotBlank() } ?: return null
        val expectedPackage = info.pkgName?.takeIf { it.isNotBlank() } ?: return null
        val request = Request.Builder()
            .url("$APPDL_URL$appId")
            .header("Accept", "application/octet-stream, */*")
            .build()

        return redirectClient.newCall(request).execute().use { response ->
            if (response.code !in listOf(200, 302)) {
                return null
            }
            val location = response.header("Location")?.takeIf { it.isNotBlank() } ?: run {
                return null
            }
            val redirectPackage = extractPackageFromApkUrl(location) ?: run {
                return null
            }
            location.takeIf { redirectPackage.equals(expectedPackage, ignoreCase = true) } ?: run {
                null
            }
        }
    }

    private fun HuaweiAppInfo.toAppUpdate(
        app: AppInstalled?,
        downloadUrl: String?,
        releaseType: ReleaseType
    ): AppUpdate {
        val versionName = version?.takeIf { it.isNotBlank() } ?: versionCode?.toString().orEmpty()
        val remoteVersionCode = versionCode ?: versionName.versionCodeFromTag()
        val packageName = pkgName.orEmpty()
        return AppUpdate(
            name = name?.takeIf { it.isNotBlank() } ?: app?.name ?: packageName,
            packageName = packageName,
            version = versionName,
            oldVersion = app?.version ?: "?",
            versionCode = remoteVersionCode,
            oldVersionCode = app?.versionCode ?: 0L,
            source = HuaweiSource,
            iconUri = icon?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
            link = downloadUrl?.let {
                Link.Url(
                    link = it,
                    size = size ?: 0L,
                    expectedPackageName = packageName,
                    sha256 = sha256?.takeIf { value -> value.isNotBlank() }
                )
            } ?: Link.Empty,
            sourceUrl = "$APP_URL${appId.orEmpty()}",
            releaseUrl = "$APP_URL${appId.orEmpty()}",
            whatsNew = upgradeMsg?.takeIf { it.isNotBlank() } ?: newFeatures.orEmpty(),
            releaseType = releaseType,
            isPaid = isPaid()
        )
    }

    private fun HuaweiAppInfo.isNewerThan(app: AppInstalled): Boolean {
        versionCode?.takeIf { it > 0L }?.let { return it > app.versionCode }
        val remoteVersion = version?.takeIf { it.isNotBlank() } ?: return false
        return runCatching { Version(remoteVersion) > Version(app.version) }.getOrDefault(false)
    }

    private fun HuaweiAppInfo.isPaid(): Boolean {
        if (price != null && price != "0") return true
        if (isPay != null && isPay != "0") return true
        return payInfo?.payApp?.let { it != 0 } == true
    }

    private fun shouldInclude(releaseType: ReleaseType): Boolean = when (releaseType) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private fun extractAppId(text: String): String? =
        appIdRegex.find(text)?.value

    private fun extractPackageName(text: String): String? {
        val queryValue = text.substringAfter("id=", missingDelimiterValue = text)
            .substringBefore("&")
            .substringBefore("#")
            .trim()
        return queryValue.takeIf { packageRegex.matches(it) }
    }

    private fun extractPackageFromApkUrl(url: String): String? {
        val fileName = url.substringBefore("?").substringAfterLast("/")
        if (!fileName.endsWith(".apk", ignoreCase = true)) return null
        val baseName = fileName.removeSuffix(".apk")
        val parts = baseName.split(".")
        if (parts.size < 3) return null
        return parts.dropLast(1).joinToString(".")
            .takeIf { packageRegex.matches(it) }
    }

    private fun currentLocale(): String {
        val locale = Locale.getDefault()
        return listOf(locale.language, locale.country)
            .filter { it.isNotBlank() }
            .joinToString("_")
            .ifBlank { "en_US" }
    }

    private fun currentZone(): String =
        Locale.getDefault().country.takeIf { it.isNotBlank() } ?: languageDefaultZone()

    private fun configuredRegion(): String? =
        prefs.huaweiRegion.get()
            .trim()
            .uppercase(Locale.ROOT)
            .takeIf { it.isNotBlank() && it != REGION_AUTO }

    private fun preferredRegion(): String =
        configuredRegion()
            ?: prefs.huaweiCachedRegion.get().takeIf { it.isNotBlank() }
            ?: currentZone()

    private fun languageDefaultZone(): String =
        when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "be" -> "BY"
            "kk" -> "KZ"
            "ru" -> "RU"
            "uk" -> "UA"
            "en" -> "GB"
            "de" -> "DE"
            else -> "DE"
        }

    private data class HuaweiAppInfo(
        @SerializedName("AG-TraceId") val traceId: String? = null,
        @SerializedName("rtnCode") val rtnCode: Int? = null,
        @SerializedName("rtnDesc") val rtnDesc: String? = null,
        @SerializedName("appId") val appId: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("version") val version: String? = null,
        @SerializedName("versionCode") val versionCode: Long? = null,
        @SerializedName("icon") val icon: String? = null,
        @SerializedName("pkgName") val pkgName: String? = null,
        @SerializedName("sha256") val sha256: String? = null,
        @SerializedName("size") val size: Long? = null,
        @SerializedName("upgradeMsg") val upgradeMsg: String? = null,
        @SerializedName("newFeatures") val newFeatures: String? = null,
        @SerializedName("price") val price: String? = null,
        @SerializedName("isPay") val isPay: String? = null,
        @SerializedName("payInfo") val payInfo: HuaweiPayInfo? = null
    )

    private data class HuaweiPayInfo(
        @SerializedName("payApp") val payApp: Int? = null
    )

    private data class HuaweiSearchResponse(
        @SerializedName("rtnCode") val rtnCode: Int? = null,
        @SerializedName("layoutData") val layoutData: List<HuaweiSearchLayout>? = null
    )

    private data class HuaweiSearchLayout(
        @SerializedName("dataList") val dataList: List<HuaweiSearchItem>? = null
    )

    private data class HuaweiSearchItem(
        @SerializedName("appId") val appId: String? = null,
        @SerializedName("appid") val appid: String? = null,
        @SerializedName("detailId") val detailId: String? = null,
        @SerializedName("package") val packageName: String? = null,
        @SerializedName("pkgName") val pkgName: String? = null
    )

    private data class HuaweiSearchCandidate(
        val appId: String?,
        val packageName: String?
    )

    companion object {
        private const val UPDATE_BATCH_SIZE = 4
        private const val MAX_SEARCH_DETAILS = 8
        private const val EXPIRED_INTERFACE_CODE = 1002
        private const val REGION_AUTO = "AUTO"
        private const val WEB_EDGE_URL = "https://web-dre.hispace.dbankcloud.com/edge"
        private const val APP_INFO_URL = "https://web-dre.hispace.dbankcloud.com/edge/webedge/appinfo"
        private const val SEARCH_URL = "$WEB_EDGE_URL/uowap/index"
        private const val INTERFACE_CODE_URL = "$WEB_EDGE_URL/webedge/getInterfaceCode"
        private const val APPDL_URL = "https://appgallery.cloud.huawei.com/appdl/"
        private const val APP_URL = "https://appgallery.huawei.com/app/"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val appIdRegex = Regex("C\\d{3,}")
        private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}
