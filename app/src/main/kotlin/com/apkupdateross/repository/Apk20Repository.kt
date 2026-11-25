package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.ui.Apk20Source
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.AbiMatcher
import com.apkupdateross.util.StoreNotFoundCache
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class Apk20Repository(
    client: OkHttpClient,
    private val prefs: Prefs,
    private val notFoundCache: StoreNotFoundCache
) {
    private val apiClient = client.newBuilder()
        .cache(null)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val redeemClient = apiClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val candidates = apps.filterNot { notFoundCache.isNotFound(StoreNotFoundCache.STORE_APK20, it.packageName) }
        val allowDowngrade = prefs.installMode.get() > 0
        val limiter = Semaphore(UPDATE_CONCURRENCY)
        val updates = coroutineScope {
            candidates.map { app ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        runCatching { checkUpdate(app, allowDowngrade) }
                            .onFailure { Log.w(TAG, "Could not check ${app.packageName}", it) }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        emit(updates)
    }

    suspend fun search(text: String) = flow {
        val query = text.trim()
        if (query.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val items = withContext(Dispatchers.IO) { searchItems(query) }
        val limiter = Semaphore(SEARCH_CONCURRENCY)
        val results = coroutineScope {
            items.take(MAX_SEARCH_RESULTS).map { item ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        runCatching { loadSearchResult(item) }
                            .onFailure { Log.w(TAG, "Could not load ${item.packageName}", it) }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        emit(Result.success(results))
    }.catch {
        Log.e(TAG, "Error searching", it)
        emit(Result.failure(it))
    }

    private fun checkUpdate(installed: AppInstalled, allowDowngrade: Boolean): AppUpdate? {
        val details = loadApp(installed.packageName) ?: run {
            notFoundCache.markNotFound(StoreNotFoundCache.STORE_APK20, installed.packageName)
            return null
        }
        notFoundCache.clearFound(StoreNotFoundCache.STORE_APK20, installed.packageName)
        if (!details.packageName.equals(installed.packageName, ignoreCase = true)) return null
        val selected = selectRelease(details.versions) { it.isNewerThan(installed, allowDowngrade) } ?: return null
        val releaseType = ReleaseType.from(selected.version.version, details.name)
        if (!shouldInclude(releaseType)) return null
        return details.toAppUpdate(selected, installed, releaseType)
    }

    private fun loadSearchResult(item: Apk20SearchItem): AppUpdate? {
        val details = loadApp(item.packageName) ?: return null
        if (!details.packageName.equals(item.packageName, ignoreCase = true)) return null
        val selected = selectRelease(details.versions) { true } ?: return null
        return details.copy(
            name = details.name.ifBlank { item.name },
            author = details.author.ifBlank { item.author },
            description = details.description.ifBlank { item.description },
            iconUrl = details.iconUrl.ifBlank { item.iconUrl }
        ).toAppUpdate(selected, null, ReleaseType.from(selected.version.version, details.name))
    }

    private fun selectRelease(
        versions: List<Apk20Version>,
        include: (Apk20Version) -> Boolean
    ): Apk20Release? {
        val candidates = versions
            .filter(include)
            .distinctBy { it.versionCode }
            .sortedWith(APK20_VERSION_COMPARATOR.reversed())
        for (version in candidates.take(MAX_VERSION_ATTEMPTS)) {
            val variants = loadVariants(version.packageName, version.versionCode)
            val selected = selectApk20Variant(
                variants,
                Build.SUPPORTED_ABIS.toList(),
                Build.VERSION.SDK_INT
            ) ?: continue
            return Apk20Release(version, selected)
        }
        return null
    }

    private fun Apk20AppDetails.toAppUpdate(
        release: Apk20Release,
        installed: AppInstalled?,
        releaseType: ReleaseType
    ): AppUpdate {
        val version = release.version
        val variant = release.variant
        val packagePage = "$WEB_BASE/search#q=${Uri.encode(packageName)}"
        val placeholder = "$WEB_BASE/download/$packageName-${version.versionCode}.xapk"
        return AppUpdate(
            name = name.ifBlank { packageName },
            packageName = packageName,
            version = version.version,
            oldVersion = installed?.version ?: "?",
            versionCode = version.versionCode,
            oldVersionCode = installed?.versionCode ?: 0L,
            source = Apk20Source,
            iconUri = iconUrl.takeIf(String::isNotBlank)?.let(Uri::parse)
                ?: installed?.iconUri ?: Uri.EMPTY,
            link = Link.Url(
                link = placeholder,
                size = variant.fileSize,
                expectedPackageName = packageName,
                resolveUrl = {
                    resolveDownloadUrl(packageName, version.versionCode, variant.variantId)
                }
            ),
            sourceUrl = packagePage,
            releaseUrl = packagePage,
            whatsNew = version.whatsNew.ifBlank { description },
            releaseType = releaseType
        )
    }

    private fun searchItems(query: String): List<Apk20SearchItem> {
        val body = JsonObject().apply {
            addProperty("query", query)
            addProperty("page", 1)
            addProperty("size", MAX_SEARCH_RESULTS)
        }
        val root = requestJson(
            Request.Builder()
                .url("$API_BASE/api/database/search")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        ).asObjectOrNull() ?: throw IOException("APK20 search returned invalid JSON")
        return parseApk20SearchResults(root)
    }

    private fun loadApp(packageName: String): Apk20AppDetails? {
        if (!PACKAGE_REGEX.matches(packageName)) return null
        val request = Request.Builder()
            .url("$API_BASE/api/database/apk/$packageName")
            .get()
            .build()
        return try {
            parseApk20AppDetails(requestJson(request))
        } catch (error: Apk20HttpException) {
            if (error.statusCode == 404) null else throw error
        }
    }

    private fun loadVariants(packageName: String, versionCode: Long): List<Apk20Variant> {
        val request = Request.Builder()
            .url("$API_BASE/api/database/apk/$packageName/$versionCode/variants")
            .get()
            .build()
        return try {
            parseApk20Variants(requestJson(request))
        } catch (error: Apk20HttpException) {
            if (error.statusCode == 404) emptyList() else throw error
        }
    }

    private suspend fun resolveDownloadUrl(
        packageName: String,
        versionCode: Long,
        variantId: String
    ): String = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            addProperty("packageName", packageName)
            addProperty("versionCode", versionCode.toString())
            addProperty("variant", variantId)
            // Raw XAPK is the requested app. The APK format can be APK20's installer wrapper.
            addProperty("format", "xapk")
            addProperty("async", false)
        }
        val tokenResponse = executeJson(
            Request.Builder()
                .url("$API_BASE/api/token")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            acceptedStatusCodes = setOf(200, 202)
        )
        val token = tokenResponse.body.findString("token")
            ?: tokenResponse.body.findString("data", "token")
            ?: tokenResponse.body.findString("jobId")?.let { pollBuildJob(it) }
            ?: throw IOException("APK20 did not return a download token")
        redeemToken(token)
    }

    private suspend fun pollBuildJob(jobId: String): String {
        repeat(MAX_JOB_POLLS) {
            delay(JOB_POLL_DELAY_MILLIS)
            val url = "$API_BASE/api/token/jobs/$jobId".toHttpUrl().newBuilder()
                .addQueryParameter("format", "xapk")
                .build()
            val root = requestJson(Request.Builder().url(url).get().build())
            root.findString("token")?.let { return it }
            val status = root.findString("status", "state")?.lowercase(Locale.ROOT)
            if (status == "failed") {
                throw IOException(root.findString("reason", "message") ?: "APK20 build failed")
            }
        }
        throw IOException("APK20 download preparation timed out")
    }

    private fun redeemToken(token: String): String {
        val request = Request.Builder()
            .url("$API_BASE/api/download/redeem/${Uri.encode(token)}")
            .get()
            .build()
        return redeemClient.newCall(request).execute().use { response ->
            val location = response.header("Location")
            if (response.code !in 300..399 || location.isNullOrBlank()) {
                throw Apk20HttpException(response.code, response.body?.string().orEmpty())
            }
            val url = location.toHttpUrl()
            if (url.scheme != "https" || !isTrustedDownloadHost(url.host)) {
                throw IOException("APK20 returned an untrusted download URL")
            }
            url.toString()
        }
    }

    private fun requestJson(request: Request): JsonElement = executeJson(request).body

    private fun executeJson(
        request: Request,
        acceptedStatusCodes: Set<Int> = emptySet()
    ): Apk20JsonResponse = apiClient.newCall(
        request.newBuilder()
            .header("Accept", "application/json")
            .build()
    ).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful && response.code !in acceptedStatusCodes) {
            throw Apk20HttpException(response.code, raw.take(MAX_ERROR_LENGTH))
        }
        val body = runCatching { JsonParser.parseString(raw) }
            .getOrElse { throw IOException("APK20 returned invalid JSON", it) }
        Apk20JsonResponse(response.code, body)
    }

    private fun shouldInclude(type: ReleaseType): Boolean = when (type) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    companion object {
        private const val TAG = "Apk20Repository"
        private const val WEB_BASE = "https://www.apk20.com"
        private const val API_BASE = "$WEB_BASE/playstore"
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_VERSION_ATTEMPTS = 5
        private const val UPDATE_CONCURRENCY = 4
        private const val SEARCH_CONCURRENCY = 4
        private const val MAX_JOB_POLLS = 30
        private const val JOB_POLL_DELAY_MILLIS = 2_000L
        private const val MAX_ERROR_LENGTH = 400
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}

private data class Apk20JsonResponse(val statusCode: Int, val body: JsonElement)

private class Apk20HttpException(val statusCode: Int, detail: String) :
    IOException("APK20 HTTP $statusCode${detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}")

internal data class Apk20SearchItem(
    val packageName: String,
    val name: String,
    val author: String,
    val description: String,
    val iconUrl: String
)

internal data class Apk20Version(
    val packageName: String,
    val versionCode: Long,
    val version: String,
    val whatsNew: String
) {
    fun isNewerThan(installed: AppInstalled, allowVersionCodeDowngrade: Boolean = false): Boolean = isApk20Update(
        version,
        versionCode,
        installed.version,
        installed.versionCode,
        allowVersionCodeDowngrade
    )
}

internal fun isApk20Update(
    remoteVersion: String,
    remoteVersionCode: Long,
    installedVersion: String,
    installedVersionCode: Long,
    allowVersionCodeDowngrade: Boolean = false
): Boolean {
    if (remoteVersionCode <= installedVersionCode) {
        if (!allowVersionCodeDowngrade || installedVersionCode <= Int.MAX_VALUE.toLong()) return false
        val nameComparison = runCatching {
            Version(remoteVersion).compareTo(Version(installedVersion))
        }.getOrNull()
        return nameComparison != null && nameComparison > 0
    }
    val nameComparison = runCatching {
        Version(remoteVersion).compareTo(Version(installedVersion))
    }.getOrNull()
    return nameComparison == null || nameComparison >= 0
}

internal data class Apk20Variant(
    val variantId: String,
    val architecture: String,
    val versionCode: Long,
    val version: String,
    val minSdk: Int?,
    val isBundle: Boolean,
    val fileSize: Long
)

internal data class Apk20AppDetails(
    val packageName: String,
    val name: String,
    val author: String,
    val description: String,
    val iconUrl: String,
    val versions: List<Apk20Version>
)

private data class Apk20Release(val version: Apk20Version, val variant: Apk20Variant)

internal fun parseApk20SearchResults(root: JsonObject): List<Apk20SearchItem> =
    root.arrayOrNull("items")?.mapNotNull { element ->
        val item = element.asObjectOrNull() ?: return@mapNotNull null
        val packageName = item.stringOrNull("packageName") ?: return@mapNotNull null
        if (!APK20_PACKAGE_REGEX.matches(packageName)) return@mapNotNull null
        Apk20SearchItem(
            packageName = packageName,
            name = item.stringOrNull("title").orEmpty().ifBlank { packageName },
            author = item.stringOrNull("developerName").orEmpty(),
            description = item.stringOrNull("shortDescription").orEmpty(),
            iconUrl = item.stringOrNull("icon").orEmpty()
        )
    }.orEmpty()

internal fun parseApk20AppDetails(root: JsonElement): Apk20AppDetails {
    val tuple = root.asArrayOrNull() ?: throw IOException("APK20 app response is not a tuple")
    val app = tuple.elementAtOrNull(0)?.asObjectOrNull()
        ?: throw IOException("APK20 app response has no app record")
    val packageName = app.stringOrNull("packageName")
        ?: throw IOException("APK20 app response has no package name")
    if (!APK20_PACKAGE_REGEX.matches(packageName)) throw IOException("APK20 returned invalid package name")
    val versions = buildList {
        app.objectOrNull("lastVersion")?.toApk20Version()?.let(::add)
        tuple.elementAtOrNull(1)?.asArrayOrNull()?.forEach { element ->
            element.asObjectOrNull()?.toApk20Version()?.let(::add)
        }
    }.distinctBy { it.versionCode }
    val icon = app.objectOrNull("media")?.objectOrNull("icon")?.stringOrNull("imageUrl")
        ?: app.arrayOrNull("images")?.firstNotNullOfOrNull { element ->
            element.asObjectOrNull()?.takeIf { it.intOrNull("imageType") == 4 }
                ?.stringOrNull("imageUrl")
        }
    return Apk20AppDetails(
        packageName = packageName,
        name = app.stringOrNull("title").orEmpty().ifBlank { packageName },
        author = app.objectOrNull("developer")?.stringOrNull("name").orEmpty(),
        description = app.stringOrNull("descriptionHtml")?.toPlainText().orEmpty(),
        iconUrl = icon.orEmpty(),
        versions = versions
    )
}

internal fun parseApk20Variants(root: JsonElement): List<Apk20Variant> =
    root.asArrayOrNull()?.mapNotNull { element ->
        val item = element.asObjectOrNull() ?: return@mapNotNull null
        val variantId = item.stringOrNull("variantId") ?: return@mapNotNull null
        val versionCode = item.longOrNull("versionCode") ?: return@mapNotNull null
        Apk20Variant(
            variantId = variantId,
            architecture = item.stringOrNull("architecture").orEmpty(),
            versionCode = versionCode,
            version = item.stringOrNull("versionString").orEmpty(),
            minSdk = item.intOrNull("minSdk"),
            isBundle = item.booleanOrNull("isBundle") ?: false,
            fileSize = item.longOrNull("fileSize") ?: 0L
        )
    }.orEmpty()

internal fun selectApk20Variant(
    variants: List<Apk20Variant>,
    supportedAbis: List<String>,
    sdkInt: Int
): Apk20Variant? {
    val compatible = variants
        .filter { it.minSdk == null || it.minSdk <= sdkInt }
        .filter { variant ->
            val architecture = variant.architecture.trim()
            architecture.isBlank() || AbiMatcher.isCompatible(listOf(architecture), supportedAbis)
        }
    val nonInstaller = compatible.filterNot { it.variantId.equals("installer", ignoreCase = true) }
    val pool = nonInstaller.ifEmpty { compatible }
    return AbiMatcher.selectCompatible(
        pool,
        supportedAbis,
        nameSelector = { it.architecture },
        sizeSelector = { if (it.isBundle) 0L else it.fileSize }
    ) ?: pool.maxWithOrNull(
        compareBy<Apk20Variant> { !it.isBundle }.thenBy { it.fileSize }
    )
}

private val APK20_VERSION_COMPARATOR = Comparator<Apk20Version> { first, second ->
    val names = runCatching {
        Version(first.version).compareTo(Version(second.version))
    }.getOrNull()
    if (names != null && names != 0) names else first.versionCode.compareTo(second.versionCode)
}

private fun JsonObject.toApk20Version(): Apk20Version? {
    val packageName = stringOrNull("packageName") ?: return null
    val versionCode = longOrNull("versionCode") ?: return null
    val version = stringOrNull("versionString") ?: return null
    return Apk20Version(
        packageName,
        versionCode,
        version,
        stringOrNull("recentChangesHtml")?.toPlainText().orEmpty()
    )
}

private fun String.toPlainText(): String = Jsoup.parse(this).text().trim()

private fun isTrustedDownloadHost(host: String): Boolean =
    host == "apk20.com" || host.endsWith(".apk20.com")

private fun JsonElement.findString(vararg path: String): String? {
    var current = this
    path.forEach { key ->
        current = current.asObjectOrNull()?.get(key) ?: return null
    }
    return current.stringValueOrNull()
}

private fun JsonElement.asObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonElement.asArrayOrNull(): JsonArray? =
    takeIf { it.isJsonArray }?.asJsonArray

private fun JsonElement.stringValueOrNull(): String? = runCatching {
    takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}.getOrNull()?.takeIf(String::isNotBlank)

private fun JsonObject.stringOrNull(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> get(key)?.stringValueOrNull() }

private fun JsonObject.longOrNull(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
    get(key)?.let { value -> runCatching { value.asLong }.getOrNull() }
}

private fun JsonObject.intOrNull(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
    get(key)?.let { value -> runCatching { value.asInt }.getOrNull() }
}

private fun JsonObject.booleanOrNull(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
    get(key)?.let { value -> runCatching { value.asBoolean }.getOrNull() }
}

private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.asObjectOrNull()
private fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key)?.asArrayOrNull()

private val APK20_PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
