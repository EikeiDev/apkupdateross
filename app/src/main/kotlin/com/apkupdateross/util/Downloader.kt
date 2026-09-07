package com.apkupdateross.util

import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val dir: File
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
        val call = registerCall(id, c.newCall(downloadRequest(url)))
        call.execute().use { response ->
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
        val call = registerCall(id, c.newCall(downloadRequest(url)))
        val response = call.execute()
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
        val call = registerCall(id, c.newCall(downloadRequest(url)))
        call.execute().use { response ->
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

    private fun downloadRequest(url: String): Request {
        val normalized = url.trim()
        val resolvedUrl = resolveApkMirrorDownloadUrl(normalized)
        return Request.Builder()
            .url(resolvedUrl)
            .apply {
                if (normalized.isApkMirrorHtmlDownloadUrl() || resolvedUrl.startsWith(APKMIRROR_BASE_URL)) {
                    header("Accept", "*/*")
                    header("Referer", normalized.takeIf { it.startsWith(APKMIRROR_BASE_URL) } ?: APKMIRROR_BASE_URL)
                    header("User-Agent", APKMIRROR_USER_AGENT)
                }
            }
            .build()
    }

    private fun resolveApkMirrorDownloadUrl(url: String): String {
        val normalized = url.trim()
        if (!normalized.isApkMirrorHtmlDownloadUrl()) return normalized
        if (normalized.contains("/wp-content/themes/APKMirror/download.php", ignoreCase = true)) return normalized

        val thankYouUrl = if (normalized.contains("/download/?key=", ignoreCase = true)) {
            normalized
        } else {
            val page = requestApkMirrorHtml(normalized, APKMIRROR_BASE_URL)
            page.selectFirst("a.downloadButton[href*=/download/?key=], a[href*=/download/?key=]")
                ?.attr("abs:href")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("APKMirror download button not found")
        }

        val thankYouPage = requestApkMirrorHtml(thankYouUrl, normalized)
        return thankYouPage.selectFirst("#download-link[href], a[href*=/download.php?id=]")
            ?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("APKMirror final download link not found")
    }

    private fun requestApkMirrorHtml(url: String, referer: String): org.jsoup.nodes.Document {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", referer)
            .header("User-Agent", APKMIRROR_USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
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

private const val APKMIRROR_BASE_URL = "https://www.apkmirror.com"
private val APKMIRROR_USER_AGENT: String
    get() = AppUserAgent.value
