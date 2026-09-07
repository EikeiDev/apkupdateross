package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.ui.ApkComboSource
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.AbiMatcher
import com.apkupdateross.util.versionCodeFromTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap


class ApkComboRepository(
    private val client: OkHttpClient,
    private val prefs: Prefs
) {

    private val appUrlCache = ConcurrentHashMap<String, String>()

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val limiter = Semaphore(UPDATE_CONCURRENCY)
        val updates = coroutineScope {
            apps.map { app ->
                async(Dispatchers.IO) {
                    limiter.withPermit { checkUpdate(app) }
                }
            }.awaitAll().filterNotNull()
        }
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("ApkComboRepository", "Error looking for updates.", it)
    }

    suspend fun search(text: String) = flow {
        val query = text.trim()
        if (query.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val limiter = Semaphore(SEARCH_CONCURRENCY)
        val updates = coroutineScope {
            searchResults(query)
                .take(MAX_SEARCH_RESULTS)
                .map { result ->
                    async(Dispatchers.IO) {
                        limiter.withPermit {
                            loadDetails(result.url, result.packageName, result)?.toAppUpdate(null)
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
        emit(Result.success(updates))
    }.catch {
        emit(Result.failure(it))
        Log.e("ApkComboRepository", "Error searching.", it)
    }

    private fun checkUpdate(app: AppInstalled): AppUpdate? {
        val appUrl = appUrlCache[app.packageName]
            ?: findAppUrl(app.packageName, app.packageName)?.also { appUrlCache[app.packageName] = it }
            ?: return null

        val details = loadDetails(appUrl, app.packageName) ?: return null
        if (!details.packageName.equals(app.packageName, ignoreCase = true)) {
            appUrlCache.remove(app.packageName)
            return null
        }

        val variant = details.preferredVariant() ?: return null
        val remoteVersionCode = details.remoteVersionCode(variant)
        if (!isNewerThan(app, details.version, remoteVersionCode)) return null
        if (!shouldInclude(details.releaseType)) return null

        return details.toAppUpdate(app, variant)
    }

    private fun findAppUrl(query: String, packageName: String? = null): String? {
        val results = searchResults(query)
        val exact = packageName?.let { expected ->
            results.firstOrNull { it.packageName.equals(expected, ignoreCase = true) }
        }
        return exact?.url ?: results.firstOrNull()?.url
    }

    private fun searchResults(text: String): List<ApkComboSearchResult> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment(text.trim())
            .build()
            .toString()

        val doc = requestDocument(url)
        return doc.select("div.content.content-apps a.l_item[href], a.l_item[href]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.packageName.lowercase(Locale.ROOT) }
    }

    private fun loadDetails(
        pageUrl: String,
        fallbackPackageName: String? = null,
        searchResult: ApkComboSearchResult? = null
    ): ApkComboDetails? {
        val doc = requestDocument(pageUrl)
        val canonicalUrl = doc.selectFirst("link[rel=canonical]")
            ?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?: pageUrl
        val normalizedUrl = canonicalUrl.normalizedAppPageUrl()
        val packageName = extractPackageName(normalizedUrl)
            ?: extractPackageNameFromInfo(doc)
            ?: fallbackPackageName?.takeIf { packageRegex.matches(it) }
            ?: return null

        val versionFromTable = doc.infoValue("Version")?.text().orEmpty()
        val version = doc.selectFirst(".app_header .version, div.version")
            ?.text()
            ?.cleanVersionName()
            ?.takeIf { it.isNotBlank() }
            ?: versionFromTable.cleanVersionName().takeIf { it.isNotBlank() }
            ?: return null

        val variants = runCatching { loadVariants(normalizedUrl) }
            .getOrElse {
                Log.w("ApkComboRepository", "Could not load variants for $packageName", it)
                emptyList()
            }

        val versionCode = parseVersionCode(versionFromTable)
            ?: variants.maxOfOrNull { it.versionCode }?.takeIf { it > 0L }
            ?: 0L

        val name = doc.selectFirst(".app_header .app_name h1, div.app_name h1, div.app_name")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: searchResult?.name
            ?: packageName
        val author = doc.selectFirst(".app_header .author, div.author")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: searchResult?.author
            ?: name
        val iconUrl = doc.selectFirst("meta[name=thumbnail]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst(".app_header img")
                ?.bestImageUrl()
            ?: searchResult?.iconUrl.orEmpty()
        val whatsNew = doc.selectFirst(".whatnew, #whats-new, div.whats-new")
            ?.html()
            ?.trim()
            .orEmpty()

        return ApkComboDetails(
            packageName = packageName,
            name = name,
            author = author,
            version = version,
            versionCode = versionCode,
            iconUri = iconUrl.toUriOrEmpty(),
            sourceUrl = normalizedUrl,
            whatsNew = whatsNew,
            variants = variants,
            releaseType = ReleaseType.from(version, name)
        )
    }

    private fun loadVariants(pageUrl: String): List<ApkComboVariant> {
        val downloadUrl = "${pageUrl.trimEnd('/')}/download/apk"
        val doc = requestDocument(downloadUrl, referer = pageUrl)
        val groups = doc.select("#variants-tab div.tree > ul > li, #download-tab div.tree > ul > li")
            .ifEmpty { doc.select("a.variant[href]").map { it.parent() ?: it } }

        return groups.flatMap { group ->
            val arches = parseArches(group.selectFirst("code")?.text().orEmpty())
            group.select("a.variant[href]").mapNotNull { it.toVariant(arches) }
        }.distinctBy { it.url }
    }

    private fun ApkComboDetails.toAppUpdate(
        app: AppInstalled?,
        selectedVariant: ApkComboVariant? = preferredVariant()
    ): AppUpdate? {
        if (selectedVariant == null) return null
        val remoteVersionCode = remoteVersionCode(selectedVariant)
        return AppUpdate(
            name = name,
            packageName = packageName,
            version = version,
            oldVersion = app?.version ?: "?",
            versionCode = remoteVersionCode,
            oldVersionCode = app?.versionCode ?: 0L,
            source = ApkComboSource,
            iconUri = if (iconUri != Uri.EMPTY) iconUri else app?.iconUri ?: Uri.EMPTY,
            link = selectedVariant.toLink(packageName),
            sourceUrl = sourceUrl,
            releaseUrl = sourceUrl,
            whatsNew = whatsNew,
            releaseType = releaseType
        )
    }

    private fun ApkComboDetails.preferredVariant(): ApkComboVariant? {
        val compatible = variants
            .filter { it.minSdk == null || it.minSdk <= Build.VERSION.SDK_INT }
            .filter { it.isPhoneCompatible }
        if (compatible.isEmpty()) return null

        val highestCode = compatible.maxOfOrNull { it.versionCode } ?: 0L
        val latest = if (highestCode > 0L) {
            compatible.filter { it.versionCode == highestCode }
        } else {
            compatible
        }
        val singleApks = latest.filter { it.isSingleApk }
        val pool = singleApks.ifEmpty { latest }

        return AbiMatcher.selectCompatible(
            items = pool,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            nameSelector = { it.matcherText },
            sizeSelector = { it.size.takeIf { size -> size > 0L } ?: it.versionCode }
        ) ?: pool.maxByOrNull { it.size.takeIf { size -> size > 0L } ?: it.versionCode }
    }

    private fun ApkComboDetails.remoteVersionCode(selectedVariant: ApkComboVariant): Long =
        selectedVariant.versionCode.takeIf { it > 0L }
            ?: versionCode.takeIf { it > 0L }
            ?: version.versionCodeFromTag()

    private fun ApkComboVariant.toLink(packageName: String): Link =
        if (isArchive) {
            Link.Xapk(url)
        } else {
            Link.Url(
                link = url,
                size = size,
                expectedPackageName = packageName
            )
        }

    private fun Element.toSearchResult(): ApkComboSearchResult? {
        val url = attr("abs:href").ifBlank { attr("href").toAbsoluteUrl() }
        val packageName = extractPackageName(url) ?: return null
        val name = selectFirst(".name")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: attr("title").removeSuffix(" APK").trim().takeIf { it.isNotBlank() }
            ?: packageName
        val author = selectFirst(".author")
            ?.text()
            ?.substringBefore("·")
            ?.trim()
            .orEmpty()
        val iconUrl = selectFirst("img")?.bestImageUrl().orEmpty()
        return ApkComboSearchResult(
            packageName = packageName,
            name = name,
            author = author,
            iconUrl = iconUrl,
            url = url.normalizedAppPageUrl()
        )
    }

    private fun Element.toVariant(arches: List<String>): ApkComboVariant? {
        val url = attr("abs:href").ifBlank { attr("href").toAbsoluteUrl() }
        if (url.isBlank()) return null

        val effectiveUrl = redirectTarget(url)
        val type = fileType(effectiveUrl, selectFirst(".vtype span")?.text().orEmpty())
            ?: return null
        val versionCode = parseVersionCode(selectFirst(".vercode")?.text().orEmpty()) ?: 0L
        val text = text()
        val size = parseSize(text)
        val minSdk = parseMinSdk(text)
        val name = buildVariantName(arches, versionCode, type)
        return ApkComboVariant(
            name = name,
            url = url,
            versionCode = versionCode,
            size = size,
            arches = arches,
            minSdk = minSdk,
            isPhoneCompatible = !text.contains("Android TV", ignoreCase = true)
                    && !text.contains("Wear OS", ignoreCase = true)
                    && !text.contains("Android Wear", ignoreCase = true),
            fileType = type
        )
    }

    private fun requestDocument(url: String, referer: String = BASE_URL): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Connection", "keep-alive")
            .header("Referer", referer)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APKCombo HTTP ${response.code}: $url")
            }
            Jsoup.parse(response.body?.string().orEmpty(), url)
        }
    }

    private fun isNewerThan(app: AppInstalled, version: String, versionCode: Long): Boolean {
        if (versionCode > 0L) return versionCode > app.versionCode
        return runCatching { Version(version) > Version(app.version) }.getOrDefault(false)
    }

    private fun shouldInclude(releaseType: ReleaseType): Boolean = when (releaseType) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private fun Document.infoValue(label: String): Element? =
        select("div.information-table > .item").firstOrNull { item ->
            item.selectFirst("div.name")
                ?.text()
                ?.trim()
                ?.equals(label, ignoreCase = true) == true
        }?.selectFirst("div.value")

    private fun extractPackageNameFromInfo(doc: Document): String? {
        val value = doc.infoValue("Google Play ID") ?: return null
        val hrefId = value.selectFirst("a[href]")?.attr("href")
            ?.substringAfter("id=", missingDelimiterValue = "")
            ?.substringBefore("&")
            ?.takeIf { packageRegex.matches(it) }
        return hrefId ?: value.text().trim().takeIf { packageRegex.matches(it) }
    }

    private fun extractPackageName(url: String): String? =
        runCatching {
            Uri.parse(url).pathSegments
                .lastOrNull()
                ?.takeIf { packageRegex.matches(it) }
        }.getOrNull()

    private fun Element.bestImageUrl(): String =
        attr("abs:data-src").ifBlank { attr("data-src").toAbsoluteUrl() }
            .ifBlank { attr("abs:src") }
            .ifBlank { attr("src").toAbsoluteUrl() }

    private fun String.toAbsoluteUrl(): String = when {
        isBlank() -> ""
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true) -> this
        startsWith("//") -> "https:$this"
        startsWith("/") -> "$BASE_URL$this"
        else -> "$BASE_URL/$this"
    }

    private fun String.toUriOrEmpty(): Uri =
        takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY

    private fun String.normalizedAppPageUrl(): String =
        substringBefore("/download/")
            .trimEnd('/')
            .let { "$it/" }

    private fun String.cleanVersionName(): String =
        replace(Regex("\\(\\s*\\d+\\s*\\)"), "")
            .trim()

    private fun redirectTarget(url: String): String =
        runCatching { Uri.parse(url).getQueryParameter("u") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: url

    private fun fileType(url: String, rawType: String): String? {
        val type = rawType.uppercase(Locale.ROOT)
        val lowerUrl = url.lowercase(Locale.ROOT)
        return when {
            "XAPK" in type || lowerUrl.contains(".xapk") -> "XAPK"
            "APKS" in type || lowerUrl.contains(".apks") -> "APKS"
            "APK" in type || lowerUrl.contains(".apk") -> "APK"
            else -> null
        }
    }

    private fun parseArches(raw: String): List<String> =
        raw.split(",", " ", "\n", "\t")
            .map { it.trim().removeSuffix(":") }
            .filter { it.isNotBlank() }

    private fun parseVersionCode(raw: String): Long? =
        Regex("\\((\\d+)\\)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: raw.trim().trim('(', ')').takeIf { it.all(Char::isDigit) }?.toLongOrNull()

    private fun parseSize(raw: String): Long {
        val match = sizeRegex.find(raw) ?: return 0L
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val multiplier = when (match.groupValues[2].uppercase(Locale.ROOT)) {
            "GB" -> 1024L * 1024L * 1024L
            "MB" -> 1024L * 1024L
            "KB" -> 1024L
            else -> 1L
        }
        return (value * multiplier).toLong()
    }

    private fun parseMinSdk(raw: String): Int? {
        val androidVersion = androidVersionRegex.find(raw)?.groupValues?.getOrNull(1) ?: return null
        return androidVersionToSdk(androidVersion)
    }

    private fun androidVersionToSdk(version: String): Int? = when (version.androidVersionKey()) {
        "4" -> 14
        "4.1" -> 16
        "4.2" -> 17
        "4.3" -> 18
        "4.4" -> 19
        "5" -> 21
        "5.1" -> 22
        "6" -> 23
        "7" -> 24
        "7.1" -> 25
        "8" -> 26
        "8.1" -> 27
        "9" -> 28
        "10" -> 29
        "11" -> 30
        "12" -> 31
        "12.1" -> 32
        "13" -> 33
        "14" -> 34
        "15" -> 35
        "16" -> 36
        else -> null
    }

    private fun String.androidVersionKey(): String {
        val parts = split(".").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 && parts[1] == "0" -> parts[0]
            parts.size >= 2 -> "${parts[0]}.${parts[1]}"
            else -> parts.firstOrNull().orEmpty()
        }
    }

    private fun buildVariantName(arches: List<String>, versionCode: Long, type: String): String {
        val archPart = arches.takeIf { it.isNotEmpty() }?.joinToString("-") ?: "universal"
        val codePart = versionCode.takeIf { it > 0L }?.let { "-$it" }.orEmpty()
        return "$archPart$codePart.${type.lowercase(Locale.ROOT)}"
    }

    private val ApkComboVariant.isSingleApk: Boolean
        get() = fileType == "APK"

    private val ApkComboVariant.isArchive: Boolean
        get() = fileType == "XAPK" || fileType == "APKS"

    private val ApkComboVariant.matcherText: String
        get() = listOf(name, fileType, arches.joinToString(" ")).joinToString(" ")

    private data class ApkComboSearchResult(
        val packageName: String,
        val name: String,
        val author: String,
        val iconUrl: String,
        val url: String
    )

    private data class ApkComboDetails(
        val packageName: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Long,
        val iconUri: Uri,
        val sourceUrl: String,
        val whatsNew: String,
        val variants: List<ApkComboVariant>,
        val releaseType: ReleaseType
    )

    private data class ApkComboVariant(
        val name: String,
        val url: String,
        val versionCode: Long,
        val size: Long,
        val arches: List<String>,
        val minSdk: Int?,
        val isPhoneCompatible: Boolean,
        val fileType: String
    )

    companion object {
        private const val BASE_URL = "https://apkcombo.com"
        private const val USER_AGENT = "curl/8.0.1"
        private const val UPDATE_CONCURRENCY = 4
        private const val SEARCH_CONCURRENCY = 4
        private const val MAX_SEARCH_RESULTS = 10
        private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val sizeRegex = Regex("(\\d+(?:[\\.,]\\d+)?)\\s*(KB|MB|GB)", RegexOption.IGNORE_CASE)
        private val androidVersionRegex = Regex("Android\\s+([0-9]+(?:\\.[0-9]+)?)\\+", RegexOption.IGNORE_CASE)
    }
}
