package com.apkupdateross.repository

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.BuildConfig
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.UptodownSource
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.AbiMatcher
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class UptodownRepository(
    private val client: OkHttpClient,
    private val prefs: Prefs,
    private val context: Context
) {
    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenExpiresAt = 0L
    @Volatile private var registeredIdentifier: String? = null
    private val authLock = Any()
    private val registrationLock = Any()
    private val apiClient = client.newBuilder()
        .apply { networkInterceptors().clear() }
        .cache(null)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    suspend fun updates(apps: List<AppInstalled>) = flow {
        withContext(Dispatchers.IO) { ensureDeviceRegistered() }
        val limiter = Semaphore(UPDATE_CONCURRENCY)
        val updates = coroutineScope {
            apps.map { app ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        runCatching { checkUpdate(app) }
                            .onFailure { Log.w(TAG, "Could not check ${app.packageName}", it) }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e(TAG, "Error looking for updates", it)
    }

    suspend fun search(text: String) = flow {
        val query = text.trim()
        if (query.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val results = withContext(Dispatchers.IO) {
            ensureDeviceRegistered()
            searchItems(query)
        }
        val limiter = Semaphore(SEARCH_CONCURRENCY)
        val updates = coroutineScope {
            results.take(MAX_SEARCH_RESULTS).map { item ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        runCatching { loadUpdate(item, null) }
                            .onFailure { Log.w(TAG, "Could not load ${item.packageName}", it) }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        emit(Result.success(updates))
    }.catch {
        emit(Result.failure(it))
        Log.e(TAG, "Error searching", it)
    }

    private fun checkUpdate(app: AppInstalled): AppUpdate? {
        val appId = resolveAppId(app.packageName) ?: return null
        val metadata = loadMetadata(appId)
        val packageName = metadata?.packageName.orEmpty().ifBlank { app.packageName }
        if (!packageName.equals(app.packageName, ignoreCase = true)) return null
        val selected = selectLatestUptodownVersion(
            loadVersions(appId), Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT
        ) ?: return null
        if (!selected.isNewerThan(app)) return null
        val releaseType = ReleaseType.from(selected.version, metadata?.name ?: app.name)
        if (!shouldInclude(releaseType)) return null
        return selected.toAppUpdate(
            item = UptodownSearchItem(
                appId, app.packageName, metadata?.name ?: app.name,
                metadata?.author.orEmpty(), metadata?.description.orEmpty(), metadata?.iconUrl.orEmpty()
            ),
            metadata = metadata,
            installed = app,
            releaseType = releaseType
        )
    }

    private fun loadUpdate(item: UptodownSearchItem, installed: AppInstalled?): AppUpdate? {
        if (!PACKAGE_REGEX.matches(item.packageName)) return null
        val metadata = loadMetadata(item.appId)
        val actualPackage = metadata?.packageName.orEmpty().ifBlank { item.packageName }
        if (!actualPackage.equals(item.packageName, ignoreCase = true)) return null
        val selected = selectLatestUptodownVersion(
            loadVersions(item.appId), Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT
        ) ?: return null
        val name = metadata?.name.orEmpty().ifBlank { item.name }
        return selected.toAppUpdate(
            item = item.copy(name = name, packageName = actualPackage),
            metadata = metadata,
            installed = installed,
            releaseType = ReleaseType.from(selected.version, name)
        )
    }

    private fun UptodownRemoteVersion.toAppUpdate(
        item: UptodownSearchItem,
        metadata: UptodownAppMetadata?,
        installed: AppInstalled?,
        releaseType: ReleaseType
    ): AppUpdate {
        val name = metadata?.name.orEmpty().ifBlank { item.name.ifBlank { item.packageName } }
        val iconUrl = metadata?.iconUrl.orEmpty().ifBlank { item.iconUrl }
        val sourceUrl = metadata?.sourceUrl.orEmpty()
        val whatsNew = metadata?.whatsNew.orEmpty()
            .ifBlank { metadata?.description.orEmpty() }
            .ifBlank { item.description }
        return AppUpdate(
            name = name,
            packageName = item.packageName,
            version = version,
            oldVersion = installed?.version ?: "?",
            versionCode = versionCode,
            oldVersionCode = installed?.versionCode ?: 0L,
            source = UptodownSource,
            iconUri = iconUrl.toUriOrEmpty().takeUnless { it == Uri.EMPTY }
                ?: installed?.iconUri ?: Uri.EMPTY,
            link = Link.Url(
                link = sourceUrl.ifBlank { UPTODOWN_HOME },
                size = size,
                expectedPackageName = item.packageName,
                sha256 = sha256,
                resolveUrl = {
                    withContext(Dispatchers.IO) { loadDownloadUrl(item.appId, fileId) }
                }
            ),
            sourceUrl = sourceUrl,
            releaseUrl = sourceUrl,
            whatsNew = whatsNew,
            releaseType = releaseType
        )
    }

    private fun searchItems(query: String): List<UptodownSearchItem> {
        val identifier = deviceIdentifier()
        val url = "$API_BASE/v1/app/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addCommonParameters(identifier)
            .build()
        return parseUptodownSearchResults(requestJson(url.toString()))
    }

    private fun resolveAppId(packageName: String): String? {
        val root = try {
            requestJson("$EAPI_BASE/apps/byPackagename/$packageName")
        } catch (error: UptodownApiException) {
            if (error.errorCode == APP_NOT_FOUND_ERROR) return null
            throw error
        }
        return root.objectOrNull("data")?.stringOrNull("appID", "id")
    }

    private fun loadMetadata(appId: String): UptodownAppMetadata? {
        val identifier = deviceIdentifier()
        val url = "$EAPI_BASE/v3/apps/$appId/device/$identifier".toHttpUrl().newBuilder()
            .addCommonParameters(identifier)
            .build()
        val data = requestJson(url.toString()).objectOrNull("data") ?: return null
        return UptodownAppMetadata(
            packageName = data.stringOrNull("packagename", "packageName") ?: return null,
            name = data.stringOrNull("name").orEmpty(),
            author = data.stringOrNull("author", "authorName").orEmpty(),
            description = data.stringOrNull("shortDescription", "description").orEmpty(),
            whatsNew = data.stringOrNull("newFeatures").orEmpty(),
            iconUrl = data.stringOrNull("icon", "iconURL").orEmpty(),
            sourceUrl = data.stringOrNull("urlShare").orEmpty()
        )
    }

    private fun loadVersions(appId: String): List<UptodownRemoteVersion> {
        val identifier = deviceIdentifier()
        val url = "$EAPI_BASE/v3/app/$appId/device/$identifier/compatible/versions"
            .toHttpUrl().newBuilder()
            .addQueryParameter("page[limit]", MAX_VERSION_RESULTS.toString())
            .addQueryParameter("page[offset]", "0")
            .addCommonParameters(identifier)
            .build()
        val root = try {
            requestJson(url.toString())
        } catch (error: UptodownApiException) {
            if (error.errorCode in NO_VERSIONS_ERRORS) return emptyList()
            throw error
        }
        return parseUptodownVersions(root)
    }

    private fun loadDownloadUrl(appId: String, fileId: String): String {
        val identifier = deviceIdentifier()
        val url = "$EAPI_BASE/apps/$appId/file/$fileId/downloadUrl".toHttpUrl().newBuilder()
            .addQueryParameter("update", "0")
            .addCommonParameters(identifier)
            .build()
        val root = requestJson(url.toString())
        val downloadUrl = root.stringOrNull("downloadURL")
            ?: root.objectOrNull("data")?.stringOrNull("downloadURL")
            ?: throw IOException("Uptodown response has no download URL")
        Log.d(TAG, "Resolved download host=${downloadUrl.toHttpUrl().host}")
        return downloadUrl
    }

    private fun ensureDeviceRegistered() {
        val identifier = deviceIdentifier()
        if (registeredIdentifier == identifier) return
        synchronized(registrationLock) {
            if (registeredIdentifier == identifier) return
            val response = requestJson(
                "$TRACKER_BASE/v2/tracker/device",
                method = "POST",
                body = FormBody.Builder().add("device", buildDeviceJson(identifier).toString()).build(),
                retryAfterDeviceRegistration = false
            )
            if (response.intOrNull("success") != 1) {
                throw IOException(response.stringOrNull("errorMsg") ?: "Uptodown registration failed")
            }
            registeredIdentifier = identifier
        }
    }

    private fun buildDeviceJson(identifier: String): JsonObject {
        val metrics = context.resources.displayMetrics
        val memoryInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memoryInfo)
        return JsonObject().apply {
            addProperty("identifier", identifier)
            add("userID", JsonNull.INSTANCE)
            addProperty("country", countryCode())
            addProperty("language", languageCode())
            addProperty("brand", Build.BRAND.orEmpty())
            addProperty("model", Build.MODEL.orEmpty())
            addProperty("manufacturer", Build.MANUFACTURER.orEmpty())
            addProperty("sdk", Build.VERSION.SDK_INT)
            addProperty("displayWidth", metrics.widthPixels)
            addProperty("displayHeight", metrics.heightPixels)
            addProperty("displayDensity", metrics.density)
            addProperty("density", densityQualifier(metrics.densityDpi))
            addProperty("ram", memoryInfo.totalMem)
            addProperty("operador", "")
            add("supportedABIs", Build.SUPPORTED_ABIS.toJsonArray())
            add("supported32BitsABIs", Build.SUPPORTED_32_BIT_ABIS.toJsonArray())
            add("supported64BitsABIs", Build.SUPPORTED_64_BIT_ABIS.toJsonArray())
            addProperty("appVersion", OFFICIAL_APP_VERSION)
            addProperty("rooted", 0)
            addProperty("isTV", if (isTelevision) 1 else 0)
        }
    }

    private fun requestJson(
        url: String,
        method: String = "GET",
        body: RequestBody? = null,
        retryAfterUnauthorized: Boolean = true,
        retryAfterDeviceRegistration: Boolean = true
    ): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Accept", "application/json")
            .header("Accept-Language", languageCode())
            .header("Client-Version", CLIENT_VERSION)
            .header("Identificador", IDENTIFIER_HEADER)
            .header("Identificador-Version", CLIENT_VERSION)
            .header("Authorization", "Bearer ${token()}")
            .method(method, body)
            .build()
        return apiClient.newCall(request).execute().use { response ->
            if (response.code == 401 && retryAfterUnauthorized) {
                invalidateToken()
                return requestJson(url, method, body, false, retryAfterDeviceRegistration)
            }
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorJson = runCatching { parseJsonObject(responseBody) }.getOrNull()
                if (
                    errorJson?.intOrNull("errorCode") == DEVICE_NOT_FOUND_ERROR &&
                    retryAfterDeviceRegistration
                ) {
                    synchronized(registrationLock) { registeredIdentifier = null }
                    ensureDeviceRegistered()
                    return requestJson(url, method, body, retryAfterUnauthorized, false)
                }
                throw UptodownApiException(
                    statusCode = response.code,
                    errorCode = errorJson?.intOrNull("errorCode"),
                    detail = errorJson?.stringOrNull("errorMsg")
                        ?: responseBody.take(MAX_ERROR_LENGTH)
                )
            }
            parseJsonObject(responseBody)
        }
    }

    private fun token(): String {
        val now = System.currentTimeMillis()
        accessToken?.takeIf { now < accessTokenExpiresAt }?.let { return it }
        synchronized(authLock) {
            val lockedNow = System.currentTimeMillis()
            accessToken?.takeIf { lockedNow < accessTokenExpiresAt }?.let { return it }
            val timestamp = System.currentTimeMillis() / 1000L
            val body = FormBody.Builder()
                .add("hmac", timestamp.hmacSha256(API_AUTH_SECRET))
                .add("unixtime", timestamp.toString())
                .add("clientID", AUTH_CLIENT_ID)
                .add("lang", languageCode())
                .add("id_plataforma", PLATFORM_ID)
                .add("identifier", deviceIdentifier())
                .build()
            val request = Request.Builder()
                .url("$API_BASE/auth/token")
                .header("User-Agent", userAgent())
                .header("Accept", "application/json")
                .header("Client-Version", CLIENT_VERSION)
                .post(body)
                .build()
            val token = apiClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(
                        "Uptodown auth HTTP ${response.code} (${response.protocol}): " +
                            responseBody.take(MAX_ERROR_LENGTH)
                    )
                }
                parseJsonObject(responseBody).stringOrNull("token")
                    ?: throw IOException("Uptodown authentication returned no token")
            }
            accessToken = token
            accessTokenExpiresAt = lockedNow + TOKEN_CACHE_MILLIS
            return token
        }
    }

    private fun invalidateToken() = synchronized(authLock) {
        accessToken = null
        accessTokenExpiresAt = 0L
    }

    private fun deviceIdentifier(): String {
        prefs.uptodownDeviceIdentifier.get().takeIf(DEVICE_ID_REGEX::matches)?.let { return it }
        synchronized(registrationLock) {
            prefs.uptodownDeviceIdentifier.get().takeIf(DEVICE_ID_REGEX::matches)?.let { return it }
            val bytes = ByteArray(8).also(SecureRandom()::nextBytes)
            val generated = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            prefs.uptodownDeviceIdentifier.put(generated)
            return generated
        }
    }

    private fun shouldInclude(type: ReleaseType): Boolean = when (type) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private val isTelevision: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    private fun languageCode() = Locale.getDefault().language.takeIf(String::isNotBlank) ?: "en"
    private fun countryCode() = Locale.getDefault().country.takeIf(String::isNotBlank)
        ?.uppercase(Locale.ROOT) ?: "US"

    private fun okhttp3.HttpUrl.Builder.addCommonParameters(identifier: String) = apply {
        addQueryParameter("lang", languageCode())
        addQueryParameter("id_plataforma", PLATFORM_ID)
        addQueryParameter("identifier", identifier)
        addQueryParameter("countryIsoCode", countryCode())
    }

    private fun userAgent() =
        "APKUpdater OSS/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    companion object {
        private const val TAG = "UptodownRepository"
        private const val UPTODOWN_HOME = "https://www.uptodown.com"
        private const val API_BASE = "https://api.uptodown.app"
        private const val EAPI_BASE = "https://www.uptodown.app/eapi"
        private const val TRACKER_BASE = "https://t.uptodown.app/eapi"
        private const val CLIENT_VERSION = "745"
        private const val OFFICIAL_APP_VERSION = "Uptodown App Store v.7.45 (745)"
        private const val IDENTIFIER_HEADER = "Uptodown_Android"
        private const val PLATFORM_ID = "13"
        private const val AUTH_CLIENT_ID = "8"
        private const val API_AUTH_SECRET = "MDGMXUMdvHJBG/vjdFgmqX6LUdy7ecfwvYNd0gyfOCs="
        private const val TOKEN_CACHE_MILLIS = 25L * 60L * 1000L
        private const val UPDATE_CONCURRENCY = 3
        private const val SEARCH_CONCURRENCY = 4
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_VERSION_RESULTS = 40
        private const val MAX_ERROR_LENGTH = 300
        private const val APP_NOT_FOUND_ERROR = -29
        private const val DEVICE_NOT_FOUND_ERROR = -75
        private val NO_VERSIONS_ERRORS = setOf(-84, -41)
        private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val DEVICE_ID_REGEX = Regex("^[a-f0-9]{16}$")
    }
}

