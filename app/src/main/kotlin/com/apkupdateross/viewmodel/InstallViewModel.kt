package com.apkupdateross.viewmodel

import android.util.Log
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdateross.R
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.DownloadStorage
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.apkupdateross.util.copyToAndNotify
import java.util.concurrent.ConcurrentHashMap


abstract class InstallViewModel(
    protected val downloader: Downloader,
    private val installer: SessionInstaller,
    private val prefs: Prefs,
    protected val snackBar: SnackBar,
    protected val stringer: Stringer,
    protected val installLog: InstallLog,
    protected val downloadStorage: DownloadStorage
): ViewModel() {
    protected val activeJobs = ConcurrentHashMap<Int, Job>()

    protected fun trackJob(id: Int, job: Job): Job {
        activeJobs[id]?.cancel()
        activeJobs[id] = job
        job.invokeOnCompletion { activeJobs.remove(id, job) }
        return job
    }

    fun install(update: AppUpdate, uriHandler: UriHandler) {
        when (update.source) {
            ApkMirrorSource -> uriHandler.openUri((update.link as Link.Url).link)
            else -> when (prefs.installMode.get()) {
                2 -> downloadAndShizukuInstall(update)
                1 -> downloadAndRootInstall(update)
                else -> downloadAndInstall(update)
            }
        }
    }

    protected fun subscribeToInstallStatus(updates: List<AppUpdate>) = installLog.status().onEach {
        runCatching {
            sendInstallSnack(updates, it)
            if (it.success) {
                finishInstall(it.id).join()
            } else {
                installLog.emitProgress(AppInstallProgress(it.id, 0L))
                cancelInstall(it.id).join()
            }
            downloader.cleanup(it.id)
        }.onFailure {
            Log.e("InstallViewModel", "Error in subscribeToInstallStatus", it)
        }
    }.launchIn(viewModelScope)

    protected fun subscribeToInstallProgress(
        block: (AppInstallProgress) -> Unit
    ) = installLog.progress().onEach {
        block(it)
    }.launchIn(viewModelScope)

    protected fun downloadAndRootInstall(id: Int, link: Link) = runCatching {
        when (link) {
            is Link.Url -> {
                if (installer.rootInstall(downloader.download(id, link.link))) {
                    finishInstall(id)
                } else {
                    cancelInstall(id)
                }
                downloader.cleanup(id)
            }
            is Link.Xapk -> {
                if (installer.rootInstallXapk(downloader.download(id, link.link))) {
                    finishInstall(id)
                } else {
                    cancelInstall(id)
                }
                downloader.cleanup(id)
            }
            else -> {
                snackBar.snackBar(
                    viewModelScope,
                    TextSnack(stringer.get(R.string.root_install_not_supported))
                )
                cancelInstall(id)
            }
        }
    }.getOrElse {
        Log.e("InstallViewModel", "Error in downloadAndRootInstall.", it)
        cancelInstall(id)
        downloader.cleanup(id)
    }

    protected suspend fun downloadAndShizukuInstall(id: Int, packageName: String, link: Link) = runCatching {
        if (!installer.isShizukuAvailable()) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.shizuku_not_running)))
            cancelInstall(id)
            return@runCatching
        }
        when (link) {
            Link.Empty -> Log.e("InstallViewModel", "downloadAndShizukuInstall: Unsupported.")
            is Link.Play -> {
                val files = link.getInstallFiles()
                val total = files.sumOf { it.size }
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                val streams = files.map { downloader.downloadStream(id, it.url) ?: throw Exception("Failed to open stream for ${it.name}") }
                installer.shizukuInstall(id, packageName, streams, total)
                finishInstall(id)
            }
            is Link.Url -> {
                val result = downloader.downloadWithSize(id, link.link) ?: throw Exception("Download failed")
                val total = if (link.size > 0) link.size else result.contentLength
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                installer.shizukuInstall(id, packageName, result.stream, total)
                finishInstall(id)
            }
            is Link.Xapk -> {
                val result = downloader.downloadWithSize(id, link.link) ?: throw Exception("Download failed")
                installLog.emitProgress(AppInstallProgress(id, 0L, result.contentLength))
                installer.shizukuInstallXapk(id, packageName, result.stream, result.contentLength)
                finishInstall(id)
            }
        }
        downloader.cleanup(id)
    }.getOrElse {
        Log.e("InstallViewModel", "Error in downloadAndShizukuInstall.", it)
        snackBar.snackBar(viewModelScope, TextSnack("${stringer.get(R.string.download_failure_cant_download)}: ${it.message}"))
        cancelInstall(id)
        downloader.cleanup(id)
    }

    protected suspend fun downloadAndInstall(id: Int, packageName: String, link: Link) = runCatching {
        when (link) {
            Link.Empty -> { Log.e("InstallViewModel", "downloadAndInstall: Unsupported.")}
            is Link.Play -> {
                val files = link.getInstallFiles()
                val total = files.sumOf { it.size }
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                val streams = files.map { downloader.downloadStream(id, it.url) ?: throw Exception("Failed to open stream for ${it.name}") }
                installer.playInstall(id, packageName, streams, total)
            }
            is Link.Url -> {
                val result = downloader.downloadWithSize(id, link.link) ?: throw Exception("Download failed")
                val total = if (link.size > 0) link.size else result.contentLength
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                installer.install(id, packageName, result.stream, total)
            }
            is Link.Xapk -> {
                val result = downloader.downloadWithSize(id, link.link) ?: throw Exception("Download failed")
                installLog.emitProgress(AppInstallProgress(id, 0L, result.contentLength))
                installer.installXapk(id, packageName, result.stream, result.contentLength)
            }
        }
    }.getOrElse {
        Log.e("InstallViewModel", "Error in downloadAndInstall.", it)
        snackBar.snackBar(viewModelScope, TextSnack("${stringer.get(R.string.download_failure_cant_download)}: ${it.message}"))
        cancelInstall(id)
        downloader.cleanup(id) // Missing in original snippet, but should be here.
    }

    private fun sendInstallSnack(updates: List<AppUpdate>, log: AppInstallStatus) {
        if (log.snack) {
            updates.find { log.id == it.id }?.let { app ->
                val message = if (log.success) R.string.install_success else R.string.install_failure
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name)))
            }
        }
    }

    fun openSourcePage(update: AppUpdate, uriHandler: androidx.compose.ui.platform.UriHandler) {
        val target = update.releaseUrl.ifBlank { update.sourceUrl.ifBlank { (update.link as? Link.Url)?.link.orEmpty() } }
        if (target.isNotBlank()) {
            uriHandler.openUri(target)
        } else {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.link_not_available)))
        }
    }

    protected suspend fun saveStream(update: AppUpdate, url: String) {
        val result = downloader.downloadWithSize(update.id, url)
        if (result == null) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure_cant_download)))
            return
        }
        val ext = if (url.lowercase().contains(".xapk")) "xapk" else "apk"
        val mime = if (ext == "xapk") "application/vnd.android.xapk" else "application/vnd.android.package-archive"
        val fileName = "${update.packageName}-${update.version}.$ext"
        val ok = runCatching { downloadStorage.save(fileName, mime, result.stream, update.id, installLog, result.contentLength) }.getOrElse { false }
        if (ok) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
        } else {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
        }
    }

    protected suspend fun savePlayFiles(update: AppUpdate, link: Link.Play) {
        val files = link.getInstallFiles()
        if (files.isEmpty()) return

        if (files.size == 1) {
            val file = files.first()
            val result = downloader.downloadWithSize(update.id, file.url) ?: return
            val fileName = "${update.name}_${update.version}.apk"
            if (downloadStorage.save(fileName, "application/vnd.android.package-archive", result.stream, update.id, installLog, result.contentLength)) {
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
            } else {
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
            }
        } else {
            val totalSize = files.sumOf { it.size }
            val pos = java.io.PipedOutputStream()
            val pis = java.io.PipedInputStream(pos)
            
            val job = viewModelScope.launch(Dispatchers.IO) {
                try {
                    java.util.zip.ZipOutputStream(pos).use { zos ->
                        var currentOffset = 0L
                        for (file in files) {
                            if (!isActive) break
                            zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                            downloader.downloadStream(update.id, file.url)?.use { input ->
                                input.copyToAndNotify(zos, update.id, installLog, totalSize, currentOffset) ?: throw Exception("Cancelled")
                            }
                            zos.closeEntry()
                            currentOffset += file.size
                        }
                        if (totalSize > 0) {
                            installLog.emitProgress(AppInstallProgress(update.id, totalSize, totalSize))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InstallViewModel", "Error while zipping Split-APKs", e)
                } finally {
                    runCatching { pos.close() }
                }
            }

            val fileName = "${update.name}_${update.version}.apks"
            val success = try {
                downloadStorage.save(fileName, "application/octet-stream", pis, update.id, null, totalSize)
            } finally {
                job.cancel()
                runCatching { pis.close() }
            }

            if (success) {
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
            } else {
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
            }
        }
    }

    fun cancel(update: AppUpdate) = cancelInstall(update.id)

    protected fun cancelDownload(id: Int) {
        downloader.cancel(id)
    }

    protected fun clearDownload(id: Int) {
        downloader.clear(id)
    }

    abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun downloadAndRootInstall(update: AppUpdate): Job
    protected abstract fun downloadAndShizukuInstall(update: AppUpdate): Job
}
