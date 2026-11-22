package com.apkupdateross.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.InstallLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DownloadStorage(
    private val context: Context,
    private val prefs: Prefs
) {

    suspend fun save(fileName: String, mimeType: String, inputStream: InputStream, id: Int = 0, installLog: InstallLog? = null, total: Long = 0L, offset: Long = 0L): Boolean = withContext(Dispatchers.IO) {
        inputStream.use { input ->
            val safeFileName = fileName.safeDownloadFileName()
            val customFolderUri = prefs.downloadFolderUri.get().takeIf { it.isNotBlank() }
            if (customFolderUri != null) {
                return@withContext saveToDocumentTree(customFolderUri, safeFileName, mimeType, input, id, installLog, total, offset)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(safeFileName, mimeType, input, id, installLog, total, offset)
            } else {
                saveLegacy(safeFileName, input, id, installLog, total, offset)
            }
        }
    }

    private suspend fun saveToDocumentTree(folderUri: String, fileName: String, mimeType: String, inputStream: InputStream, id: Int, installLog: InstallLog?, total: Long, offset: Long): Boolean {
        val resolver = context.contentResolver
        val treeUri = Uri.parse(folderUri)
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val uri = DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName) ?: return false

        return runCatching {
            val output = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Could not open the selected folder for writing")
            output.use {
                inputStream.use { input ->
                    if (input.copyToAndNotify(it, id, installLog, total, offset) == null) throw CancellationException("Cancelled")
                    if (total > 0) {
                        installLog?.emitProgress(AppInstallProgress(id, total, total))
                    }
                }
            }
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            if (it is CancellationException) throw it
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
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
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("Could not open the Downloads item for writing")
            output.use {
                inputStream.use { input ->
                    if (input.copyToAndNotify(it, id, installLog, total, offset) == null) throw CancellationException("Cancelled")
                    if (total > 0) {
                         installLog?.emitProgress(AppInstallProgress(id, total, total))
                    }
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            if (resolver.update(uri, contentValues, null, null) <= 0) {
                throw IOException("Could not finalize the Downloads item")
            }
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            if (it is CancellationException) throw it
            false
        }
    }

    private suspend fun saveLegacy(fileName: String, inputStream: InputStream, id: Int, installLog: InstallLog?, total: Long, offset: Long): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloads, "APKUpdaterOSS")
        if (!targetDir.exists() && !targetDir.mkdirs()) return false
        val targetFile = runCatching { createUniqueFile(targetDir, fileName) }.getOrElse { return false }
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

private fun createUniqueFile(directory: File, fileName: String): File {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        .orEmpty()
    val base = fileName.removeSuffix(extension)
    repeat(1_000) { index ->
        val suffix = if (index == 0) "" else " ($index)"
        val candidate = File(directory, "$base$suffix$extension")
        if (candidate.createNewFile()) return candidate
    }
    throw IOException("Could not create a unique download file")
}

internal fun String.safeDownloadFileName(): String {
    val cleaned = replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .trim()
        .trim('.')
    val fallback = cleaned.takeIf { it.isNotBlank() } ?: "download.apk"
    val extension = fallback.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.length in 1..10 }
        ?.let { ".$it" }
        .orEmpty()
    if (fallback.toByteArray(Charsets.UTF_8).size <= MAX_FILE_NAME_BYTES) return fallback
    val base = fallback.removeSuffix(extension)
        .takeUtf8Bytes(MAX_FILE_NAME_BYTES - extension.toByteArray(Charsets.UTF_8).size)
        .trimEnd('.')
    return base.ifBlank { "download" } + extension
}

private fun String.takeUtf8Bytes(maxBytes: Int): String = buildString {
    var usedBytes = 0
    for (codePoint in this@takeUtf8Bytes.codePoints().toArray()) {
        val text = String(Character.toChars(codePoint))
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (usedBytes + bytes > maxBytes) break
        append(text)
        usedBytes += bytes
    }
}

private const val MAX_FILE_NAME_BYTES = 240

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
