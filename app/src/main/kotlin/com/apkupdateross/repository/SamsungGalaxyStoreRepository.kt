package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.SamsungGalaxyStoreSource
import com.apkupdateross.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SamsungGalaxyStoreRepository(
    client: OkHttpClient,
    private val prefs: Prefs
) {
    private val apiClient = client.newBuilder()
        .cache(null)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun updates(apps: List<AppInstalled>) = flow {
		val profile = deviceProfile()
        val limiter = Semaphore(UPDATE_CONCURRENCY)
        val updates = coroutineScope {
            apps.map { app ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
						runCatching { checkUpdate(app, profile) }
                            .onFailure { Log.w(TAG, "Could not check ${app.packageName}", it) }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        emit(updates)
    }.catch {
        Log.e(TAG, "Error looking for updates", it)
        emit(emptyList())
    }

    suspend fun search(text: String) = flow {
        val query = text.trim()
        if (query.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

		val profile = deviceProfile()
		val searchItems = withContext(Dispatchers.IO) { searchItems(query, profile) }
        val limiter = Semaphore(SEARCH_CONCURRENCY)
        val results = coroutineScope {
            searchItems.take(MAX_SEARCH_RESULTS).map { item ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
						runCatching { loadSearchResult(item, profile) }
                            .onFailure { Log.w(TAG, "Could not load ${item.packageName}", it) }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        emit(Result.success(results.distinctBy { it.packageName.lowercase(Locale.ROOT) }))
    }.catch {
        Log.e(TAG, "Error searching", it)
        emit(Result.failure(it))
    }

    private fun checkUpdate(app: AppInstalled, profile: SamsungGalaxyStoreProfile): AppUpdate? {
        val release = loadRelease(app.packageName, profile) ?: return null
        if (!release.packageName.equals(app.packageName, ignoreCase = true)) return null
        if (!isSamsungGalaxyStoreUpdate(release.versionCode, app.versionCode)) return null
        val releaseType = ReleaseType.from(release.version, release.name)
        if (!shouldInclude(releaseType)) return null
        return release.toAppUpdate(
            installed = app,
            name = release.name.ifBlank { app.name },
            iconUrl = "",
            isPaid = false,
            releaseType = releaseType,
            profile = profile
        )
    }

    private fun loadSearchResult(
        item: SamsungGalaxyStoreSearchItem,
        profile: SamsungGalaxyStoreProfile
    ): AppUpdate? {
        val release = loadRelease(item.packageName, profile) ?: return null
        if (!release.packageName.equals(item.packageName, ignoreCase = true)) return null
        val name = item.name.ifBlank { release.name }.ifBlank { item.packageName }
        val releaseType = ReleaseType.from(release.version, name)
        if (!shouldInclude(releaseType)) return null
        return release.toAppUpdate(
            installed = null,
            name = name,
            iconUrl = item.iconUrl,
            isPaid = item.isPaid,
            releaseType = releaseType,
            profile = profile
        )
    }

    private fun SamsungGalaxyStoreRelease.toAppUpdate(
        installed: AppInstalled?,
        name: String,
        iconUrl: String,
        isPaid: Boolean,
        releaseType: ReleaseType,
        profile: SamsungGalaxyStoreProfile
    ): AppUpdate {
        val pageUrl = "$STORE_DETAIL_URL$packageName"
        val icon = iconUrl.takeIf(String::isNotBlank)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: installed?.iconUri
            ?: Uri.EMPTY
        return AppUpdate(
            name = name,
            packageName = packageName,
            version = version,
            oldVersion = installed?.version ?: "?",
            versionCode = versionCode,
            oldVersionCode = installed?.versionCode ?: 0L,
            source = SamsungGalaxyStoreSource,
            iconUri = icon,
            link = Link.Url(
                link = downloadUrl,
                size = size,
                expectedPackageName = packageName,
                resolveUrl = {
                    withContext(Dispatchers.IO) {
						loadRelease(packageName, profile)?.downloadUrl
                            ?: throw IOException("Galaxy Store download is unavailable")
                    }
                }
            ),
            sourceUrl = pageUrl,
            releaseUrl = pageUrl,
            whatsNew = "",
            releaseType = releaseType,
            isPaid = isPaid
        )
    }

	private fun searchItems(
		query: String,
		profile: SamsungGalaxyStoreProfile
	): List<SamsungGalaxyStoreSearchItem> {
        val identity = randomIdentity()
        val parameters = linkedMapOf(
            "keyword" to query,
            "imgWidth" to "135",
            "imgHeight" to "135",
            "alignOrder" to "bestMatch",
            "startNum" to "1",
            "endNum" to MAX_SEARCH_RESULTS.toString(),
            "contentType" to "all",
            "storeContentType" to "Apps",
            "status" to "0",
            "qlDomainCode" to "sa",
            "qlDeviceType" to "phone",
            "qlInputMethod" to "",
            "imei" to identity,
            "stduk" to identity,
            "extuk" to identity,
            "runestoneYn" to "N"
        )
        val body = buildSamsungOdsEnvelope(
            method = SEARCH_METHOD,
            requestId = SEARCH_REQUEST_ID,
            parameters = parameters,
            identity = identity,
			device = profile.device,
			language = profile.language
        )
        val request = Request.Builder()
			.url("${profile.odsEndpoint}?reqId=$SEARCH_REQUEST_ID&ot=01&ct=B")
            .header("Accept", "image/webp")
            .post(body.toRequestBody(XML_MEDIA_TYPE))
            .build()
        val raw = execute(request)
        return parseSamsungGalaxyStoreSearch(raw, SEARCH_REQUEST_ID)
    }

	private fun loadRelease(
		packageName: String,
		profile: SamsungGalaxyStoreProfile
	): SamsungGalaxyStoreRelease? {
        if (!SAMSUNG_PACKAGE_REGEX.matches(packageName)) return null
		val device = profile.device
        val url = STUB_DOWNLOAD_URL.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("appId", packageName)
            .addQueryParameter("deviceId", device.model)
            .addQueryParameter("mcc", device.mcc)
            .addQueryParameter("mnc", device.mnc)
            .addQueryParameter("csc", device.csc)
            .addQueryParameter("sdkVer", device.sdk.toString())
            .addQueryParameter("pd", "0")
            .addQueryParameter("systemId", SYSTEM_ID)
            .addQueryParameter("callerId", GALAXY_STORE_PACKAGE)
            .addQueryParameter("abiType", if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64" else "32")
            .addQueryParameter("extuk", STUB_IDENTITY)
            .build()
        val release = parseSamsungGalaxyStoreStub(execute(Request.Builder().url(url).get().build()))
            ?: return null
        if (!release.packageName.equals(packageName, ignoreCase = true)) return null
        if (!isTrustedSamsungDownloadUrl(release.downloadUrl)) {
            throw IOException("Galaxy Store returned an untrusted download URL")
        }
        return release
    }

    private fun execute(request: Request): String = apiClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Galaxy Store HTTP ${response.code}")
        val raw = response.body?.string().orEmpty()
        if (raw.isBlank() || raw.length > MAX_XML_LENGTH) {
            throw IOException("Galaxy Store returned an invalid response")
        }
        raw
    }

	private fun deviceProfile(): SamsungGalaxyStoreProfile {
        val actualSamsungModel = Build.MODEL.orEmpty().trim().takeIf {
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                SAMSUNG_MODEL_REGEX.matches(it)
        }
		return buildSamsungGalaxyStoreProfile(
			region = prefs.samsungGalaxyStoreRegion.get(),
			sdk = Build.VERSION.SDK_INT,
			actualSamsungModel = actualSamsungModel,
			abi32 = Build.SUPPORTED_32_BIT_ABIS.joinToString(":").ifBlank { "armeabi-v7a:armeabi" },
			abi64 = Build.SUPPORTED_64_BIT_ABIS.joinToString(":").ifBlank { "arm64-v8a" }
        )
    }

    private fun shouldInclude(type: ReleaseType): Boolean = when (type) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private fun randomIdentity(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "SamsungStoreRepository"
        const val STORE_DETAIL_URL = "https://galaxystore.samsung.com/detail/"
        const val STUB_DOWNLOAD_URL = "https://vas.samsungapps.com/stub/stubDownload.as"
        const val GALAXY_STORE_PACKAGE = "com.sec.android.app.samsungapps"
        const val SEARCH_METHOD = "searchProductListEx2Notc"
        const val SEARCH_REQUEST_ID = "2040"
        const val SYSTEM_ID = "1608665720954"
        const val STUB_IDENTITY = "0191d6627f38685f"
        const val MAX_SEARCH_RESULTS = 20
        const val UPDATE_CONCURRENCY = 4
        const val SEARCH_CONCURRENCY = 4
        const val REQUEST_TIMEOUT_SECONDS = 40L
        const val MAX_XML_LENGTH = 2_000_000
        val XML_MEDIA_TYPE = "text/plain; charset=UTF-8".toMediaType()
        val SAMSUNG_PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        val SAMSUNG_MODEL_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{2,31}$")
    }
}

internal data class SamsungGalaxyStoreDevice(
    val model: String,
    val sdk: Int,
    val csc: String,
    val mcc: String,
    val mnc: String,
    val abi32: String,
    val abi64: String
)

internal data class SamsungGalaxyStoreProfile(
	val device: SamsungGalaxyStoreDevice,
	val odsEndpoint: String,
	val language: String
)

internal fun buildSamsungGalaxyStoreProfile(
	region: String,
	sdk: Int,
	actualSamsungModel: String?,
	abi32: String,
	abi64: String
): SamsungGalaxyStoreProfile {
	val china = region.trim().equals("CN", ignoreCase = true)
	return SamsungGalaxyStoreProfile(
		device = SamsungGalaxyStoreDevice(
			model = if (china) "SM-S9480" else actualSamsungModel ?: "SM-S948B",
			sdk = sdk,
			csc = if (china) "CHC" else "DBT",
			mcc = if (china) "460" else "425",
			mnc = if (china) "00" else "01",
			abi32 = abi32,
			abi64 = abi64
		),
		odsEndpoint = if (china) {
			"https://cn-ms.galaxyappstore.com/ods.as"
		} else {
			"https://us-odc.samsungapps.com/ods.as"
		},
		language = if (china) "zh_CN" else "en_US"
	)
}

internal data class SamsungGalaxyStoreRelease(
    val packageName: String,
    val name: String,
    val productId: String,
    val version: String,
    val versionCode: Long,
    val size: Long,
    val downloadUrl: String
)

internal data class SamsungGalaxyStoreSearchItem(
    val packageName: String,
    val name: String,
    val author: String,
    val iconUrl: String,
    val version: String,
    val versionCode: Long,
    val size: Long,
    val isPaid: Boolean
)

internal fun isSamsungGalaxyStoreUpdate(remoteVersionCode: Long, installedVersionCode: Long): Boolean =
    remoteVersionCode > 0L && remoteVersionCode > installedVersionCode

internal fun isTrustedSamsungDownloadUrl(raw: String): Boolean {
    val url = raw.toHttpUrlOrNull() ?: return false
    if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
    return listOf("samsungapps.com", "galaxyappstore.com").any { domain ->
        url.host == domain || url.host.endsWith(".$domain")
    }
}

internal fun parseSamsungGalaxyStoreStub(raw: String): SamsungGalaxyStoreRelease? {
    val root = parseSamsungXmlRoot(raw, "result")
    val fields = root.directValueMap(useTagName = true)
    if (fields["resultCode"] != "1") return null
    val packageName = fields["appId"].orEmpty()
    val productId = fields["productId"].orEmpty()
    val version = fields["versionName"].orEmpty()
    val versionCode = fields["versionCode"]?.toLongOrNull() ?: return null
    val size = fields["contentSize"]?.toLongOrNull() ?: return null
    val downloadUrl = fields["downloadURI"].orEmpty()
    if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(packageName) ||
        !Regex("^\\d{1,30}$").matches(productId) ||
        version.isBlank() || versionCode <= 0L || size <= 0L ||
        !isTrustedSamsungDownloadUrl(downloadUrl)
    ) {
        throw IOException("Galaxy Store returned malformed APK metadata")
    }
    return SamsungGalaxyStoreRelease(
        packageName = packageName,
        name = fields["productName"].orEmpty().ifBlank { packageName },
        productId = productId,
        version = version,
        versionCode = versionCode,
        size = size,
        downloadUrl = downloadUrl
    )
}

internal fun parseSamsungGalaxyStoreSearch(
    raw: String,
    requestId: String = "2040"
): List<SamsungGalaxyStoreSearchItem> {
    val root = parseSamsungXmlRoot(raw, "SamsungProtocol")
    val responses = root.children().filter { it.tagName() == "response" }
    if (responses.size != 1) throw IOException("Galaxy Store returned an ambiguous response")
    val response = responses.single()
    val errorInfo = response.children().singleOrNull { it.tagName() == "errorInfo" }
        ?: throw IOException("Galaxy Store response has no status")
    val error = errorInfo.children().singleOrNull { it.tagName() == "errorString" }
        ?: throw IOException("Galaxy Store response has no error code")
    if (response.attr("id") != requestId || response.attr("returnCode") != "0" ||
        error.attr("errorCode") != "0"
    ) {
        throw IOException("Galaxy Store rejected the search request")
    }

    return response.children()
        .asSequence()
        .filter { it.tagName() == "list" }
        .mapNotNull { list ->
            val fields = list.directValueMap(useTagName = false)
            val packageName = fields["GUID"].orEmpty()
            val versionCode = fields["versionCode"]?.toLongOrNull() ?: return@mapNotNull null
            val size = fields["realContentSize"]?.toLongOrNull() ?: 0L
            if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(packageName) ||
                versionCode <= 0L ||
                fields["playStoreProductYN"].equals("Y", ignoreCase = true) ||
                fields["installable"].equals("N", ignoreCase = true) ||
                fields["installableYN"].equals("N", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            SamsungGalaxyStoreSearchItem(
                packageName = packageName,
                name = fields["productName"].orEmpty().ifBlank { packageName },
                author = fields["sellerName"].orEmpty().ifBlank { "Galaxy Store" },
                iconUrl = fields["productImgUrl"].orEmpty(),
                version = fields["version"].orEmpty(),
                versionCode = versionCode,
                size = size,
                isPaid = fields["price"]?.toDoubleOrNull()?.let { it > 0.0 } == true
            )
        }
        .distinctBy { it.packageName.lowercase(Locale.ROOT) }
        .toList()
}

internal fun buildSamsungOdsEnvelope(
    method: String,
    requestId: String,
    parameters: Map<String, String>,
    identity: String,
    device: SamsungGalaxyStoreDevice,
	language: String = "en_US",
    nowMillis: Long = System.currentTimeMillis()
): String {
    val now = Date(nowMillis)
    fun format(pattern: String): String = SimpleDateFormat(pattern, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("GMT+08:00")
    }.format(now)
    val hour = format("yyyyMMddHH")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$identity${hour}GalaxyApps".toByteArray())
        .joinToString("") { "%02x".format(it) }
    val transaction = format("dd") + digest.take(7)
    val session = transaction + format("yyyyMMddHHmm")
    val paramsXml = parameters.entries.joinToString("") { (key, value) ->
        "<param name=\"${xmlEscape(key)}\">${xmlEscape(value)}</param>"
    }
    return """<?xml version="1.0" encoding="UTF-8"?>""" +
        "<SamsungProtocol networkType=\"0\" version2=\"0\" lang=\"$language\" " +
        "openApiVersion=\"${device.sdk}\" deviceModel=\"${xmlEscape(device.model)}\" " +
        "deviceMakerName=\"samsung\" deviceMakerType=\"0\" mcc=\"${device.mcc}\" " +
        "mnc=\"${device.mnc}\" csc=\"${device.csc}\" odcVersion=\"4.6.11.4\" " +
        "storeFilter=\"themeDeviceModel=${xmlEscape(device.model)}_TM\" supportFeature=\"\" " +
        "version=\"7.9\" filter=\"1\" odcType=\"01\" storeMode=\"0\" cacheVersion=\"1\" " +
        "systemId=\"${System.currentTimeMillis()}\" sessionId=\"$session\" logId=\"$identity\" " +
        "deviceFeature=\"locale=$language||abi32=${xmlEscape(device.abi32)}||abi64=${xmlEscape(device.abi64)}\" " +
        "userMode=\"0\" asaaMode=\"0\"><request name=\"${xmlEscape(method)}\" id=\"$requestId\" " +
        "numParam=\"${parameters.size}\" transactionId=\"$transaction\">$paramsXml</request></SamsungProtocol>"
}

private fun parseSamsungXmlRoot(raw: String, expectedTag: String): Element {
    if (raw.isBlank() || raw.length > 2_000_000) throw IOException("Invalid Galaxy Store XML")
    val document = Jsoup.parse(raw, "", Parser.xmlParser())
    if (document.childNodes().any { it is DocumentType }) throw IOException("Galaxy Store XML contains a doctype")
    val roots = document.children().filter { it.tagName() == expectedTag }
    if (roots.size != 1 || document.children().size != 1) throw IOException("Unexpected Galaxy Store XML")
    return roots.single()
}

private fun Element.directValueMap(useTagName: Boolean): Map<String, String> {
    val result = linkedMapOf<String, String>()
    for (child in children()) {
        if (!useTagName && child.tagName() != "value") continue
        val key = if (useTagName) child.tagName() else child.attr("name")
        if (key.isBlank() || result.containsKey(key)) throw IOException("Duplicate Galaxy Store XML field")
        result[key] = child.text().trim()
    }
    return result
}

private fun xmlEscape(value: String): String = buildString(value.length) {
    value.forEach { char ->
        append(
            when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> char
            }
        )
    }
}
