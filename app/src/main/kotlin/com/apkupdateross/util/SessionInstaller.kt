package com.apkupdateross.util

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
import androidx.core.content.ContextCompat.startActivity
import com.apkupdateross.BuildConfig
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.ui.activity.MainActivity
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile


class SessionInstaller(
    private val context: Context,
    private val installLog: InstallLog
) {

    companion object {
        const val INSTALL_ACTION = "installAction"
    }

    private val installMutex = AtomicBoolean(false)

    suspend fun install(id: Int, packageName: String, stream: InputStream) =
        install(id, packageName, listOf(stream))

    private suspend fun install(id: Int, packageName: String, streams: List<InputStream>) {
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        if (Build.VERSION.SDK_INT > 24) {
            params.setOriginatingUid(android.os.Process.myUid())
        }

        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }

        val sessionId = packageInstaller.createSession(params)
        var bytes = 0L
        packageInstaller.openSession(sessionId).use { session ->
            streams.forEach {
                session.openWrite("$packageName.${randomUUID()}", 0, -1).use { output ->
                    bytes += it.copyToAndNotify(output, id, installLog, bytes)
                    it.close()
                    session.fsync(output)
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                action = "$INSTALL_ACTION.$id"
            }

            installMutex.lock()
            val pending = PendingIntent.getActivity(context, 0, intent, FLAG_MUTABLE)
            session.commit(pending.intentSender)
            session.close()
        }
    }

    fun rootInstall(file: File): Boolean {
        val res = Shell.cmd("pm install -r ${file.absolutePath}").exec().isSuccess
        file.delete()
        return res
    }

    fun rootInstallXapk(file: File): Boolean {
        val zip = ZipFile(file)
        val apks = zip.entries().toList().filter { it.name.contains(".apk") }
        val result = if (apks.size == 1) {
            val tempApk = File(file.parentFile, "${randomUUID()}.apk")
            zip.getInputStream(apks[0]).use { it.copyTo(tempApk.outputStream()) }
            val res = Shell.cmd("pm install -r ${tempApk.absolutePath}").exec().isSuccess
            tempApk.delete()
            res
        } else {
            val createResult = Shell.cmd("pm install-create -r").exec()
            val sessionId = Regex("\\[(\\d+)]").find(createResult.out.joinToString(""))?.groupValues?.get(1)
                ?: run { zip.close(); file.delete(); return false }
            apks.forEachIndexed { index, entry ->
                val tempApk = File(file.parentFile, "${randomUUID()}_$index.apk")
                zip.getInputStream(entry).use { it.copyTo(tempApk.outputStream()) }
                Shell.cmd("pm install-write -S ${tempApk.length()} $sessionId $index ${tempApk.absolutePath}").exec()
                tempApk.delete()
            }
            Shell.cmd("pm install-commit $sessionId").exec().isSuccess
        }
        zip.close()
        file.delete()
        return result
    }

    fun finish() = installMutex.unlock()

    fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!context.packageManager.canRequestPackageInstalls()) {
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                val intent = Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(context, intent, null)
                return false
            }
        }
        return true
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun installXapk(id: Int, packageName: String, stream: InputStream) {
        // Copy file to disk.
        // TODO: Find a way to do this without saving file
        val file = File(context.cacheDir, randomUUID())
        stream.copyTo(file.outputStream())

        // Get entries
        val zip = ZipFile(file)
        val entries = zip.entries().toList()

        // Install all the apks
        // TODO: Try to install only needed apks
        val apks = entries.filter { it.name.contains(".apk") }.map { zip.getInputStream(it) }
        install(id, packageName, apks)

        // Cleanup
        zip.close()
        file.delete()
    }

    suspend fun playInstall(id: Int, packageName: String, streams: List<InputStream>) =
        install(id, packageName, streams)

    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun shizukuInstall(id: Int, packageName: String, stream: InputStream) =
        shizukuInstall(id, packageName, listOf(stream))

    suspend fun shizukuInstall(id: Int, packageName: String, streams: List<InputStream>) {
        // Write streams to temp files, then install via Shizuku shell
        val tempFiles = streams.mapIndexed { index, stream ->
            val file = File(context.cacheDir, "${randomUUID()}_$index.apk")
            var bytes = 0L
            file.outputStream().use { output ->
                bytes = stream.copyToAndNotify(output, id, installLog, bytes)
                stream.close()
            }
            file
        }

        try {
            val result = if (tempFiles.size == 1) {
                shizukuExec("pm install -r ${tempFiles[0].absolutePath}")
            } else {
                val sessionResult = shizukuExec("pm install-create -r")
                val sessionId = Regex("\\[(\\d+)]").find(sessionResult.out)?.groupValues?.get(1)
                    ?: throw Exception("Failed to create install session: ${sessionResult.out}")
                tempFiles.forEachIndexed { index, file ->
                    shizukuExec("pm install-write -S ${file.length()} $sessionId $index ${file.absolutePath}")
                }
                shizukuExec("pm install-commit $sessionId")
            }

            if (!result.isSuccess) {
                throw Exception("Shizuku install failed: ${result.err}")
            }
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    private data class ShizukuResult(val isSuccess: Boolean, val out: String, val err: String)

    private fun shizukuExec(command: String): ShizukuResult {
        val process = Class.forName("rikka.shizuku.Shizuku")
            .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .also { it.isAccessible = true }
            .invoke(null, arrayOf("sh", "-c", command), null, null) as Process

        process.outputStream.close()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return ShizukuResult(exitCode == 0, out, err)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun shizukuInstallXapk(id: Int, packageName: String, stream: InputStream) {
        val file = File(context.cacheDir, randomUUID())
        stream.copyTo(file.outputStream())
        val zip = ZipFile(file)
        val entries = zip.entries().toList()
        val apks = entries.filter { it.name.contains(".apk") }.map { zip.getInputStream(it) }
        shizukuInstall(id, packageName, apks)
        zip.close()
        file.delete()
    }

}

fun InputStream.copyToAndNotify(out: OutputStream, id: Int, installLog: InstallLog, total: Long, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        installLog.emitProgress(AppInstallProgress(id, progress = total + bytesCopied))
        bytes = read(buffer)
    }
    return bytesCopied
}