private class UptodownApiException(
    val statusCode: Int,
    val errorCode: Int?,
    detail: String
) : IOException(
    "Uptodown HTTP $statusCode" +
        (errorCode?.let { " ($it)" } ?: "") +
        detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
)

internal data class UptodownSearchItem(
    val appId: String,
    val packageName: String,
    val name: String,
    val author: String,
    val description: String,
    val iconUrl: String
)

internal data class UptodownRemoteVersion(
    val fileId: String,
    val version: String,
    val versionCode: Long,
    val size: Long,
    val minSdk: Int?,
    val arches: List<String>,
    val fileType: String,
    val sha256: String?
) {
    fun isNewerThan(app: AppInstalled): Boolean {
        val comparison = runCatching { Version(version).compareTo(Version(app.version)) }.getOrNull()
        return when {
            comparison == null -> versionCode > app.versionCode
            comparison > 0 -> true
            comparison < 0 -> false
            else -> versionCode > app.versionCode
        }
    }
}

private data class UptodownAppMetadata(
    val packageName: String,
    val name: String,
    val author: String,
    val description: String,
    val whatsNew: String,
    val iconUrl: String,
    val sourceUrl: String
)

internal fun parseUptodownSearchResults(root: JsonObject): List<UptodownSearchItem> =
    root.arrayOrNull("items")?.mapNotNull { element ->
        val item = element.asObjectOrNull() ?: return@mapNotNull null
        val appId = item.stringOrNull("id", "appID") ?: return@mapNotNull null
        val packageName = item.stringOrNull("packagename", "packageName") ?: return@mapNotNull null
        if (!PACKAGE_NAME_REGEX.matches(packageName)) return@mapNotNull null
        UptodownSearchItem(
            appId, packageName,
            item.stringOrNull("name").orEmpty().ifBlank { packageName },
            item.stringOrNull("authorName", "author").orEmpty(),
            item.stringOrNull("description", "shortDescription").orEmpty(),
            item.stringOrNull("iconURL", "icon").orEmpty()
        )
    }.orEmpty()

