package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.UptodownSource
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


class UptodownRepository(
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
        Log.e("UptodownRepository", "Error looking for updates.", it)
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
                            loadDetails(result.url, searchResult = result)?.toAppUpdate(null)
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
        emit(Result.success(updates))
    }.catch {
        emit(Result.failure(it))
        Log.e("UptodownRepository", "Error searching.", it)
    }

    private fun checkUpdate(app: AppInstalled): AppUpdate? {
        val details = findDetailsForPackage(app.packageName) ?: return null
        if (!details.packageName.equals(app.packageName, ignoreCase = true)) return null
        if (!details.isCompatible()) return null
        if (!details.isNewerThan(app)) return null
        if (!shouldInclude(details.releaseType)) return null
        return details.toAppUpdate(app)
    }

    private fun findDetailsForPackage(packageName: String): UptodownDetails? {
        appUrlCache[packageName]?.let { cachedUrl ->
            loadDetails(cachedUrl, expectedPackageName = packageName)?.let { cachedDetails ->
                if (cachedDetails.packageName.equals(packageName, ignoreCase = true)) return cachedDetails
            }
            appUrlCache.remove(packageName)
        }

        val details = searchResults(packageName)
            .take(MAX_UPDATE_CANDIDATES)
            .firstNotNullOfOrNull { result ->
                loadDetails(result.url, expectedPackageName = packageName, searchResult = result)
                    ?.takeIf { it.packageName.equals(packageName, ignoreCase = true) }
            }

        details?.let { appUrlCache[packageName] = it.sourceUrl }
        return details
    }

    private fun searchResults(text: String): List<UptodownSearchResult> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("android")
            .addPathSegment("search")
            .addQueryParameter("query", text.trim())
            .build()
            .toString()

        val doc = requestDocument(url)
        return doc.select("#content-list > div.item, #content-list div.item")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url.lowercase(Locale.ROOT) }
    }

    private fun loadDetails(
        pageUrl: String,
        expectedPackageName: String? = null,
        searchResult: UptodownSearchResult? = null
    ): UptodownDetails? {
        val appPageUrl = pageUrl.toAppPageUrl()
        val downloadPageUrl = pageUrl.toDownloadPageUrl()
        val doc = requestDocument(downloadPageUrl, referer = appPageUrl)
        val info = doc.technicalInfo()

        val packageName = info["package name"]
            ?.takeIf { packageRegex.matches(it) }
            ?: doc.selectFirst("#gplay-url")
                ?.attr("data-url")
                ?.substringAfter("id=", missingDelimiterValue = "")
                ?.substringBefore("&")
                ?.takeIf { packageRegex.matches(it) }
            ?: expectedPackageName?.takeIf { packageRegex.matches(it) }
            ?: return null

        val nameElement = doc.selectFirst("#detail-app-name")
        val version = doc.selectFirst(".detail .info .version, div.version")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val fileId = nameElement?.attr("data-file-id")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("#detail-download-button")?.attr("data-file-id")?.takeIf { it.isNotBlank() }
        val appId = nameElement?.attr("data-code")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("#detail-download-button")?.attr("data-app-id")?.takeIf { it.isNotBlank() }
        val versionCode = version.versionCodeFromTag().takeIf { it > 0L }
            ?: fileId?.toLongOrNull()
            ?: 0L
        val name = nameElement
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: searchResult?.name
            ?: packageName
        val author = doc.selectFirst("#author-link")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: searchResult?.author
            ?: name
        val iconUri = doc.selectFirst("meta[property=og:image], meta[name=twitter:image]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: searchResult?.iconUrl.orEmpty()
        val fileType = info["file type"]?.uppercase(Locale.ROOT).orEmpty()
        val size = parseSize(info["size"].orEmpty())
        val sha256 = info["sha256"]?.takeIf { sha256Regex.matches(it) }
        val arches = parseArches(info["architecture"].orEmpty())
        val releaseUrl = fileId?.let { "$downloadPageUrl/$it-x" } ?: downloadPageUrl
        val description = searchResult?.description.orEmpty()

        return UptodownDetails(
            packageName = packageName,
            name = name,
            author = author,
            version = version,
            versionCode = versionCode,
            appId = appId,
            fileId = fileId,
            fileType = fileType,
            size = size,
            sha256 = sha256,
            arches = arches,
            iconUri = iconUri.toUriOrEmpty(),
            sourceUrl = appPageUrl,
            releaseUrl = releaseUrl,
            whatsNew = description,
            releaseType = ReleaseType.from(version, name)
        )
    }

    private fun UptodownDetails.toAppUpdate(app: AppInstalled?): AppUpdate =
        AppUpdate(
            name = name,
            packageName = packageName,
            version = version,
            oldVersion = app?.version ?: "?",
            versionCode = versionCode,
            oldVersionCode = app?.versionCode ?: 0L,
            source = UptodownSource,
            iconUri = if (iconUri != Uri.EMPTY) iconUri else app?.iconUri ?: Uri.EMPTY,
            link = Link.BrowserDownload(
                pageUrl = releaseUrl,
                expectedPackageName = packageName,
                sha256 = sha256,
                suggestedFileName = suggestedFileName(),
                isXapk = fileType.contains("XAPK", ignoreCase = true)
            ),
            sourceUrl = sourceUrl,
            releaseUrl = releaseUrl,
            whatsNew = whatsNew,
            releaseType = releaseType
        )

    private fun UptodownDetails.suggestedFileName(): String {
        val extension = when {
            fileType.contains("XAPK", ignoreCase = true) -> "xapk"
            fileType.contains("APKS", ignoreCase = true) -> "apks"
            else -> "apk"
        }
        return "${packageName.toSafeFileNamePart()}-${version.toSafeFileNamePart()}.$extension"
    }

    private fun UptodownDetails.isCompatible(): Boolean {
        if (arches.isNotEmpty() && !AbiMatcher.isCompatible(arches, Build.SUPPORTED_ABIS.toList())) {
            return false
        }
        val text = "$name $sourceUrl $releaseUrl"
        return !text.contains("Android TV", ignoreCase = true)
                && !text.contains("Wear OS", ignoreCase = true)
                && !text.contains("Android Wear", ignoreCase = true)
    }

    private fun UptodownDetails.isNewerThan(app: AppInstalled): Boolean {
        runCatching { Version(version) > Version(app.version) }
            .getOrNull()
            ?.let { return it }
        return versionCode > 0L && versionCode > app.versionCode
    }

    private fun shouldInclude(releaseType: ReleaseType): Boolean = when (releaseType) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private fun Element.toSearchResult(): UptodownSearchResult? {
        val anchor = selectFirst(".name a[href], a[href]") ?: return null
        val url = anchor.attr("abs:href")
            .ifBlank { anchor.attr("href").toAbsoluteUrl() }
            .ifBlank { onclickUrl().orEmpty() }
            .takeIf { it.isNotBlank() }
            ?: return null
        if (!url.contains(".uptodown.com/android", ignoreCase = true)) return null

        val name = anchor.selectFirst("h2")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: anchor.attr("title").removePrefix("Download ").trim().takeIf { it.isNotBlank() }
            ?: return null
        val author = selectFirst(".author")?.text()?.trim().orEmpty()
        val description = selectFirst(".description")?.text()?.trim().orEmpty()
        val iconUrl = selectFirst("img.app_card_img, figure img")?.bestImageUrl().orEmpty()
        return UptodownSearchResult(
            name = name,
            author = author,
            description = description,
            iconUrl = iconUrl,
            url = url.toAppPageUrl()
        )
    }

    private fun Element.onclickUrl(): String? =
        onclickUrlRegex.find(attr("onclick"))
            ?.groupValues
            ?.getOrNull(1)

    private fun Document.technicalInfo(): Map<String, String> =
        select("#technical-information tr").mapNotNull { row ->
            val label = row.selectFirst("th")?.text()?.trim()?.lowercase(Locale.ROOT)
                ?: return@mapNotNull null
            val value = row.select("td").lastOrNull()?.text()?.trim()
                ?: return@mapNotNull null
            if (label.isBlank() || value.isBlank()) null else label to value
        }.toMap()

    private fun requestDocument(url: String, referer: String = BASE_URL): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Uptodown HTTP ${response.code}: $url")
            }
            Jsoup.parse(response.body?.string().orEmpty(), url)
        }
    }

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

    private fun String.toAppPageUrl(): String {
        val trimmed = trimEnd('/')
        return when {
            trimmed.contains("/android/download", ignoreCase = true) ->
                trimmed.substringBefore("/android/download") + "/android"
            trimmed.endsWith("/android", ignoreCase = true) -> trimmed
            else -> "$trimmed/android"
        }
    }

    private fun String.toDownloadPageUrl(): String =
        "${toAppPageUrl().trimEnd('/')}/download"

    private fun String.toUriOrEmpty(): Uri =
        takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY

    private fun String.toSafeFileNamePart(): String =
        replace(unsafeFileNameRegex, "_").trim('_').ifBlank { "file" }

    private fun parseArches(raw: String): List<String> =
        raw.split(",", " ", "\n", "\t")
            .map { it.trim().removeSuffix(":") }
            .filter { it.isNotBlank() }

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

    private data class UptodownSearchResult(
        val name: String,
        val author: String,
        val description: String,
        val iconUrl: String,
        val url: String
    )

    private data class UptodownDetails(
        val packageName: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Long,
        val appId: String?,
        val fileId: String?,
        val fileType: String,
        val size: Long,
        val sha256: String?,
        val arches: List<String>,
        val iconUri: Uri,
        val sourceUrl: String,
        val releaseUrl: String,
        val whatsNew: String,
        val releaseType: ReleaseType
    )

    companion object {
        private const val BASE_URL = "https://en.uptodown.com"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36"
        private const val UPDATE_CONCURRENCY = 3
        private const val SEARCH_CONCURRENCY = 4
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_UPDATE_CANDIDATES = 4
        private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val sha256Regex = Regex("^[A-Fa-f0-9]{64}$")
        private val sizeRegex = Regex("(\\d+(?:[\\.,]\\d+)?)\\s*(KB|MB|GB)", RegexOption.IGNORE_CASE)
        private val onclickUrlRegex = Regex("location\\.href='([^']+)'")
        private val unsafeFileNameRegex = Regex("[^A-Za-z0-9._-]")
    }
}
