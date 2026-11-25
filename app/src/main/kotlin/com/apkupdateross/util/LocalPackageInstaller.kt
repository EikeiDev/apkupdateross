package com.apkupdateross.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.HistoryEventType
import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.data.ui.InstallHistoryEntry
import com.apkupdateross.data.ui.classifyInstallFailure
import com.apkupdateross.prefs.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

data class LocalPackageInfo(
	val packageName: String,
	val appName: String,
	val versionName: String,
	val isSplit: Boolean
)

class LocalPackageInstaller(
	private val context: Context,
	private val prefs: Prefs,
	private val installer: SessionInstaller,
	private val installLog: InstallLog,
	private val snackBar: SnackBar,
	private val stringer: Stringer
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val nextId = AtomicInteger(-10_000)
	private val pendingSessions = ConcurrentHashMap<Int, Pair<LocalPackageInfo, File>>()

	init {
		installLog.status().onEach { status ->
			val pending = pendingSessions.remove(status.id) ?: return@onEach
			val (info, file) = pending
			installer.finish(status.id)
			file.delete()
			if (status.snack && status.reason != InstallFailureReason.CANCELLED) {
				recordAndNotify(info, status.success, status.reason, status.message)
			}
		}.launchIn(scope)
	}

	suspend fun installFromUri(uri: Uri) = withContext(Dispatchers.IO) {
		val displayName = resolveDisplayName(uri)
		val ext = displayName.substringAfterLast('.', "").lowercase()
			.takeIf { it in setOf("apk", "xapk", "apks", "apkm", "zip") }
			?: "apk"
		val tempFile = File(context.cacheDir, "local_${UUID.randomUUID()}.$ext")
		var keepFileForSession = false
		try {
			val input = context.contentResolver.openInputStream(uri)
				?: throw IllegalStateException("Cannot open file")
			input.use { stream ->
				tempFile.outputStream().use { out -> stream.copyTo(out) }
			}
			val info = inspectFile(tempFile, displayName)
			val label = info.appName.ifBlank { info.packageName }
			val mode = prefs.installMode.get()
			val id = nextId.decrementAndGet()

			when (mode) {
				1 -> {
					val (ok, output) = if (info.isSplit) {
						installer.rootInstallXapkDetailed(tempFile, info.packageName)
					} else {
						installer.rootInstallDetailed(tempFile, info.packageName)
					}
					val reason = if (ok) InstallFailureReason.NONE else classifyInstallFailure(-1, output)
					recordAndNotify(info, ok, reason, output)
				}
				2 -> {
					if (!installer.isShizukuAvailable()) {
						snackBar.snackBar(scope, TextSnack(stringer.get(R.string.shizuku_not_running)))
						return@withContext
					}
					if (info.isSplit) {
						installer.shizukuInstallXapk(id, info.packageName, tempFile)
					} else {
						tempFile.inputStream().use { stream ->
							installer.shizukuInstall(id, info.packageName, stream, tempFile.length())
						}
					}
					recordAndNotify(info, true)
				}
				else -> {
					if (!installer.checkPermission()) return@withContext
					pendingSessions[id] = info to tempFile
					try {
						if (info.isSplit) {
							installer.installXapk(id, info.packageName, tempFile)
						} else {
							tempFile.inputStream().use { stream ->
								installer.install(id, info.packageName, stream, tempFile.length())
							}
						}
						keepFileForSession = true
					} catch (e: Throwable) {
						pendingSessions.remove(id)
						throw e
					}
				}
			}
		} catch (e: Exception) {
			Log.e("LocalPackageInstaller", "Failed to install local package: $uri", e)
			val reason = classifyInstallFailure(-1, e.message)
			val detail = localizedFailureReason(reason, e.message ?: displayName)
			snackBar.snackBar(scope, TextSnack(stringer.get(R.string.local_install_failed, detail.ifBlank { displayName })))
		} finally {
			if (!keepFileForSession) tempFile.delete()
		}
	}

	private fun localizedFailureReason(reason: InstallFailureReason, rawMessage: String = ""): String = when (reason) {
		InstallFailureReason.SIGNATURE_CONFLICT -> stringer.get(R.string.install_error_signature_conflict)
		InstallFailureReason.STORAGE_FULL -> stringer.get(R.string.install_error_storage_full)
		InstallFailureReason.INCOMPATIBLE -> stringer.get(R.string.install_error_incompatible)
		InstallFailureReason.DOWNGRADE -> stringer.get(R.string.install_error_downgrade)
		InstallFailureReason.PARSE_ERROR -> stringer.get(R.string.install_error_parse)
		InstallFailureReason.CANCELLED -> ""
		InstallFailureReason.NONE,
		InstallFailureReason.UNKNOWN -> rawMessage.trim()
	}

	private fun recordAndNotify(
		info: LocalPackageInfo,
		success: Boolean,
		reason: InstallFailureReason = InstallFailureReason.NONE,
		rawMessage: String = ""
	) {
		val label = info.appName.ifBlank { info.packageName }
		val detail = if (success) "" else localizedFailureReason(reason, rawMessage)
		val base = stringer.get(if (success) R.string.install_success else R.string.install_failure, label)
		val snackMessage = if (detail.isNotBlank()) "$base — $detail" else base
		snackBar.snackBar(scope, TextSnack(snackMessage))
		prefs.appendInstallHistory(
			InstallHistoryEntry(
				packageName = info.packageName,
				appName = label,
				version = info.versionName,
				sourceName = "Local",
				eventType = HistoryEventType.FOREGROUND_INSTALL,
				success = success,
				details = detail
			)
		)
	}

	private fun inspectFile(file: File, fallbackName: String): LocalPackageInfo {
		val isSplit = installer.isSplitPackageFile(file)
		if (isSplit) {
			installer.validateSplitPackageFile(file, expectedPackageName = null)
			ZipFile(file).use { zip ->
				val entries = zip.entries().asSequence()
					.filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
					.sortedWith(compareBy({ !it.name.substringAfterLast('/').equals("base.apk", ignoreCase = true) }, { it.name }))
					.toList()
				for (entry in entries) {
					val tempApk = File(context.cacheDir, "inspect_${UUID.randomUUID()}.apk")
					try {
						zip.getInputStream(entry).use { input ->
							tempApk.outputStream().use { out -> input.copyTo(out) }
						}
						readArchiveInfo(tempApk, fallbackName, isSplit = true)?.let { return it }
					} finally {
						tempApk.delete()
					}
				}
			}
			throw IllegalArgumentException("Could not read package metadata from archive")
		} else {
			installer.validateApkFile(file, expectedPackageName = null, expectedSha256 = null, expectedSize = 0L)
			return readArchiveInfo(file, fallbackName, isSplit = false)
				?: throw IllegalArgumentException("Invalid APK archive")
		}
	}

	private fun readArchiveInfo(apkFile: File, fallbackName: String, isSplit: Boolean): LocalPackageInfo? {
		val archive = if (apkFile.extension.equals("apk", ignoreCase = true)) {
			apkFile
		} else {
			File.createTempFile("inspect_", ".apk", context.cacheDir).also { temp ->
				apkFile.inputStream().use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
			}
		}
		return try {
			val pm = context.packageManager
			val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
				pm.getPackageArchiveInfo(archive.absolutePath, PackageManager.PackageInfoFlags.of(0))
			} else {
				@Suppress("DEPRECATION")
				pm.getPackageArchiveInfo(archive.absolutePath, 0)
			} ?: return null
			val appInfo = pkgInfo.applicationInfo?.apply {
				sourceDir = archive.absolutePath
				publicSourceDir = archive.absolutePath
			}
			val label = runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull()
				?.takeIf { it.isNotBlank() }
				?: fallbackName.substringBeforeLast('.')
			LocalPackageInfo(
				packageName = pkgInfo.packageName,
				appName = label,
				versionName = pkgInfo.versionName.orEmpty(),
				isSplit = isSplit
			)
		} finally {
			if (archive !== apkFile) archive.delete()
		}
	}

	private fun resolveDisplayName(uri: Uri): String {
		runCatching {
			context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if (idx >= 0) return cursor.getString(idx).orEmpty()
				}
			}
		}
		return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "package.apk" }
	}
}