internal fun parseUptodownVersions(root: JsonObject): List<UptodownRemoteVersion> =
    root.arrayOrNull("data")?.mapNotNull { element ->
        val item = element.asObjectOrNull() ?: return@mapNotNull null
        val fileId = item.stringOrNull("fileID", "fileId", "fileid") ?: return@mapNotNull null
        val version = item.stringOrNull("version") ?: return@mapNotNull null
        val versionCode = item.longOrNull("versionCode") ?: return@mapNotNull null
        val arches = buildList {
            item.stringOrNull("abi")?.let(::add)
            item.arrayOrNull("abis")?.forEach { it.stringValueOrNull()?.let(::add) }
        }.filter(String::isNotBlank).distinct()
        UptodownRemoteVersion(
            fileId = fileId,
            version = version,
            versionCode = versionCode,
            size = item.longOrNull("sizeInBytes", "size") ?: 0L,
            minSdk = item.intOrNull("sdkVersion")
                ?: parseUptodownMinSdk(item.stringOrNull("minSDKVersion").orEmpty()),
            arches = arches,
            fileType = item.stringOrNull("fileType", "filetype").orEmpty().lowercase(Locale.ROOT),
            sha256 = item.stringOrNull("sha256", "SHA256")?.takeIf(SHA256_REGEX::matches)
        )
    }.orEmpty()

