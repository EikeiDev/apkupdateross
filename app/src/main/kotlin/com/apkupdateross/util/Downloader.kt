package com.apkupdateross.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File
) {

    data class DownloadResult(val stream: InputStream, val contentLength: Long)

    fun download(url: String): File {
        val file = File(dir, randomUUID())
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        c.newCall(downloadRequest(url)).execute().use {
            if (it.isSuccessful) {
                it.body?.byteStream()?.copyTo(file.outputStream())
            }
        }
        return file
    }

    fun downloadStream(url: String): InputStream? = downloadWithSize(url)?.stream

    fun downloadWithSize(url: String): DownloadResult? = runCatching {
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        val response = c.newCall(downloadRequest(url)).execute()
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

}
