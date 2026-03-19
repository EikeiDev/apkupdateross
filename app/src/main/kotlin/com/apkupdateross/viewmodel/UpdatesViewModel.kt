package com.apkupdateross.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.UpdatesUiState
import com.apkupdateross.data.ui.indexOf
import com.apkupdateross.data.ui.removeId
import com.apkupdateross.data.ui.removePackageName
import com.apkupdateross.data.ui.setIsDownloading
import com.apkupdateross.data.ui.setIsInstalling
import com.apkupdateross.data.ui.setProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.util.Badger
import com.apkupdateross.util.DownloadStorage
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.copyToAndNotify
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.apkupdateross.data.snack.TextSnack


class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	private val prefs: Prefs,
	private val badger: Badger,
	downloader: Downloader,
	downloadStorage: DownloadStorage,
	snackBar: SnackBar,
	stringer: Stringer,
	installLog: InstallLog
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog, downloadStorage) {

	private val mutex = Mutex()
	private val _updates = MutableStateFlow<List<AppUpdate>>(emptyList())
	private val _filterQuery = MutableStateFlow("")
	private val _isInitialLoading = MutableStateFlow(true)
	private val _isRefreshing = MutableStateFlow(false)
	private val _selfUpdate = MutableStateFlow<AppUpdate?>(null)
	private var snoozedSelfUpdateVersionCode: Long? = null
	val useCompactView = prefs.useCompactViewFlow
	val portraitColumns = prefs.portraitColumnsFlow
	val landscapeColumns = prefs.landscapeColumnsFlow

	val state: StateFlow<UpdatesUiState> = combine(
		_updates,
		_filterQuery,
		prefs.ignoredVersionsFlow,
		_isInitialLoading
	) { all, query, ignoredIds, loading ->
		if (loading && all.isEmpty()) return@combine UpdatesUiState.Loading

		val filtered = all.filter { !ignoredIds.contains(it.id) }
		val grouped = groupUpdates(filtered)
		
		val result = if (query.isNotBlank()) {
			grouped.filter { g ->
				g.primary.name.contains(query, ignoreCase = true) ||
						g.packageName.contains(query, ignoreCase = true)
			}
		} else {
			grouped
		}
		
		UpdatesUiState.Success(result)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdatesUiState.Loading)

	init {
		_updates.onEach { updates ->
			updateSelfUpdateCandidate(updates)
			badger.changeUpdatesBadge(groupUpdates(updates).size.toString())
		}.launchIn(viewModelScope)

		// Static subscription for status (efficient)
		installLog.status().onEach { status ->
			val updates = _updates.value
			val app = updates.find { it.id == status.id }
			if (status.snack && app != null) {
				val message = if (status.success) R.string.install_success else R.string.install_failure
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name)))
			}
			if (status.success) {
				finishInstall(status.id).join()
			} else {
				installLog.emitProgress(AppInstallProgress(status.id, 0L))
				cancelInstall(status.id).join()
			}
			downloader.cleanup()
		}.launchIn(viewModelScope)

		subscribeToInstallProgress { progress ->
			_updates.value = _updates.value.toMutableList().setProgress(progress)
		}
	}

	fun isRefreshing(): StateFlow<Boolean> = _isRefreshing.asStateFlow()
	fun selfUpdate(): StateFlow<AppUpdate?> = _selfUpdate.asStateFlow()
	fun filterQuery(): StateFlow<String> = _filterQuery.asStateFlow()

	fun setFilterQuery(query: String) {
		_filterQuery.value = query
	}

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if (load) _isInitialLoading.value = true
		_isRefreshing.value = true
		try {
			updatesRepository.updates(force = load).collect { entries ->
				val current = _updates.value
				_updates.value = entries.map { entry ->
					current.find { it.id == entry.id }?.let { existing ->
						entry.copy(
							isInstalling = existing.isInstalling,
							isDownloading = existing.isDownloading,
							progress = existing.progress,
							total = if (existing.total > 0) existing.total else entry.total
						)
					} ?: entry
				}
				_isInitialLoading.value = false
			}
		} finally {
			_isRefreshing.value = false
			_isInitialLoading.value = false
		}
	}

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val allUpdates = _updates.value
		val reference = allUpdates.find { it.id == id } ?: return@launchWithMutex
		
		val ignored = prefs.ignoredVersions.get().toMutableList()
		val targetIds = allUpdates.filter { 
			it.packageName == reference.packageName && it.versionCode == reference.versionCode 
		}.map { it.id }

		if (ignored.contains(id)) {
			ignored.removeAll(targetIds.toSet())
		} else {
			ignored.addAll(targetIds)
		}
		prefs.setIgnoredVersions(ignored.distinct())
	}

	override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		activeJobs.remove(id)?.cancel()
		_updates.value = _updates.value.toMutableList()
			.setIsInstalling(id, false)
			.setIsDownloading(id, false)
		cancelDownload(id)
		installer.finish()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val current = _updates.value.toMutableList()
		val finishedUpdate = current.find { it.id == id }
		_updates.value = finishedUpdate?.let { current.removePackageName(it.packageName) } ?: current.removeId(id)
		clearDownload(id)
		installer.finish()
		finishedUpdate?.let {
			if (it.packageName == BuildConfig.APPLICATION_ID) {
				markSelfUpdateInstalled(it.versionCode)
			}
		}
	}

	override fun downloadAndRootInstall(update: AppUpdate): Job =
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			_updates.value = _updates.value.toMutableList().setIsInstalling(update.id, true)
			downloadAndRootInstall(update.id, update.link)
		})

	override fun downloadAndInstall(update: AppUpdate): Job =
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			if(installer.checkPermission()) {
				_updates.value = _updates.value.toMutableList().setIsInstalling(update.id, true)
				downloadAndInstall(update.id, update.packageName, update.link)
			}
		})

	override fun downloadAndShizukuInstall(update: AppUpdate): Job =
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			_updates.value = _updates.value.toMutableList().setIsInstalling(update.id, true)
			downloadAndShizukuInstall(update.id, update.packageName, update.link)
		})

	private fun groupUpdates(updates: List<AppUpdate>): List<GroupedAppUpdate> {
		val ignoredVersions = try { prefs.ignoredVersions.get() } catch (e: Exception) { emptyList() }
		return updates
			.filter { !ignoredVersions.contains(it.id) }
			.groupBy { it.packageName }
			.map { (packageName, list) ->
				GroupedAppUpdate(
					packageName = packageName,
					updates = list.sortedWith(
						compareByDescending<AppUpdate> { it.versionCode }
							.thenBy { it.source != PlaySource }
					)
				)
			}.sortedBy { it.primary.name }
	}

	fun snoozeSelfUpdate(versionCode: Long) {
		snoozedSelfUpdateVersionCode = versionCode
		if (_selfUpdate.value?.versionCode == versionCode) {
			_selfUpdate.value = null
		}
	}

	private fun markSelfUpdateInstalled(versionCode: Long) {
		prefs.lastSelfUpdateVersionCode.put(versionCode)
		snoozedSelfUpdateVersionCode = null
		if (_selfUpdate.value?.versionCode == versionCode) {
			_selfUpdate.value = null
		}
	}

	private fun updateSelfUpdateCandidate(updates: List<AppUpdate>) {
		val candidate = updates.firstOrNull { it.packageName == BuildConfig.APPLICATION_ID }
		if (candidate == null) {
			_selfUpdate.value = null
			snoozedSelfUpdateVersionCode = null
			return
		}
		if (snoozedSelfUpdateVersionCode != null && candidate.versionCode != snoozedSelfUpdateVersionCode) {
			snoozedSelfUpdateVersionCode = null
		}
		val lastInstalled = prefs.lastSelfUpdateVersionCode.get()
		val shouldShow = candidate.versionCode > lastInstalled && candidate.versionCode != snoozedSelfUpdateVersionCode
		_selfUpdate.value = if (shouldShow) candidate else null
	}

	fun downloadToStorage(update: AppUpdate) {
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			_updates.value = _updates.value.toMutableList().setIsDownloading(update.id, true)
			try {
				runCatching {
					when (val link = update.link) {
						is Link.Url -> saveStream(update, link.link)
						is Link.Xapk -> saveStream(update, link.link)
						is Link.Play -> savePlayFiles(update, link)
						else -> snackBar.snackBar(viewModelScope, TextSnack("Сохранение не поддерживается для этого источника"))
					}
				}.onFailure {
					if (it !is CancellationException) {
						Log.e("UpdatesViewModel", "Error in downloadToStorage", it)
					}
				}
			} finally {
				_updates.value = _updates.value.toMutableList().setIsDownloading(update.id, false)
				clearDownload(update.id)
			}
		})
	}

	private suspend fun savePlayFiles(update: AppUpdate, link: Link.Play) {
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
			val pos = PipedOutputStream()
			val pis = PipedInputStream(pos)
			
			viewModelScope.launch(Dispatchers.IO) {
				runCatching {
					ZipOutputStream(pos).use { zos ->
						var currentOffset = 0L
						for (file in files) {
							if (!isActive) break
							zos.putNextEntry(ZipEntry(file.name))
							downloader.downloadStream(update.id, file.url)?.use { input ->
								input.copyToAndNotify(zos, update.id, installLog, totalSize, currentOffset) ?: throw Exception("Cancelled")
							}
							zos.closeEntry()
							currentOffset += file.size
						}
					}
				}
			}
			val fileName = "${update.name}_${update.version}.apks"
			if (downloadStorage.save(fileName, "application/octet-stream", pis, update.id, null, totalSize)) {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
			} else {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
			}
		}
	}

	fun openSourcePage(update: AppUpdate, uriHandler: androidx.compose.ui.platform.UriHandler) {
		val target = update.releaseUrl.ifBlank { update.sourceUrl.ifBlank { (update.link as? Link.Url)?.link.orEmpty() } }
		if (target.isNotBlank()) {
			uriHandler.openUri(target)
		} else {
			snackBar.snackBar(viewModelScope, TextSnack("Ссылка недоступна"))
		}
	}

	private suspend fun saveStream(update: AppUpdate, url: String) {
		val result = downloader.downloadWithSize(update.id, url)
		if (result == null) {
			snackBar.snackBar(viewModelScope, TextSnack("Не удалось скачать файл"))
			return
		}
		val ext = when {
			url.lowercase().contains(".xapk") -> "xapk"
			else -> "apk"
		}
		val mime = if (ext == "xapk") "application/vnd.android.xapk" else "application/vnd.android.package-archive"
		val fileName = "${update.packageName}-${update.version}.$ext"
		val ok = runCatching { downloadStorage.save(fileName, mime, result.stream, update.id, installLog, result.contentLength) }.getOrElse { false }
		if (ok) {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_success, fileName)))
		} else if (!_updates.value.find { it.id == update.id }?.isDownloading!!) {
			// If not downloading anymore, it might be cancelled
		} else {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, fileName)))
		}
	}

}
