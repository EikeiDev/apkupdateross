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
import java.security.MessageDigest
import java.util.UUID.randomUUID
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore


class SessionInstaller(
    private val context: Context,
    private val installLog: InstallLog
) {

    companion object {
        const val INSTALL_ACTION = "com.apkupdateross.INSTALL_ACTION"
        const val INSTALL_ID = "installId"
        private const val MAX_SPLIT_APK_COUNT = 256
        private const val MAX_SPLIT_APK_BYTES = 2L * 1024L * 1024L * 1024L
        private const val NO_INSTALL_OWNER = Long.MIN_VALUE
    }

    private val installOwner = AtomicLong(NO_INSTALL_OWNER)
    private val installSemaphore = Semaphore(permits = 1)

    suspend fun install(id: Int, packageName: String, stream: InputStream, total: Long = 0L) =
        installList(id, packageName, listOf(stream), total)

    private suspend fun installList(id: Int, packageName: String, streams: List<InputStream>, total: Long = 0L) {
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        params.setOriginatingUid(android.os.Process.myUid())

        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }

        val sessionId = packageInstaller.createSession(params)
        var bytes = 0L
        var committed = false
        var mutexLocked = false
        try {
            packageInstaller.openSession(sessionId).use { session ->
                streams.forEach { stream ->
                    stream.use { input ->
                        session.openWrite("$packageName.${randomUUID()}", 0, -1).use { output ->
                            val copied = input.copyToAndNotify(output, id, installLog, total, bytes)
                                ?: throw CancellationException("Installation cancelled")
                            bytes += copied
                            session.fsync(output)
                        }
                    }
                }
                if (total > 0) {
                    installLog.emitProgress(AppInstallProgress(id, total, total))
                }

                val intent = Intent(context, InstallReceiver::class.java).apply {
                    action = INSTALL_ACTION
                    putExtra(INSTALL_ID, id)
                }

                acquireInstall(id)
                mutexLocked = true
                installLog.markActiveSessionInstall(id)
                val pending = PendingIntent.getBroadcast(
                    context,
                    id,
                    intent,
                    FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                session.commit(pending.intentSender)
                committed = true
            }
        } catch (error: Throwable) {
            if (mutexLocked) finish(id)
            if (!committed) runCatching { packageInstaller.abandonSession(sessionId) }
            throw error
        }
    }

    fun rootInstall(file: File, expectedPackageName: String? = null): Boolean =
        rootInstallDetailed(file, expectedPackageName).first

    fun rootInstallDetailed(file: File, expectedPackageName: String? = null): Pair<Boolean, String> {
        if (!expectedPackageName.isNullOrBlank()) {
            validateApkFile(file, expectedPackageName, null, 0L)
        }
        val res = Shell.cmd("pm install $PRIVILEGED_INSTALL_FLAGS ${file.absolutePath}").exec()
        file.delete()
        val out = res.out.joinToString("\n").trim()
        val err = res.err.joinToString("\n").trim()
        val output = (res.err + res.out).joinToString(" ").trim()
        return isPrivilegedInstallSuccess(res.code, out, err) to output
    }

    fun validateApkFile(
        file: File,
        expectedPackageName: String?,
        expectedSha256: String?,
        expectedSize: Long
    ) {
        if (expectedSize > 0L && file.length() != expectedSize) {
            throw Exception("APK size mismatch: expected $expectedSize, got ${file.length()}")
        }

        expectedSha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = file.sha256()
            if (!actual.equals(expected, ignoreCase = true)) {
                throw Exception("APK SHA-256 mismatch")
            }
        }

        validateApkStructure(file)

        expectedPackageName?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = archivePackageName(file) ?: throw Exception("Could not read APK package name")
            if (!actual.equals(expected, ignoreCase = true)) {
                throw Exception("APK package mismatch: expected $expected, got $actual")
            }
        }
    }

    fun validateSplitPackageFile(
        file: File,
        expectedPackageName: String?,
        expectedSha256: String? = null,
        expectedSize: Long = 0L
    ) {
        if (expectedSize > 0L && file.length() != expectedSize) {
            throw Exception("Split package size mismatch: expected $expectedSize, got ${file.length()}")
        }

        expectedSha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = file.sha256()
            if (!actual.equals(expected, ignoreCase = true)) {
                throw Exception("Split package SHA-256 mismatch")
            }
        }

        ZipFile(file).use { zip ->
            val apks = zip.apkEntries()
            if (apks.isEmpty()) {
                throw Exception("Split package has no APK entries")
            }

            val expected = expectedPackageName?.takeIf { it.isNotBlank() }
            var packageVerified = expected == null
            for (entry in apks) {
                val tempApk = File(context.cacheDir, "${randomUUID()}.apk")
                try {
                    zip.getInputStream(entry).use { input ->
                        tempApk.outputStream().use { output -> input.copyTo(output) }
                    }
                    validateApkFile(tempApk, null, null, 0L)
                    archivePackageName(tempApk)?.let { actual ->
                        if (expected != null && !actual.equals(expected, ignoreCase = true)) {
                            throw Exception("APK package mismatch: expected $expected, got $actual")
                        }
                        packageVerified = true
                    }
                } finally {
                    tempApk.delete()
                }
            }
            if (!packageVerified) {
                throw Exception("Could not read base APK package name")
            }
        }
    }

    fun isSplitPackageFile(file: File): Boolean = file.containsNestedApkFiles()

    private fun validateApkStructure(file: File) {
        if (!file.isFile || file.length() <= 0L) {
            throw Exception("APK file is empty")
        }
        try {
            ZipFile(file).use { zip ->
                if (zip.getEntry("AndroidManifest.xml") == null) {
                    throw Exception("Downloaded file is not a valid APK")
                }
            }
        } catch (error: Exception) {
            if (error.message == "Downloaded file is not a valid APK") throw error
            throw Exception("Downloaded file is not a valid APK", error)
        }
    }

    private fun validateApkSet(files: List<File>, expectedPackageName: String?) {
        if (files.isEmpty()) throw Exception("APK set is empty")
        val expected = expectedPackageName?.takeIf { it.isNotBlank() }
        var packageVerified = expected == null
        files.forEach { file ->
            validateApkFile(file, null, null, 0L)
            archivePackageName(file)?.let { actual ->
                if (expected != null && !actual.equals(expected, ignoreCase = true)) {
                    throw Exception("APK package mismatch: expected $expected, got $actual")
                }
                packageVerified = true
            }
        }
        if (!packageVerified) throw Exception("Could not read base APK package name")
    }

    private fun archivePackageName(file: File): String? {
        val archive = if (file.extension.equals("apk", ignoreCase = true)) {
            file
        } else {
            File.createTempFile("package_", ".apk", context.cacheDir).also { temp ->
                file.inputStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(
                    archive.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )?.packageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(archive.absolutePath, 0)?.packageName
            }
        } finally {
            if (archive !== file) archive.delete()
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun ZipFile.apkEntries(): List<ZipEntry> {
        val apks = entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            .sortedWith(compareBy({ !it.name.substringAfterLast('/').equals("base.apk", ignoreCase = true) }, { it.name }))
            .toList()
        if (apks.size > MAX_SPLIT_APK_COUNT) {
            throw Exception("Split package contains too many APK entries: ${apks.size}")
        }
        var uncompressedSize = 0L
        apks.forEach { entry ->
            if (entry.size < 0L) {
                throw Exception("Split package contains an APK with unknown size")
            }
            if (entry.size > MAX_SPLIT_APK_BYTES - uncompressedSize) {
                throw Exception("Split package is too large after extraction")
            }
            uncompressedSize += entry.size
        }
        return apks
    }

    fun rootInstallApks(files: List<File>, expectedPackageName: String? = null): Boolean =
        rootInstallApksDetailed(files, expectedPackageName).first

    fun rootInstallApksDetailed(files: List<File>, expectedPackageName: String? = null): Pair<Boolean, String> {
        return try {
            validateApkSet(files, expectedPackageName)
            when (files.size) {
                0 -> false to "Empty APK list"
                1 -> {
                    val res = Shell.cmd("pm install $PRIVILEGED_INSTALL_FLAGS ${files[0].absolutePath}").exec()
                    val ok = isPrivilegedInstallSuccess(
                        res.code,
                        res.out.joinToString("\n"),
                        res.err.joinToString("\n")
                    )
                    ok to (res.err + res.out).joinToString(" ").trim()
                }
                else -> {
                    val createResult = Shell.cmd("pm install-create $PRIVILEGED_INSTALL_FLAGS").exec()
                    val sessionId = Regex("Success:.*?\\[?(\\d+)]?")
                        .find(createResult.out.joinToString(""))
                        ?.groupValues
                        ?.get(1)
                        ?: return false to (createResult.err + createResult.out).joinToString(" ").trim()
                    var committed = false
                    try {
                        files.forEachIndexed { index, apk ->
                            val writeResult = Shell.cmd(
                                "pm install-write -S ${apk.length()} $sessionId split_$index ${apk.absolutePath}"
                            ).exec()
                            if (!writeResult.isSuccess) {
                                return false to (writeResult.err + writeResult.out).joinToString(" ").trim()
                            }
                        }
                        val commitResult = Shell.cmd("pm install-commit $sessionId").exec()
                        committed = isPrivilegedInstallSuccess(
                            commitResult.code,
                            commitResult.out.joinToString("\n"),
                            commitResult.err.joinToString("\n")
                        )
                        committed to (commitResult.err + commitResult.out).joinToString(" ").trim()
                    } finally {
                        if (!committed) Shell.cmd("pm install-abandon $sessionId").exec()
                    }
                }
            }
        } finally {
            files.forEach { it.delete() }
        }
    }

    fun rootInstallXapk(file: File, expectedPackageName: String? = null): Boolean =
        rootInstallXapkDetailed(file, expectedPackageName).first

    fun rootInstallXapkDetailed(file: File, expectedPackageName: String? = null): Pair<Boolean, String> {
        val extracted = mutableListOf<File>()
        return try {
            ZipFile(file).use { zip ->
                val apks = zip.apkEntries()
                if (apks.isEmpty()) return@use false to "Split package has no APK entries"
                apks.forEachIndexed { index, entry ->
                    val tempApk = File(file.parentFile ?: context.cacheDir, "${randomUUID()}_$index.apk")
                    zip.getInputStream(entry).use { input ->
                        tempApk.outputStream().use { output -> input.copyTo(output) }
                    }
                    extracted += tempApk
                }
                rootInstallApksDetailed(extracted, expectedPackageName)
            }
        } finally {
            extracted.forEach { it.delete() }
            file.delete()
        }
    }

    private suspend fun acquireInstall(id: Int) {
        installSemaphore.acquire()
        installOwner.set(id.toLong())
    }

    fun finish(id: Int) {
        if (installOwner.compareAndSet(id.toLong(), NO_INSTALL_OWNER)) {
            installSemaphore.release()
        }
    }

    fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!context.packageManager.canRequestPackageInstalls()) {
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                val intent = Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            }
        }
        return true
    }

    suspend fun installXapk(id: Int, packageName: String, file: File, total: Long = file.length()) {
        ZipFile(file).use { zip ->
            val apks = zip.apkEntries().map { zip.getInputStream(it) }
            if (apks.isEmpty()) throw Exception("Split package has no APK entries")
            installList(id, packageName, apks, total)
        }
    }

    suspend fun playInstall(id: Int, packageName: String, streams: List<InputStream>, total: Long = 0L) =
        installList(id, packageName, streams, total)

    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun shizukuInstall(id: Int, packageName: String, stream: InputStream, total: Long = 0L) =
        shizukuInstall(id, packageName, listOf(stream), total)

    suspend fun shizukuInstall(id: Int, packageName: String, streams: List<InputStream>, total: Long = 0L) {
        if (streams.size == 1) {
            val stream = streams[0]
            val tempFile = File(context.cacheDir, "${randomUUID()}.apk")
            var bytes = 0L
            tempFile.outputStream().use { output ->
                stream.use { input ->
                    bytes = input.copyToAndNotify(output, id, installLog, total, 0L)
                        ?: throw CancellationException("Installation cancelled")
                    if (total > 0) {
                        installLog.emitProgress(AppInstallProgress(id, total, total))
                    }
                }
            }
            try {
                validateApkFile(tempFile, packageName, null, 0L)
                val result = shizukuExec("pm install $PRIVILEGED_INSTALL_FLAGS -S $bytes", tempFile)
                if (!result.isInstallSuccess) {
                    val message = result.message()
                    Log.e("SessionInstaller", "Shizuku install failed: $message")
                    throw Exception("Shizuku install failed: $message")
                }
                if (result.exitCode != 0) {
                    Log.w("SessionInstaller", "Shizuku install completed with warnings: ${result.message()}")
                }
                Log.i("SessionInstaller", "Shizuku install successful")
            } finally {
                tempFile.delete()
            }
        } else {
            val prepared = mutableListOf<Pair<File, Long>>()
            var sessionId: String? = null
            var committed = false
            try {
                var totalBytes = 0L
                streams.forEachIndexed { index, stream ->
                    val tempFile = File(context.cacheDir, "${randomUUID()}_$index.apk")
                    var bytes = 0L
                    tempFile.outputStream().use { output ->
                        stream.use { input ->
                            bytes = input.copyToAndNotify(output, id, installLog, total, totalBytes)
                                ?: throw CancellationException("Installation cancelled")
                            totalBytes += bytes
                        }
                    }
                    prepared += tempFile to bytes
                }
                validateApkSet(prepared.map { it.first }, packageName)

                val createResult = shizukuExec("pm install-create $PRIVILEGED_INSTALL_FLAGS")
                sessionId = Regex("Success:.*?\\[?(\\d+)]?").find(createResult.out)?.groupValues?.get(1)
                    ?: throw Exception("Failed to create install session: ${createResult.out}")

                prepared.forEachIndexed { index, (tempFile, bytes) ->
                    val result = shizukuExec("pm install-write -S $bytes $sessionId split_$index", tempFile)
                        if (!result.isSuccess) {
                            val message = result.message()
                            throw Exception("Shizuku install-write failed: $message")
                        }
                }
                if (total > 0) {
                    installLog.emitProgress(AppInstallProgress(id, total, total))
                }

                val commitResult = shizukuExec("pm install-commit $sessionId")
                if (!commitResult.isInstallSuccess) {
                    val message = commitResult.message()
                    throw Exception("Shizuku install-commit failed: $message")
                }
                if (commitResult.exitCode != 0) {
                    Log.w("SessionInstaller", "Shizuku install-commit completed with warnings: ${commitResult.message()}")
                }
                committed = true
            } finally {
                if (!committed && sessionId != null) shizukuExec("pm install-abandon $sessionId")
                prepared.forEach { (file, _) -> file.delete() }
            }
        }
    }

    private data class ShizukuResult(val isSuccess: Boolean, val out: String, val err: String, val exitCode: Int) {
        val isInstallSuccess: Boolean
            get() = isPrivilegedInstallSuccess(exitCode, out, err)

        fun message(): String {
            val text = when {
                err.isNotBlank() -> err
                out.isNotBlank() -> out
                else -> "exitCode=$exitCode"
            }
            return text.trim()
        }
    }

    private fun shizukuExec(command: String, inputFile: File? = null): ShizukuResult {
        var process: ShizukuRemoteProcess? = null
        return try {
            val binder = Shizuku.getBinder()
            val service = IShizukuService.Stub.asInterface(binder)
            val iRemoteProcess = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val constructor = ShizukuRemoteProcess::class.java.getDeclaredConstructor(
                Class.forName("moe.shizuku.server.IRemoteProcess")
            )
            constructor.isAccessible = true
            val proc = constructor.newInstance(iRemoteProcess)
            process = proc
            
            inputFile?.inputStream()?.use { input ->
                proc.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: proc.outputStream.close()

            var errText = ""
            val errThread = Thread {
                errText = runCatching { proc.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }.apply {
                isDaemon = true
                start()
            }
            val outText = proc.inputStream.bufferedReader().use { it.readText() }
            errThread.join(5_000L)
            val exitCode = proc.waitFor()

            ShizukuResult(exitCode == 0, outText, errText, exitCode)
        } catch (e: Exception) {
            Log.e("SessionInstaller", "Shizuku execution failed: $command", e)
            val errorMsg = e.message ?: "Unknown error"
            ShizukuResult(false, "", errorMsg, -1)
        } finally {
            runCatching { process?.destroy() }
        }
    }

    suspend fun shizukuInstallXapk(id: Int, packageName: String, file: File, total: Long = file.length()) {
        ZipFile(file).use { zip ->
            val apks = zip.apkEntries().map { zip.getInputStream(it) }
            if (apks.isEmpty()) throw Exception("Split package has no APK entries")
            shizukuInstall(id, packageName, apks, total)
        }
    }

}

internal const val PRIVILEGED_INSTALL_FLAGS = "-r -d"

/**
 * Android 15+ `pm install` / `pm install-commit` installs modded APKs with mismatched
 * `assets/art-profile/baseline.prof` checksums successfully, but logs a dexopt warning,
 * prints `Completed with warning(s)`, and exits with a non-zero status code.
 */
internal fun isPrivilegedInstallSuccess(exitCode: Int, stdout: String, stderr: String): Boolean {
    if (exitCode == 0) return true
    val combined = "$stdout\n$stderr"
    if (combined.contains("Failure [", ignoreCase = true)) return false
    return combined.contains("Completed with warning(s)", ignoreCase = true) ||
        combined.lines().any { it.trim().equals("Success", ignoreCase = true) }
}

internal fun File.containsNestedApkFiles(): Boolean = runCatching {
    ZipFile(this).use { zip ->
        // A regular APK may legitimately embed companion APK payloads (for example,
        // MT Manager includes FileTransfer). A root manifest identifies the outer
        // archive as the installable APK, so it must not be unpacked as an APK set.
        if (zip.getEntry("AndroidManifest.xml") != null) return@use false

        zip.entries().asSequence().any { entry ->
            !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
        }
    }
}.getOrDefault(false)
