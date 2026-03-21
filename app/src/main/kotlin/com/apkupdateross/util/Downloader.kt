package com.apkupdateross.util

import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File
) {

    data class DownloadResult(val stream: InputStream, val contentLength: Long)

    private val calls = ConcurrentHashMap<Int, MutableList<Call>>()
    private val cancelledIds = ConcurrentSkipListSet<Int>()

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
            response.body?.let {
                return DownloadResult(it.byteStream(), it.contentLength())
            }
        } else {
            response.close()
            Log.e("Downloader", "Download failed with error code: ${response.code}")
        }
        return null
    }.getOrElse {
        Log.e("Downloader", "Error downloading", it)
        null
    }

    private fun downloadRequest(url: String) = Request.Builder().url(url).build()

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
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(5000)
            cancelledIds.remove(id)
        }
    }

    fun clear(id: Int) {
        cancelledIds.remove(id)
        calls.remove(id)
        cleanup(id)
    }

}
