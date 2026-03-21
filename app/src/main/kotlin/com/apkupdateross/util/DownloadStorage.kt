package com.apkupdateross.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.util.InstallLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DownloadStorage(private val context: Context) {

    suspend fun save(fileName: String, mimeType: String, inputStream: InputStream, id: Int = 0, installLog: InstallLog? = null, total: Long = 0L, offset: Long = 0L): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(fileName, mimeType, inputStream, id, installLog, total, offset)
        } else {
            saveLegacy(fileName, inputStream, id, installLog, total, offset)
        }
    }

    private suspend fun saveScoped(fileName: String, mimeType: String, inputStream: InputStream, id: Int, installLog: InstallLog?, total: Long, offset: Long): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.TITLE, fileName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "APKUpdaterOSS")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                inputStream.use { input ->
                    if (input.copyToAndNotify(output, id, installLog, total, offset) == null) throw CancellationException("Cancelled")
                    if (total > 0) {
                         installLog?.emitProgress(AppInstallProgress(id, total, total))
                    }
                }
            } ?: return false
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            if (it is CancellationException) throw it
            false
        }
    }

    private suspend fun saveLegacy(fileName: String, inputStream: InputStream, id: Int, installLog: InstallLog?, total: Long, offset: Long): Boolean {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloads, "APKUpdaterOSS").apply { if (!exists()) mkdirs() }
        val targetFile = File(targetDir, fileName)
        return try {
            FileOutputStream(targetFile).use { output ->
                inputStream.use { input ->
                    if (input.copyToAndNotify(output, id, installLog, total, offset) == null) throw CancellationException("Cancelled")
                    if (total > 0) {
                         installLog?.emitProgress(AppInstallProgress(id, total, total))
                    }
                }
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (targetFile.exists()) targetFile.delete()
            false
        }
    }

}

suspend fun InputStream.copyToAndNotify(
    out: OutputStream,
    id: Int,
    installLog: InstallLog? = null,
    total: Long = 0L,
    offset: Long = 0L,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): Long? {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        if (!coroutineContext.isActive) return null
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        installLog?.emitProgress(AppInstallProgress(id, offset + bytesCopied, total))
        bytes = read(buffer)
    }
    return bytesCopied
}
