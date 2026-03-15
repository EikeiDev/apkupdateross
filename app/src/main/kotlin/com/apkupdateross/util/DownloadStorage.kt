package com.apkupdateross.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DownloadStorage(private val context: Context) {

    fun save(fileName: String, mimeType: String, inputStream: InputStream): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(fileName, mimeType, inputStream)
        } else {
            saveLegacy(fileName, inputStream)
        }
    }

    private fun saveScoped(fileName: String, mimeType: String, inputStream: InputStream): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/APKUpdaterOSS")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            } ?: return false
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun saveLegacy(fileName: String, inputStream: InputStream): Boolean {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloads, "APKUpdaterOSS").apply { if (!exists()) mkdirs() }
        val targetFile = File(targetDir, fileName)
        return runCatching {
            FileOutputStream(targetFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrElse { false }
    }
}
