package com.apkupdateross.util

import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File
) {

    data class DownloadResult(val stream: InputStream, val contentLength: Long)

    private val calls = ConcurrentHashMap<Int, MutableList<Call>>()

    private fun registerCall(id: Int, call: Call): Call {
        calls.compute(id) { _, list ->
            (list ?: mutableListOf()).apply { add(call) }
        }
        return call
    }

    fun download(id: Int, url: String): File {
        val file = File(dir, randomUUID())
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        val call = registerCall(id, c.newCall(downloadRequest(url)))
        call.execute().use {
            if (it.isSuccessful) {
                it.body?.byteStream()?.copyTo(file.outputStream())
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

    fun cleanup() = runCatching {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun cancel(id: Int) = runCatching {
        calls.remove(id)?.forEach { call ->
            runCatching { call.cancel() }
        }
    }

    fun clear(id: Int) {
        calls.remove(id)
    }

}
