package com.apkupdateross.util

import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.classifyInstallFailure
import com.apkupdateross.data.ui.isSplitPackageDownloadUrl
import com.apkupdateross.data.ui.resolvedUrl
import com.apkupdateross.data.ui.totalSize
import com.apkupdateross.data.ui.validationPackageName
import com.apkupdateross.data.ui.validationSize
import com.aurora.gplayapi.model.AppFile
import kotlinx.coroutines.CancellationException

enum class InstallMethod {
	SESSION,
	ROOT,
	SHIZUKU;

	companion object {
		fun fromMode(mode: Int): InstallMethod = when (mode) {
			1 -> ROOT
			2 -> SHIZUKU
			else -> SESSION
		}
	}
}

sealed interface InstallExecutionResult {
	data object Dispatched : InstallExecutionResult
	data object Succeeded : InstallExecutionResult
	data class Failed(
		val reason: InstallFailureReason = InstallFailureReason.UNKNOWN,
		val details: String = ""
	) : InstallExecutionResult
	data class Unavailable(val message: String) : InstallExecutionResult
}

/**
 * Coordinates download, verification, and installation across Session, Root, and Shizuku modes
 * so foreground ViewModels and background Workers share one implementation.
 */
class AppInstallExecutor(
	private val downloader: Downloader,
	private val installer: SessionInstaller,
	private val installLog: InstallLog,
	private val stringer: Stringer
) {

	suspend fun execute(
		id: Int,
		packageName: String,
		link: Link,
		method: InstallMethod,
		emitProgress: Boolean = true
	): InstallExecutionResult {
		if (method == InstallMethod.SHIZUKU && !installer.isShizukuAvailable()) {
			return InstallExecutionResult.Unavailable(stringer.get(R.string.shizuku_not_running))
		}

		return try {
			when (link) {
				Link.Empty -> InstallExecutionResult.Unavailable(
					if (method == InstallMethod.ROOT) {
						stringer.get(R.string.root_install_not_supported)
					} else {
						stringer.get(R.string.download_not_supported_for_source)
					}
				)
				is Link.BrowserDownload -> InstallExecutionResult.Unavailable(
					if (method == InstallMethod.ROOT) {
						stringer.get(R.string.root_install_not_supported)
					} else {
						stringer.get(R.string.browser_download_required)
					}
				)
				is Link.Play -> executePlayInstall(id, packageName, link, method, emitProgress)
				is Link.Url -> executeUrlInstall(id, packageName, link, method, emitProgress)
				is Link.Xapk -> executeXapkInstall(id, packageName, link, method, emitProgress)
			}
		} catch (cancelled: CancellationException) {
			throw cancelled
		} finally {
			downloader.cleanup(id)
		}
	}

	fun downloadPlayFiles(
		id: Int,
		packageName: String,
		files: List<AppFile>,
		emitProgress: Boolean = true
	): List<Downloader.FileDownloadResult> {
		val baseCount = files.count { it.type == AppFile.Type.BASE }
		if (baseCount != 1) throw Exception("Google Play APK set must contain exactly one base APK")
		val total = files.sumOf { it.size.coerceAtLeast(0L) }
		var offset = 0L
		return files.map { file ->
			val result = downloader.downloadFileWithSize(
				id = id,
				url = file.url,
				onProgress = if (emitProgress) {
					progressCallback(id, expectedTotal = file.size, offset = offset, aggregateTotal = total)
				} else {
					null
				}
			) ?: throw Exception("Failed to download ${file.name}")

			val expectedSize = file.size.coerceAtLeast(0L)
			val expectedPackage = packageName.takeIf { it.isNotBlank() && file.type == AppFile.Type.BASE }
			val expectedSha256 = file.sha256.takeIf { it.isNotBlank() }
			installer.validateApkFile(result.file, expectedPackage, expectedSha256, expectedSize)
			offset += result.file.length()
			result
		}
	}

	fun progressCallback(
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

	private suspend fun executePlayInstall(
		id: Int,
		packageName: String,
		link: Link.Play,
		method: InstallMethod,
		emitProgress: Boolean
	): InstallExecutionResult {
		val files = link.getInstallFiles()
		val total = files.sumOf { it.size.coerceAtLeast(0L) }
		if (emitProgress && method != InstallMethod.ROOT) {
			installLog.emitProgress(AppInstallProgress(id, 0L, total))
		}
		val results = downloadPlayFiles(id, packageName, files, emitProgress)
		return when (method) {
			InstallMethod.ROOT -> {
				val (ok, output) = installer.rootInstallApksDetailed(results.map { it.file }, packageName)
				if (ok) InstallExecutionResult.Succeeded
				else InstallExecutionResult.Failed(classifyInstallFailure(-1, output), output)
			}
			InstallMethod.SHIZUKU -> {
				installer.shizukuInstall(id, packageName, results.map { it.file.inputStream() }, total)
				InstallExecutionResult.Succeeded
			}
			InstallMethod.SESSION -> {
				installer.playInstall(id, packageName, results.map { it.file.inputStream() }, total)
				InstallExecutionResult.Dispatched
			}
		}
	}

	private suspend fun executeUrlInstall(
		id: Int,
		packageName: String,
		link: Link.Url,
		method: InstallMethod,
		emitProgress: Boolean
	): InstallExecutionResult {
		val url = link.resolvedUrl()
		val result = downloader.downloadFileWithSize(
			id = id,
			url = url,
			onProgress = if (emitProgress) progressCallback(id, link.size) else null,
			requestHeaders = link.requestHeaders
		) ?: throw Exception("Download failed")

		val isSplitPackage = link.isSplitPackage(url, result.url) || installer.isSplitPackageFile(result.file)
		val validationPackage = link.validationPackageName(packageName) ?: packageName
		val validationSize = link.validationSize()
		if (isSplitPackage) {
			installer.validateSplitPackageFile(result.file, validationPackage, link.sha256, validationSize)
		} else {
			installer.validateApkFile(result.file, validationPackage, link.sha256, validationSize)
		}

		val total = link.totalSize(result.contentLength, result.file.length())
		if (emitProgress && method != InstallMethod.ROOT) {
			installLog.emitProgress(AppInstallProgress(id, 0L, total))
		}

		return when (method) {
			InstallMethod.ROOT -> {
				val (ok, output) = if (isSplitPackage) {
					installer.rootInstallXapkDetailed(result.file, packageName)
				} else {
					installer.rootInstallDetailed(result.file, packageName)
				}
				if (ok) InstallExecutionResult.Succeeded
				else InstallExecutionResult.Failed(classifyInstallFailure(-1, output), output)
			}
			InstallMethod.SHIZUKU -> {
				if (isSplitPackage) {
					installer.shizukuInstallXapk(id, packageName, result.file, total)
				} else {
					installer.shizukuInstall(id, packageName, result.file.inputStream(), total)
				}
				InstallExecutionResult.Succeeded
			}
			InstallMethod.SESSION -> {
				if (isSplitPackage) {
					installer.installXapk(id, packageName, result.file, total)
				} else {
					installer.install(id, packageName, result.file.inputStream(), total)
				}
				InstallExecutionResult.Dispatched
			}
		}
	}

	private suspend fun executeXapkInstall(
		id: Int,
		packageName: String,
		link: Link.Xapk,
		method: InstallMethod,
		emitProgress: Boolean
	): InstallExecutionResult {
		val result = downloader.downloadFileWithSize(
			id = id,
			url = link.link,
			onProgress = if (emitProgress) progressCallback(id) else null
		) ?: throw Exception("Download failed")

		installer.validateSplitPackageFile(result.file, packageName)
		val total = result.file.length()
		if (emitProgress && method != InstallMethod.ROOT) {
			installLog.emitProgress(AppInstallProgress(id, 0L, total))
		}

		return when (method) {
			InstallMethod.ROOT -> {
				val (ok, output) = installer.rootInstallXapkDetailed(result.file, packageName)
				if (ok) InstallExecutionResult.Succeeded
				else InstallExecutionResult.Failed(classifyInstallFailure(-1, output), output)
			}
			InstallMethod.SHIZUKU -> {
				installer.shizukuInstallXapk(id, packageName, result.file, total)
				InstallExecutionResult.Succeeded
			}
			InstallMethod.SESSION -> {
				installer.installXapk(id, packageName, result.file, total)
				InstallExecutionResult.Dispatched
			}
		}
	}
}

private fun Link.Url.isSplitPackage(requestUrl: String, resolvedUrl: String): Boolean =
	link.isSplitPackageDownloadUrl() ||
		requestUrl.isSplitPackageDownloadUrl() ||
		resolvedUrl.isSplitPackageDownloadUrl()