internal fun selectLatestUptodownVersion(
    versions: List<UptodownRemoteVersion>,
    supportedAbis: List<String>,
    sdkInt: Int
): UptodownRemoteVersion? {
    val compatible = versions
        .filter { it.minSdk == null || it.minSdk <= sdkInt }
        .filter { AbiMatcher.isCompatible(it.arches, supportedAbis) }
    val latest = compatible.maxWithOrNull(Comparator(::compareUptodownVersions)) ?: return null
    val releaseVariants = compatible.filter {
        it.version == latest.version && it.versionCode == latest.versionCode
    }
    val pool = releaseVariants.filter { it.fileType == "apk" }.ifEmpty { releaseVariants }
    return AbiMatcher.selectCompatible(
        pool, supportedAbis,
        nameSelector = { it.arches.joinToString(" ") },
        sizeSelector = { it.size }
    ) ?: pool.maxByOrNull { it.size }
}

private fun compareUptodownVersions(a: UptodownRemoteVersion, b: UptodownRemoteVersion): Int {
    val names = runCatching { Version(a.version).compareTo(Version(b.version)) }.getOrNull()
    return if (names != null && names != 0) names else a.versionCode.compareTo(b.versionCode)
}

internal fun parseUptodownMinSdk(raw: String): Int? {
    val match = Regex("(?i)android\\s*(?:\\+\\s*)?(\\d+)(?:\\.(\\d+))?\\s*(L)?").find(raw)
        ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: 0
    val isL = match.groupValues[3].equals("L", true)
    return when (major) {
        4 -> when (minor) { 0 -> 14; 1 -> 16; 2 -> 17; 3 -> 18; else -> 19 }
        5 -> if (minor >= 1) 22 else 21
        6 -> 23
        7 -> if (minor >= 1) 25 else 24
        8 -> if (minor >= 1) 27 else 26
        9 -> 28
        10 -> 29
        11 -> 30
        12 -> if (isL || minor >= 1) 32 else 31
        13 -> 33
        14 -> 34
        15 -> 35
        16 -> 36
        else -> null
    }
}

