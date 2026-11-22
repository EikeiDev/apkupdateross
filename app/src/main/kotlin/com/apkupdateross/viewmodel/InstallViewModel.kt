package com.apkupdateross.viewmodel

import android.util.Log
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdateross.R
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.isSplitPackageDownloadUrl
import com.apkupdateross.data.ui.resolvedUrl
import com.apkupdateross.data.ui.totalSize
import com.apkupdateross.data.ui.validationPackageName
import com.apkupdateross.data.ui.validationSize
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.apkupdateross.util.copyToAndNotify
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


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
        when (prefs.installMode.get()) {
            2 -> downloadAndShizukuInstall(update)
            1 -> downloadAndRootInstall(update)
            else -> downloadAndInstall(update)
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

    protected suspend fun downloadAndRootInstall(id: Int, packageName: String, link: Link) = runCatching {
        when (link) {
            is Link.Play -> {
                val files = link.getInstallFiles()
                val results = downloadPlayFiles(id, packageName, files)
                val success = installer.rootInstallApks(results.map { it.file }, packageName)
                if (success) {
                    finishInstall(id)
                } else {
                    cancelInstall(id)
                }
                downloader.cleanup(id)
            }
            is Link.Url -> {
                val url = link.resolvedUrl()
                val result = downloader.downloadFileWithSize(
                    id,
                    url,
                    progressCallback(id, link.size)
                ) ?: throw Exception("Download failed")
                val isSplitPackage = link.isSplitPackage(url, result.url) || installer.isSplitPackageFile(result.file)
                if (isSplitPackage) {
                    installer.validateSplitPackageFile(
                        result.file,
                        link.validationPackageName(packageName) ?: packageName,
                        link.sha256,
                        link.validationSize()
                    )
                } else {
                    installer.validateApkFile(
                        result.file,
                        link.validationPackageName(packageName) ?: packageName,
                        link.sha256,
                        link.validationSize()
                    )
                }
                val success = if (isSplitPackage) {
                    installer.rootInstallXapk(result.file, packageName)
                } else {
                    installer.rootInstall(result.file, packageName)
                }
                if (success) {
                    finishInstall(id)
                } else {
                    cancelInstall(id)
                }
                downloader.cleanup(id)
            }
            is Link.Xapk -> {
                val result = downloader.downloadFileWithSize(
                    id,
                    link.link,
                    progressCallback(id)
                ) ?: throw Exception("Download failed")
                installer.validateSplitPackageFile(result.file, packageName)
                if (installer.rootInstallXapk(result.file, packageName)) {
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
            Link.Empty -> throw Exception(stringer.get(R.string.download_not_supported_for_source))
            is Link.BrowserDownload -> throw Exception(stringer.get(R.string.browser_download_required))
            is Link.Play -> {
                val files = link.getInstallFiles()
                val total = files.sumOf { it.size }
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                val streams = downloadPlayFiles(id, packageName, files).map { it.file.inputStream() }
                installer.shizukuInstall(id, packageName, streams, total)
                finishInstall(id)
            }
            is Link.Url -> {
                val url = link.resolvedUrl()
                val result = downloader.downloadFileWithSize(
                    id,
                    url,
                    progressCallback(id, link.size)
                ) ?: throw Exception("Download failed")
                val isSplitPackage = link.isSplitPackage(url, result.url) || installer.isSplitPackageFile(result.file)
                if (isSplitPackage) {
                    installer.validateSplitPackageFile(
                        result.file,
                        link.validationPackageName(packageName) ?: packageName,
                        link.sha256,
                        link.validationSize()
                    )
                } else {
                    installer.validateApkFile(
                        result.file,
                        link.validationPackageName(packageName) ?: packageName,
                        link.sha256,
                        link.validationSize()
                    )
                }
                val total = link.totalSize(result.contentLength, result.file.length())
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                if (isSplitPackage) {
                    installer.shizukuInstallXapk(id, packageName, result.file, total)
                } else {
                    installer.shizukuInstall(id, packageName, result.file.inputStream(), total)
                }
                finishInstall(id)
            }
            is Link.Xapk -> {
                val result = downloader.downloadFileWithSize(
                    id,
                    link.link,
                    progressCallback(id)
                ) ?: throw Exception("Download failed")
                installer.validateSplitPackageFile(result.file, packageName)
                installLog.emitProgress(AppInstallProgress(id, 0L, result.file.length()))
                installer.shizukuInstallXapk(id, packageName, result.file, result.file.length())
                finishInstall(id)
            }
        }
        downloader.cleanup(id)
    }.getOrElse { error ->
        if (error is CancellationException) {
            downloader.cleanup(id)
            throw error
        }
        Log.e("InstallViewModel", "Error in downloadAndShizukuInstall.", error)
        snackBar.snackBar(viewModelScope, TextSnack("${stringer.get(R.string.download_failure_cant_download)}: ${error.message}"))
        cancelInstall(id)
        downloader.cleanup(id)
    }

    protected suspend fun downloadAndInstall(id: Int, packageName: String, link: Link) = runCatching {
        when (link) {
            Link.Empty -> throw Exception(stringer.get(R.string.download_not_supported_for_source))
            is Link.BrowserDownload -> throw Exception(stringer.get(R.string.browser_download_required))
            is Link.Play -> {
                val files = link.getInstallFiles()
                val total = files.sumOf { it.size }
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                val streams = downloadPlayFiles(id, packageName, files).map { it.file.inputStream() }
                installer.playInstall(id, packageName, streams, total)
            }
            is Link.Url -> {
                val url = link.resolvedUrl()
                val result = downloader.downloadFileWithSize(
                    id,
                    url,
                    progressCallback(id, link.size)
                ) ?: throw Exception("Download failed")
                val isSplitPackage = link.isSplitPackage(url, result.url) || installer.isSplitPackageFile(result.file)
                if (isSplitPackage) {
                    installer.validateSplitPackageFile(
                        result.file,
                        link.validationPackageName(packageName) ?: packageName,
                        link.sha256,
                        link.validationSize()
                    )
                } else {
                    installer.validateApkFile(
                        result.file,
                        link.validationPackageName(packageName) ?: packageName,
                        link.sha256,
                        link.validationSize()
                    )
                }
                val total = link.totalSize(result.contentLength, result.file.length())
                installLog.emitProgress(AppInstallProgress(id, 0L, total))
                if (isSplitPackage) {
                    installer.installXapk(id, packageName, result.file, total)
                } else {
                    installer.install(id, packageName, result.file.inputStream(), total)
                }
            }
            is Link.Xapk -> {
                val result = downloader.downloadFileWithSize(
                    id,
                    link.link,
                    progressCallback(id)
                ) ?: throw Exception("Download failed")
                installer.validateSplitPackageFile(result.file, packageName)
                installLog.emitProgress(AppInstallProgress(id, 0L, result.file.length()))
                installer.installXapk(id, packageName, result.file, result.file.length())
            }
        }
    }.getOrElse { error ->
        if (error is CancellationException) {
            downloader.cleanup(id)
            throw error
        }
        Log.e("InstallViewModel", "Error in downloadAndInstall.", error)
        snackBar.snackBar(viewModelScope, TextSnack("${stringer.get(R.string.download_failure_cant_download)}: ${error.message}"))
        cancelInstall(id)
        downloader.cleanup(id)
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

    protected suspend fun saveStream(update: AppUpdate, link: Link.Url) {
        saveStream(
            update = update,
            url = link.resolvedUrl(),
            expectedSha256 = link.sha256,
            expectedSize = link.validationSize()
        )
    }

    protected suspend fun saveStream(
        update: AppUpdate,
        url: String,
        forceSplitPackage: Boolean = false,
        expectedSha256: String? = null,
        expectedSize: Long = 0L
    ) {
        val result = downloader.downloadFileWithSize(
            update.id,
            url,
            progressCallback(update.id, expectedSize)
        )
        if (result == null) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure_cant_download)))
            return
        }
        val lowerUrl = result.url.ifBlank { url }.lowercase()
        val detectedSplitPackage = forceSplitPackage || installer.isSplitPackageFile(result.file)
        val ext = when {
            forceSplitPackage -> "xapk"
            lowerUrl.contains(".xapk") -> "xapk"
            lowerUrl.contains(".apks") -> "apks"
            lowerUrl.contains(".apkm") -> "apkm"
            detectedSplitPackage -> "apks"
            else -> "apk"
        }
        val mime = when (ext) {
            "xapk" -> "application/vnd.android.xapk"
            "apks" -> "application/vnd.android.apks"
            "apkm" -> "application/octet-stream"
            else -> "application/vnd.android.package-archive"
        }
        val fileName = "${update.packageName}-${update.version}.$ext"
        val ok = runCatching {
            if (ext == "apk") {
                installer.validateApkFile(result.file, update.packageName, expectedSha256, expectedSize)
            } else {
                installer.validateSplitPackageFile(result.file, update.packageName, expectedSha256, expectedSize)
            }
            downloadStorage.save(
                fileName,
                mime,
                result.file.inputStream(),
                update.id,
                installLog,
                result.file.length()
            )
        }.getOrElse {
            Log.e("InstallViewModel", "Downloaded file validation or storage failed", it)
            false
        }
        if (ok) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
        } else {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
        }
    }

    protected suspend fun savePlayFiles(update: AppUpdate, link: Link.Play) {
        val files = link.getInstallFiles()
        if (files.isEmpty()) return
        if (files.count { it.type == com.aurora.gplayapi.data.models.File.FileType.BASE } != 1) {
            throw Exception("Google Play APK set must contain exactly one base APK")
        }

        if (files.size == 1) {
            val file = files.first()
            val result = downloader.downloadFileWithSize(
                update.id,
                file.url,
                progressCallback(update.id, file.size)
            ) ?: return
            val fileName = "${update.name}_${update.version}.apk"
            installer.validateApkFile(result.file, update.packageName, null, 0L)
            if (downloadStorage.save(
                    fileName,
                    "application/vnd.android.package-archive",
                    result.file.inputStream(),
                    update.id,
                    installLog,
                    result.file.length()
                )
            ) {
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
            } else {
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
            }
        } else {
            val totalSize = files.sumOf { it.size }
            val fileName = "${update.name}_${update.version}.apks"
            val archive = downloader.createTempFile(update.id, ".apks")
            val success = try {
                var currentOffset = 0L
                val entryNames = mutableSetOf<String>()
                ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                    files.forEachIndexed { index, file ->
                        currentCoroutineContext().ensureActive()
                        val preferredName = file.name.substringAfterLast('/').ifBlank { "split_$index.apk" }
                        val entryName = if (entryNames.add(preferredName)) {
                            preferredName
                        } else {
                            "${index}_$preferredName"
                        }
                        zip.putNextEntry(ZipEntry(entryName))
                        val result = downloader.downloadFileWithSize(
                            update.id,
                            file.url,
                            progressCallback(
                                id = update.id,
                                expectedTotal = file.size,
                                offset = currentOffset,
                                aggregateTotal = totalSize
                            )
                        )
                            ?: throw Exception("Failed to download ${file.name}")
                        installer.validateApkFile(
                            result.file,
                            update.packageName.takeIf {
                                file.type == com.aurora.gplayapi.data.models.File.FileType.BASE
                            },
                            null,
                            file.size.coerceAtLeast(0L)
                        )
                        val copied = result.file.inputStream().use { input ->
                            input.copyToAndNotify(zip, update.id, null, totalSize, currentOffset)
                                ?: throw CancellationException("Cancelled")
                        }
                        zip.closeEntry()
                        currentOffset += copied
                    }
                }
                installer.validateSplitPackageFile(archive, update.packageName)
                downloadStorage.save(
                    fileName,
                    "application/octet-stream",
                    archive.inputStream(),
                    update.id,
                    null,
                    archive.length()
                )
            } finally {
                archive.delete()
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

    private fun downloadPlayFiles(
        id: Int,
        packageName: String,
        files: List<com.aurora.gplayapi.data.models.File>
    ): List<Downloader.FileDownloadResult> {
        val baseCount = files.count { it.type == com.aurora.gplayapi.data.models.File.FileType.BASE }
        if (baseCount != 1) throw Exception("Google Play APK set must contain exactly one base APK")
        val total = files.sumOf { it.size.coerceAtLeast(0L) }
        var offset = 0L
        return files.map { file ->
            val result = downloader.downloadFileWithSize(
                id,
                file.url,
                progressCallback(
                    id = id,
                    expectedTotal = file.size,
                    offset = offset,
                    aggregateTotal = total
                )
            )
                ?: throw Exception("Failed to download ${file.name}")
            val expectedSize = file.size.coerceAtLeast(0L)
            val expectedPackage = packageName.takeIf {
                file.type == com.aurora.gplayapi.data.models.File.FileType.BASE
            }
            installer.validateApkFile(result.file, expectedPackage, null, expectedSize)
            offset += result.file.length()
            result
        }
    }

    private fun progressCallback(
        id: Int,
        expectedTotal: Long = 0L,
        offset: Long = 0L,
        aggregateTotal: Long = 0L
    ): (Long, Long) -> Unit = { downloaded, responseTotal ->
        val total = aggregateTotal.takeIf { it > 0L }
            ?: expectedTotal.takeIf { it > 0L }
            ?: responseTotal.coerceAtLeast(0L)
        installLog.emitProgress(AppInstallProgress(id, offset + downloaded, total))
    }

    abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun downloadAndRootInstall(update: AppUpdate): Job
    protected abstract fun downloadAndShizukuInstall(update: AppUpdate): Job
}

private fun Link.Url.isSplitPackage(requestUrl: String, resolvedUrl: String): Boolean =
    link.isSplitPackageDownloadUrl() ||
        requestUrl.isSplitPackageDownloadUrl() ||
        resolvedUrl.isSplitPackageDownloadUrl()
