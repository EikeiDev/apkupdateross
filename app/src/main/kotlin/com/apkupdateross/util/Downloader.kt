package com.apkupdateross.util

import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File,
    private val apkMirrorSession: ApkMirrorSession,
    private val apkMirrorChallengeCoordinator: ApkMirrorChallengeCoordinator,
    private val fourPdaSession: FourPdaSession
) {

    init {
        dir.mkdirs()
        dir.listFiles()?.forEach { staleFile -> staleFile.delete() }
    }

    data class FileDownloadResult(val file: File, val contentLength: Long, val url: String = "")

    private val calls = ConcurrentHashMap<Int, CopyOnWriteArrayList<Call>>()
    private val cancelledIds = ConcurrentSkipListSet<Int>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun registerCall(id: Int, call: Call): Call {
        calls.compute(id) { _, list ->
            if (cancelledIds.contains(id)) {
                call.cancel()
                list
            } else {
                (list ?: CopyOnWriteArrayList()).apply { add(call) }
            }
        }
        return call
    }

    private fun unregisterCall(id: Int, call: Call) {
        calls.computeIfPresent(id) { _, list ->
            list.remove(call)
            list.takeIf { it.isNotEmpty() }
        }
    }

    fun download(id: Int, url: String): File {
        return downloadFileWithSize(id, url)?.file
            ?: throw IOException("Download failed: $url")
    }

    fun createTempFile(id: Int, suffix: String): File =
        File.createTempFile("cache_${id}_", suffix, dir)

    fun downloadFileWithSize(
        id: Int,
        url: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        requestHeaders: Map<String, String> = emptyMap()
    ): FileDownloadResult? {
        // PackageManager refuses to inspect extensionless APKs on some Android builds.
        // The file is internal and may still contain an APKM/APKS archive; split detection
        // uses its contents rather than this temporary suffix.
        val file = File(dir, downloadCacheFileName(id, randomUUID().toString()))
        return runCatching {
            val initialUrl = url.trim().toHttpUrlOrNull()
                ?.takeIf { it.isHttps }
                ?: throw IOException("Only HTTPS download URLs are allowed")
            val c = clientFor(url)
            var lastError: Throwable? = null

            for (attempt in 0..MAX_DOWNLOAD_RETRIES) {
                if (cancelledIds.contains(id)) {
                    throw IOException("Download cancelled")
                }
                if (attempt > 0) {
                    Log.w("Downloader", "Retrying download (attempt ${attempt + 1}) for $url")
                    Thread.sleep(RETRY_BACKOFF_BASE_MS * attempt)
                }
                val existingBytes = if (file.isFile) file.length() else 0L
                val canResume = existingBytes > 0L && !isApkMirrorUrl(url)
                val attemptHeaders = if (canResume) {
                    requestHeaders + ("Range" to "bytes=$existingBytes-")
                } else {
                    requestHeaders
                }

                try {
                    val activeResponse = executeDownload(id, initialUrl.toString(), c, attemptHeaders)
                    try {
                        activeResponse.response.use { response ->
                            if (!response.request.url.isHttps) {
                                throw IOException("Download redirected to an insecure URL")
                            }
                            if (response.code == 416 && existingBytes > 0L) {
                                file.delete()
                                throw RetryableDownloadException("Range not satisfiable, restarting")
                            }
                            if (response.isSuccessful) {
                                val body = response.body ?: throw IOException("Empty response body")
                                val isPartial = response.code == 206 && canResume
                                val startOffset = if (isPartial) existingBytes else 0L
                                val remainingLength = body.contentLength()
                                val expectedTotal = if (remainingLength > 0L) startOffset + remainingLength else -1L

                                java.io.FileOutputStream(file, isPartial).use { output ->
                                    body.byteStream().use { input ->
                                        input.copyToWithProgress(
                                            output = output,
                                            total = expectedTotal.coerceAtLeast(0L),
                                            onProgress = if (onProgress != null) {
                                                { copied, total -> onProgress(startOffset + copied, total) }
                                            } else null
                                        )
                                    }
                                }
                                if (file.length() == 0L) {
                                    throw RetryableDownloadException("Download returned an empty file")
                                }
                                if (expectedTotal > 0L && file.length() != expectedTotal) {
                                    throw RetryableDownloadException("Incomplete download: expected $expectedTotal bytes, got ${file.length()}")
                                }
                                return FileDownloadResult(file, expectedTotal, response.request.url.toString())
                            } else {
                                val bodyPreview = response.body?.string()
                                    ?.replace(Regex("\\s+"), " ")
                                    ?.take(160)
                                    .orEmpty()
                                Log.e(
                                    "Downloader",
                                    "Download failed: HTTP ${response.code}, host=${response.request.url.host}, " +
                                        "type=${response.header("Content-Type").orEmpty()}, body=$bodyPreview"
                                )
                                if (response.code in RETRYABLE_HTTP_CODES) {
                                    throw RetryableDownloadException("HTTP ${response.code}")
                                }
                                if (file.exists()) file.delete()
                                return null
                            }
                        }
                    } finally {
                        unregisterCall(id, activeResponse.call)
                    }
                } catch (error: Throwable) {
                    lastError = error
                    if (cancelledIds.contains(id)) {
                        file.delete()
                        return null
                    }
                    val retryable = error !is NonRetryableDownloadException &&
                        (error is RetryableDownloadException || error is IOException)
                    if (!retryable || attempt >= MAX_DOWNLOAD_RETRIES) {
                        throw error
                    }
                }
            }
            throw lastError ?: IOException("Download failed")
        }.getOrElse {
            file.delete()
            Log.e("Downloader", "Error downloading file", it)
            null
        }
    }

    private fun clientFor(url: String): OkHttpClient {
        val host = url.trim().toHttpUrlOrNull()?.host.orEmpty().lowercase(Locale.ROOT)
        return when {
            host == "apkpure.com" || host.endsWith(".apkpure.com") -> apkPureClient
            host == "auroraoss.com" || host.endsWith(".auroraoss.com") -> auroraClient
            else -> client
        }
    }

    private data class ActiveResponse(val call: Call, val response: Response)

    private fun executeDownload(
        id: Int,
        url: String,
        httpClient: OkHttpClient,
        requestHeaders: Map<String, String>
    ): ActiveResponse {
        var verifiedUrl: String? = null
        var verificationAttempts = 0

        while (true) {
            try {
                val request = downloadRequest(id, url, verifiedUrl, requestHeaders)
                val call = registerCall(id, httpClient.newCall(request))
                val response = try {
                    call.execute()
                } catch (error: Throwable) {
                    unregisterCall(id, call)
                    throw error
                }
                val responseUrl = response.request.url.toString()
                val isApkMirrorRequest = isApkMirrorUrl(url) || isApkMirrorUrl(responseUrl)
                if (response.code != 403 || !isApkMirrorRequest) return ActiveResponse(call, response)

                val referer = request.header("Referer") ?: url
                val userAgent = request.header("User-Agent") ?: APKMIRROR_USER_AGENT
                response.close()
                unregisterCall(id, call)
                if (verificationAttempts >= MAX_APKMIRROR_VERIFICATION_ATTEMPTS) {
                    throw NonRetryableDownloadException("APKMirror Cloudflare verification did not clear HTTP 403")
                }
                verifiedUrl = verifyApkMirror(responseUrl, referer, userAgent).resolvedUrl
                verificationAttempts++
            } catch (challenge: ApkMirrorChallengeRequired) {
                if (verificationAttempts >= MAX_APKMIRROR_VERIFICATION_ATTEMPTS) {
                    throw NonRetryableDownloadException("APKMirror Cloudflare verification did not clear HTTP 403", challenge)
                }
                verifiedUrl = verifyApkMirror(
                    challenge.challengeUrl,
                    challenge.referer,
                    apkMirrorSession.userAgentOr(APKMIRROR_USER_AGENT)
                ).resolvedUrl
                verificationAttempts++
            }
        }
    }

    private fun verifyApkMirror(
        challengeUrl: String,
        referer: String,
        userAgent: String
    ): ApkMirrorChallengeCoordinator.VerificationResult {
        return apkMirrorChallengeCoordinator.requestVerification(
            challengeUrl = challengeUrl,
            referer = referer,
            userAgent = userAgent
        ) ?: throw NonRetryableDownloadException(
            "APKMirror requires Cloudflare verification; keep APKUpdater OSS open to continue"
        )
    }

    private fun downloadRequest(
        id: Int,
        url: String,
        verifiedUrl: String? = null,
        requestHeaders: Map<String, String> = emptyMap()
    ): Request {
        val normalized = url.trim()
        val resolvedUrl = verifiedUrl ?: resolveApkMirrorDownloadUrl(id, normalized)
        return Request.Builder()
            .url(resolvedUrl)
            .apply {
                requestHeaders.forEach { (name, value) -> header(name, value) }
                if (normalized.isApkMirrorHtmlDownloadUrl() || isApkMirrorUrl(resolvedUrl)) {
                    header("Accept", "*/*")
                    header("Referer", normalized.takeIf(::isApkMirrorUrl) ?: APKMIRROR_BASE_URL)
                    header("User-Agent", apkMirrorSession.userAgentOr(APKMIRROR_USER_AGENT))
                    apkMirrorSession.cookieHeaderFor(resolvedUrl)?.let { header("Cookie", it) }
                } else if (isFourPdaUrl(resolvedUrl)) {
                    header("Accept", "*/*")
                    header("Referer", "https://4pda.to/forum/")
                    header("User-Agent", AppUserAgent.value)
                    fourPdaSession.cookieHeaderFor(resolvedUrl)?.let { header("Cookie", it) }
                }
            }
            .build()
    }

    private fun resolveApkMirrorDownloadUrl(id: Int, url: String): String {
        val normalized = url.trim()
        if (!normalized.isApkMirrorHtmlDownloadUrl()) return normalized
        if (normalized.contains("/wp-content/themes/APKMirror/download.php", ignoreCase = true)) return normalized

        val thankYouUrl = if (normalized.contains("/download/?key=", ignoreCase = true)) {
            normalized
        } else {
            val page = requestApkMirrorHtml(id, normalized, APKMIRROR_BASE_URL)
            page.selectFirst("a.downloadButton[href*=/download/?key=], a[href*=/download/?key=]")
                ?.attr("abs:href")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("APKMirror download button not found")
        }

        val thankYouPage = requestApkMirrorHtml(id, thankYouUrl, normalized)
        return thankYouPage.selectFirst("#download-link[href], a[href*=/download.php?id=]")
            ?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("APKMirror final download link not found")
    }

    private fun requestApkMirrorHtml(id: Int, url: String, referer: String): org.jsoup.nodes.Document {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", referer)
            .header("User-Agent", apkMirrorSession.userAgentOr(APKMIRROR_USER_AGENT))
            .apply {
                apkMirrorSession.cookieHeaderFor(url)?.let { header("Cookie", it) }
            }
            .get()
            .build()

        val call = registerCall(id, client.newCall(request))
        return try {
            call.execute().use { response ->
                if (response.code == 403) {
                    throw ApkMirrorChallengeRequired(url, referer)
                }
                if (!response.isSuccessful) {
                    throw IOException("APKMirror HTTP ${response.code}: $url")
                }
                Jsoup.parse(response.body?.string().orEmpty(), url)
            }
        } finally {
            unregisterCall(id, call)
        }
    }

    private fun String.isApkMirrorHtmlDownloadUrl(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.startsWith(APKMIRROR_BASE_URL)
                && (
                    lower.contains("-apk-download")
                            || lower.contains("/download/?key=")
                            || lower.contains("/wp-content/themes/apkmirror/download.php")
                )
    }

    fun cacheSizeBytes(): Long = runCatching {
        dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

    fun cleanup(id: Int? = null) = runCatching {
        if (id == null) {
            dir.listFiles()?.forEach { it.delete() }
        } else {
             dir.listFiles()?.filter { it.name.startsWith("cache_${id}_") }?.forEach { it.delete() }
        }
    }

    fun cancel(id: Int) = runCatching {
        cancelledIds.add(id)
        calls.remove(id)?.forEach { call ->
            runCatching { call.cancel() }
        }
        cleanup(id)
        // Auto-remove from cancelledIds after 5 seconds to prevent memory leak
        // and allow future re-downloads of the same ID.
        cleanupScope.launch {
            delay(5000)
            cancelledIds.remove(id)
        }
    }

    fun clear(id: Int) {
        cancelledIds.remove(id)
        calls.remove(id)
        cleanup(id)
    }

}

private class ApkMirrorChallengeRequired(
    val challengeUrl: String,
    val referer: String
) : IOException("APKMirror Cloudflare challenge: $challengeUrl")

private class RetryableDownloadException(message: String) : IOException(message)
private class NonRetryableDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

private const val MAX_DOWNLOAD_RETRIES = 2
private const val RETRY_BACKOFF_BASE_MS = 800L
private val RETRYABLE_HTTP_CODES = setOf(408, 429, 500, 502, 503, 504)

private const val APKMIRROR_BASE_URL = "https://www.apkmirror.com"
private const val MAX_APKMIRROR_VERIFICATION_ATTEMPTS = 1
private val APKMIRROR_USER_AGENT: String
    get() = AppUserAgent.value

internal fun downloadCacheFileName(id: Int, token: String): String = "cache_${id}_$token.apk"

internal fun InputStream.copyToWithProgress(
    output: OutputStream,
    total: Long,
    onProgress: ((downloaded: Long, total: Long) -> Unit)?,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    nanoTime: () -> Long = System::nanoTime
): Long {
    require(bufferSize > 0) { "bufferSize must be positive" }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    var lastReported = -1L
    var lastReportTime = nanoTime()

    fun report(force: Boolean = false) {
        if (onProgress == null || copied == lastReported) return
        val now = nanoTime()
        if (!force && now - lastReportTime < PROGRESS_UPDATE_INTERVAL_NANOS) return
        lastReported = copied
        lastReportTime = now
        onProgress(copied, total)
    }

    report(force = true)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        copied += read
        report()
    }
    report(force = true)
    return copied
}

private const val PROGRESS_UPDATE_INTERVAL_NANOS = 100_000_000L
