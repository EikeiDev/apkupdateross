package com.apkupdateross.viewmodel

import android.util.Log
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdateross.R
import com.apkupdateross.data.snack.ActionSnack
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.HistoryEventType
import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.data.ui.InstallHistoryEntry
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.classifyInstallFailure
import com.apkupdateross.data.ui.resolvedUrl
import com.apkupdateross.data.ui.validationSize
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.AppInstallExecutor
import com.apkupdateross.util.DownloadStorage
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallExecutionResult
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.InstallMethod
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.copyToAndNotify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


abstract class InstallViewModel(
    protected val downloader: Downloader,
    private val installer: SessionInstaller,
    private val installExecutor: AppInstallExecutor,
    private val prefs: Prefs,
    protected val snackBar: SnackBar,
    protected val stringer: Stringer,
    protected val installLog: InstallLog,
    protected val downloadStorage: DownloadStorage
): ViewModel() {
    protected val activeJobs = ConcurrentHashMap<Int, Job>()
    private val inFlightUpdates = ConcurrentHashMap<Int, AppUpdate>()

    protected fun trackJob(id: Int, job: Job): Job {
        activeJobs[id]?.cancel()
        activeJobs[id] = job
        job.invokeOnCompletion { activeJobs.remove(id, job) }
        return job
    }

    protected fun markInFlightUpdate(update: AppUpdate) {
        inFlightUpdates[update.id] = update
    }

    fun install(update: AppUpdate, uriHandler: UriHandler) {
        markInFlightUpdate(update)
        when (prefs.installMode.get()) {
            2 -> downloadAndShizukuInstall(update)
            1 -> downloadAndRootInstall(update)
            else -> downloadAndInstall(update)
        }
    }

    protected fun recordForegroundInstallResult(
        id: Int,
        success: Boolean,
        fallbackApp: AppUpdate? = null,
        details: String = ""
    ) {
        val update = inFlightUpdates.remove(id) ?: fallbackApp ?: return
        prefs.appendInstallHistory(
            InstallHistoryEntry(
                packageName = update.packageName,
                appName = update.name,
                version = update.version,
                sourceName = update.source.name,
                eventType = HistoryEventType.FOREGROUND_INSTALL,
                success = success,
                details = details
            )
        )
    }

    protected fun clearInFlightUpdate(id: Int) {
        inFlightUpdates.remove(id)
    }

	protected fun localizedFailureReason(reason: InstallFailureReason, rawMessage: String = ""): String = when (reason) {
		InstallFailureReason.SIGNATURE_CONFLICT -> stringer.get(R.string.install_error_signature_conflict)
		InstallFailureReason.STORAGE_FULL -> stringer.get(R.string.install_error_storage_full)
		InstallFailureReason.INCOMPATIBLE -> stringer.get(R.string.install_error_incompatible)
		InstallFailureReason.DOWNGRADE -> stringer.get(R.string.install_error_downgrade)
		InstallFailureReason.PARSE_ERROR -> stringer.get(R.string.install_error_parse)
		InstallFailureReason.CANCELLED -> ""
		InstallFailureReason.NONE,
		InstallFailureReason.UNKNOWN -> rawMessage.trim()
	}

	protected fun formatInstallFailureMessage(appName: String, reason: InstallFailureReason, rawMessage: String = ""): String {
		val base = stringer.get(R.string.install_failure, appName)
		val detail = localizedFailureReason(reason, rawMessage)
		return if (detail.isNotBlank()) "$base — $detail" else base
	}

    protected fun subscribeToInstallStatus(
        updatesProvider: () -> List<AppUpdate>
    ) = installLog.status().onEach {
        runCatching {
            val updates = updatesProvider()
            val app = updates.find { u -> u.id == it.id }
            if (it.snack && it.reason != InstallFailureReason.CANCELLED) {
                val details = if (it.success) "" else localizedFailureReason(it.reason, it.message)
                recordForegroundInstallResult(it.id, it.success, app, details)
            } else {
                clearInFlightUpdate(it.id)
            }
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

    protected open fun onSignatureConflictAction(app: AppUpdate): (() -> Unit)? = null

    protected suspend fun executeInstall(
        id: Int,
        packageName: String,
        link: Link,
        method: InstallMethod
    ) {
        try {
            when (val result = installExecutor.execute(id, packageName, link, method, emitProgress = true)) {
                InstallExecutionResult.Dispatched -> Unit
                InstallExecutionResult.Succeeded -> {
                    val app = inFlightUpdates[id]
                    recordForegroundInstallResult(id, success = true)
                    if (app != null) {
                        snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.install_success, app.name)))
                    }
                    finishInstall(id)
                }
                is InstallExecutionResult.Failed -> {
                    val app = inFlightUpdates[id]
                    val detail = localizedFailureReason(result.reason, result.details)
                    recordForegroundInstallResult(id, success = false, details = detail)
                    if (result.reason != InstallFailureReason.CANCELLED) {
                        showFailureSnack(app, packageName, result.reason, result.details)
                    }
                    cancelInstall(id)
                }
                is InstallExecutionResult.Unavailable -> {
                    recordForegroundInstallResult(id, success = false, details = result.message)
                    snackBar.snackBar(viewModelScope, TextSnack(result.message))
                    cancelInstall(id)
                }
            }
        } catch (cancelled: CancellationException) {
            clearInFlightUpdate(id)
            downloader.cleanup(id)
            throw cancelled
        } catch (error: Throwable) {
            Log.e("InstallViewModel", "Error in executeInstall ($method).", error)
            val app = inFlightUpdates[id]
            val reason = classifyInstallFailure(-1, error.message)
            val detail = localizedFailureReason(reason, error.message.orEmpty())
            recordForegroundInstallResult(id, success = false, details = detail)
            showFailureSnack(app, packageName, reason, error.message.orEmpty())
            cancelInstall(id)
        }
    }

    protected fun showFailureSnack(
        app: AppUpdate?,
        fallbackName: String,
        reason: InstallFailureReason,
        rawMessage: String
    ) {
        if (reason == InstallFailureReason.CANCELLED) return
        val name = app?.name ?: fallbackName
        val message = formatInstallFailureMessage(name, reason, rawMessage)
        val hideAction = if (reason == InstallFailureReason.SIGNATURE_CONFLICT && app != null) {
            onSignatureConflictAction(app)
        } else null
        if (hideAction != null) {
            snackBar.snackBar(
                viewModelScope,
                ActionSnack(
                    message = message,
                    actionLabel = stringer.get(R.string.card_action_hide),
                    action = hideAction
                )
            )
        } else {
            snackBar.snackBar(viewModelScope, TextSnack(message))
        }
    }

    protected suspend fun downloadAndRootInstall(id: Int, packageName: String, link: Link) =
        executeInstall(id, packageName, link, InstallMethod.ROOT)

    protected suspend fun downloadAndShizukuInstall(id: Int, packageName: String, link: Link) =
        executeInstall(id, packageName, link, InstallMethod.SHIZUKU)

    protected suspend fun downloadAndInstall(id: Int, packageName: String, link: Link) =
        executeInstall(id, packageName, link, InstallMethod.SESSION)

    private fun sendInstallSnack(updates: List<AppUpdate>, log: AppInstallStatus) {
        if (log.snack && log.reason != InstallFailureReason.CANCELLED) {
            updates.find { log.id == it.id }?.let { app ->
                if (log.success) {
                    snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.install_success, app.name)))
                } else {
                    showFailureSnack(app, app.name, log.reason, log.message)
                }
            }
        }
    }

    fun openSourcePage(update: AppUpdate, uriHandler: UriHandler) {
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
            expectedSize = link.validationSize(),
            requestHeaders = link.requestHeaders
        )
    }

    protected suspend fun saveStream(
        update: AppUpdate,
        url: String,
        forceSplitPackage: Boolean = false,
        expectedSha256: String? = null,
        expectedSize: Long = 0L,
        requestHeaders: Map<String, String> = emptyMap()
    ) {
        val result = downloader.downloadFileWithSize(
            update.id,
            url,
            installExecutor.progressCallback(update.id, expectedSize),
            requestHeaders
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
                installExecutor.progressCallback(update.id, file.size)
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
                            installExecutor.progressCallback(
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

    abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun downloadAndRootInstall(update: AppUpdate): Job
    protected abstract fun downloadAndShizukuInstall(update: AppUpdate): Job
}