private fun Long.hmacSha256(secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(toString().toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun parseJsonObject(raw: String): JsonObject = runCatching {
    JsonParser.parseString(raw).asJsonObject
}.getOrElse { throw IOException("Invalid Uptodown JSON response", it) }

private fun JsonObject.objectOrNull(name: String) = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.arrayOrNull(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
private fun JsonElement.asObjectOrNull() = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonElement.stringValueOrNull() = runCatching { takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.stringOrNull(vararg names: String) = names.firstNotNullOfOrNull {
    get(it)?.stringValueOrNull()?.trim()?.takeIf(String::isNotBlank)
}
private fun JsonObject.longOrNull(vararg names: String) = names.firstNotNullOfOrNull {
    get(it)?.stringValueOrNull()?.toLongOrNull()
}
private fun JsonObject.intOrNull(vararg names: String) = names.firstNotNullOfOrNull {
    get(it)?.stringValueOrNull()?.toIntOrNull()
}
private fun Array<String>.toJsonArray() = JsonArray().also { array -> forEach(array::add) }
private fun String.toUriOrEmpty() = takeIf(String::isNotBlank)?.let(Uri::parse) ?: Uri.EMPTY

private fun densityQualifier(dpi: Int) = when {
    dpi <= 120 -> "ldpi"
    dpi <= 160 -> "mdpi"
    dpi <= 213 -> "tvdpi"
    dpi <= 240 -> "hdpi"
    dpi <= 320 -> "xhdpi"
    dpi <= 480 -> "xxhdpi"
    else -> "xxxhdpi"
}

private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
private val SHA256_REGEX = Regex("^[A-Fa-f0-9]{64}$")
