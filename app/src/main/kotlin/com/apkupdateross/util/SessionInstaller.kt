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
import android.util.Log
import com.apkupdateross.BuildConfig
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.ui.activity.MainActivity
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import moe.shizuku.server.IShizukuService
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID.randomUUID
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
        installList(id, packageName, listOf(stream))

    private suspend fun installList(id: Int, packageName: String, streams: List<InputStream>) {
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
            streams.forEach { stream ->
                session.openWrite("$packageName.${randomUUID()}", 0, -1).use { output ->
                    bytes += stream.copyToAndNotify(output, id, installLog, bytes)
                    stream.close()
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
        val file = File(context.cacheDir, randomUUID().toString())
        stream.copyTo(file.outputStream())

        val zip = ZipFile(file)
        val entries = zip.entries().toList()

        val apks = entries.filter { it.name.contains(".apk") }.map { zip.getInputStream(it) }
        installList(id, packageName, apks)

        zip.close()
        file.delete()
    }

    suspend fun playInstall(id: Int, packageName: String, streams: List<InputStream>) =
        installList(id, packageName, streams)

    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun shizukuInstall(id: Int, packageName: String, stream: InputStream) =
        shizukuInstall(id, packageName, listOf(stream))

    suspend fun shizukuInstall(id: Int, packageName: String, streams: List<InputStream>) {
        if (streams.size == 1) {
            val stream = streams[0]
            val tempFile = File(context.cacheDir, "${randomUUID()}.apk")
            var bytes = 0L
            tempFile.outputStream().use { output ->
                bytes = stream.copyToAndNotify(output, id, installLog, bytes)
                stream.close()
            }
            try {
                val result = shizukuExec("pm install -r -S $bytes", tempFile)
                if (!result.isSuccess) {
                    Log.e("SessionInstaller", "Shizuku install failed: ${result.err}")
                    throw Exception("Shizuku install failed: ${result.err}")
                }
                Log.i("SessionInstaller", "Shizuku install successful")
            } finally {
                tempFile.delete()
            }
        } else {
            val createResult = shizukuExec("pm install-create -r")
            val sessionId = Regex("\\[(\\d+)]").find(createResult.out)?.groupValues?.get(1)
                ?: throw Exception("Failed to create install session: ${createResult.out}")

            var totalBytes = 0L
            streams.forEachIndexed { index, stream ->
                val tempFile = File(context.cacheDir, "${randomUUID()}_$index.apk")
                var bytes = 0L
                tempFile.outputStream().use { output ->
                    bytes = stream.copyToAndNotify(output, id, installLog, totalBytes)
                    totalBytes += bytes
                    stream.close()
                }
                try {
                    val result = shizukuExec("pm install-write -S $bytes $sessionId $index", tempFile)
                    if (!result.isSuccess) throw Exception("Shizuku install-write failed: ${result.err}")
                } finally {
                    tempFile.delete()
                }
            }

            val commitResult = shizukuExec("pm install-commit $sessionId")
            if (!commitResult.isSuccess) throw Exception("Shizuku install-commit failed: ${commitResult.err}")
        }
    }

    private data class ShizukuResult(val isSuccess: Boolean, val out: String, val err: String)

    private fun shizukuExec(command: String, inputFile: File? = null): ShizukuResult {
        return try {
            val binder = Shizuku.getBinder()
            val service = IShizukuService.Stub.asInterface(binder)
            val iRemoteProcess = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val constructor = ShizukuRemoteProcess::class.java.getDeclaredConstructor(
                Class.forName("moe.shizuku.server.IRemoteProcess")
            )
            constructor.isAccessible = true
            val process = constructor.newInstance(iRemoteProcess)
            
            inputFile?.inputStream()?.use { input ->
                process.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: process.outputStream.close()

            val outText = process.inputStream.bufferedReader().use { it.readText() }
            val errText = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            ShizukuResult(exitCode == 0, outText, errText)
        } catch (e: Exception) {
            Log.e("SessionInstaller", "Shizuku execution failed: $command", e)
            val errorMsg = e.message ?: "Unknown error"
            ShizukuResult(false, "", errorMsg)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun shizukuInstallXapk(id: Int, packageName: String, stream: InputStream) {
        val file = File(context.cacheDir, randomUUID().toString())
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
    var lastEmitted: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        if (bytesCopied - lastEmitted >= 65536) {
            installLog.emitProgress(AppInstallProgress(id, progress = total + bytesCopied))
            lastEmitted = bytesCopied
        }
        bytes = read(buffer)
    }
    installLog.emitProgress(AppInstallProgress(id, progress = total + bytesCopied))
    return bytesCopied
}
