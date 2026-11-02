package com.apkupdateross.util

import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
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
    private val apkMirrorChallengeCoordinator: ApkMirrorChallengeCoordinator
) {

    data class DownloadResult(val stream: InputStream, val contentLength: Long, val url: String = "")
    data class FileDownloadResult(val file: File, val contentLength: Long, val url: String = "")

    private val calls = ConcurrentHashMap<Int, MutableList<Call>>()
    private val cancelledIds = ConcurrentSkipListSet<Int>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun registerCall(id: Int, call: Call): Call {
        if (cancelledIds.contains(id)) {
            call.cancel()
            return call
        }
        calls.compute(id) { _, list ->
            (list ?: mutableListOf()).apply { add(call) }
        }
        return call
    }

    fun download(id: Int, url: String): File {
        val file = File(dir, "cache_${id}_${randomUUID()}")
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        executeDownload(id, url, c).use { response ->
            if (response.isSuccessful) {
                file.outputStream().use { os ->
                    response.body?.byteStream()?.copyTo(os)
                }
            }
        }
        return file
    }

    fun downloadStream(id: Int, url: String): InputStream? = downloadWithSize(id, url)?.stream

    fun downloadWithSize(id: Int, url: String): DownloadResult? = runCatching {
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        val response = executeDownload(id, url, c)
        if (response.isSuccessful) {
            val body = response.body
            if (body != null) {
                return DownloadResult(body.byteStream(), body.contentLength(), response.request.url.toString())
            }
            response.close()
            Log.e("Downloader", "Download failed with an empty response body")
        } else {
            response.close()
            Log.e("Downloader", "Download failed with error code: ${response.code}")
        }
        return null
    }.getOrElse {
        Log.e("Downloader", "Error downloading", it)
        null
    }

    fun downloadFileWithSize(id: Int, url: String): FileDownloadResult? = runCatching {
        val file = File(dir, "cache_${id}_${randomUUID()}")
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        executeDownload(id, url, c).use { response ->
            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    file.outputStream().use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                    return FileDownloadResult(file, contentLength, response.request.url.toString())
                }
                Log.e("Downloader", "Download failed with an empty response body")
            } else {
                Log.e("Downloader", "Download failed with error code: ${response.code}")
            }
        }
        if (file.exists()) file.delete()
        null
    }.getOrElse {
        Log.e("Downloader", "Error downloading file", it)
        null
    }

    private fun executeDownload(id: Int, url: String, httpClient: OkHttpClient): Response {
        var verifiedUrl: String? = null
        var verificationAttempts = 0

        while (true) {
            try {
                val request = downloadRequest(id, url, verifiedUrl)
                val response = registerCall(id, httpClient.newCall(request)).execute()
                val responseUrl = response.request.url.toString()
                val isApkMirrorRequest = isApkMirrorUrl(url) || isApkMirrorUrl(responseUrl)
                if (response.code != 403 || !isApkMirrorRequest) return response

                val referer = request.header("Referer") ?: url
                val userAgent = request.header("User-Agent") ?: APKMIRROR_USER_AGENT
                response.close()
                if (verificationAttempts >= MAX_APKMIRROR_VERIFICATION_ATTEMPTS) {
                    throw IOException("APKMirror Cloudflare verification did not clear HTTP 403")
                }
                verifiedUrl = verifyApkMirror(responseUrl, referer, userAgent).resolvedUrl
                verificationAttempts++
            } catch (challenge: ApkMirrorChallengeRequired) {
                if (verificationAttempts >= MAX_APKMIRROR_VERIFICATION_ATTEMPTS) {
                    throw IOException("APKMirror Cloudflare verification did not clear HTTP 403", challenge)
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
        ) ?: throw IOException(
            "APKMirror requires Cloudflare verification; keep APKUpdater OSS open to continue"
        )
    }

    private fun downloadRequest(id: Int, url: String, verifiedUrl: String? = null): Request {
        val normalized = url.trim()
        val resolvedUrl = verifiedUrl ?: resolveApkMirrorDownloadUrl(id, normalized)
        return Request.Builder()
            .url(resolvedUrl)
            .apply {
                if (normalized.isApkMirrorHtmlDownloadUrl() || isApkMirrorUrl(resolvedUrl)) {
                    header("Accept", "*/*")
                    header("Referer", normalized.takeIf(::isApkMirrorUrl) ?: APKMIRROR_BASE_URL)
                    header("User-Agent", apkMirrorSession.userAgentOr(APKMIRROR_USER_AGENT))
                    apkMirrorSession.cookieHeaderFor(resolvedUrl)?.let { header("Cookie", it) }
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

        return registerCall(id, client.newCall(request)).execute().use { response ->
            if (response.code == 403) {
                throw ApkMirrorChallengeRequired(url, referer)
            }
            if (!response.isSuccessful) {
                throw IOException("APKMirror HTTP ${response.code}: $url")
            }
            Jsoup.parse(response.body?.string().orEmpty(), url)
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

private const val APKMIRROR_BASE_URL = "https://www.apkmirror.com"
private const val MAX_APKMIRROR_VERIFICATION_ATTEMPTS = 2
private val APKMIRROR_USER_AGENT: String
    get() = AppUserAgent.value
