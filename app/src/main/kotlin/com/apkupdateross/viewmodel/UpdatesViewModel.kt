package com.apkupdateross.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.UpdatesUiState
import com.apkupdateross.data.ui.indexOf
import com.apkupdateross.data.ui.removeId
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.Link


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
	private val _rawState = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
	private val _filterQuery = MutableStateFlow("")
	private val _isRefreshing = MutableStateFlow(false)
	private val _selfUpdate = MutableStateFlow<AppUpdate?>(null)
	private var snoozedSelfUpdateVersionCode: Long? = null
	val useCompactView = prefs.useCompactViewFlow
	val portraitColumns = prefs.portraitColumnsFlow
	val landscapeColumns = prefs.landscapeColumnsFlow

	val state: StateFlow<UpdatesUiState> = combine(_rawState, _filterQuery) { state, query ->
		if (state is UpdatesUiState.Success && query.isNotBlank()) {
			val filtered = state.updates.filter { grouped ->
				grouped.primary.name.contains(query, ignoreCase = true) ||
						grouped.packageName.contains(query, ignoreCase = true)
			}
			UpdatesUiState.Success(filtered)
		} else {
			state
		}
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdatesUiState.Loading)

	init {
		subscribeToInstallStatus(_rawState.value.updates().flatMap { it.updates })
		subscribeToInstallProgress { progress ->
			setSuccess(_rawState.value.mutableUpdates().setProgress(progress))
		}
	}

	fun isRefreshing(): StateFlow<Boolean> = _isRefreshing.asStateFlow()
	fun selfUpdate(): StateFlow<AppUpdate?> = _selfUpdate.asStateFlow()
	fun filterQuery(): StateFlow<String> = _filterQuery.asStateFlow()

	fun setFilterQuery(query: String) {
		_filterQuery.value = query
	}

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if (load) _rawState.value = UpdatesUiState.Loading
		_isRefreshing.value = true
		badger.changeUpdatesBadge("")
		try {
			updatesRepository.updates(force = load).collect { updates ->
				setSuccess(groupUpdates(updates))
			}
		} finally {
			_isRefreshing.value = false
		}
	}

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val ignored = prefs.ignoredVersions.get().toMutableList()
		if (ignored.contains(id)) ignored.remove(id) else ignored.add(id)
		prefs.ignoredVersions.put(ignored)
		setSuccess(_rawState.value.mutableUpdates())
	}

	override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		setSuccess(_rawState.value.mutableUpdates().setIsInstalling(id, false))
		cancelDownload(id)
		installer.finish()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val current = _rawState.value.mutableUpdates()
		val finishedUpdate = current.find { it.id == id }
		setSuccess(current.removeId(id))
		clearDownload(id)
		installer.finish()
		finishedUpdate?.let {
			if (it.packageName == BuildConfig.APPLICATION_ID) {
				markSelfUpdateInstalled(it.primary.versionCode)
			}
		}
	}

	override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		setSuccess(_rawState.value.mutableUpdates().setIsInstalling(update.id, true))
		downloadAndRootInstall(update.id, update.link)
	}

	override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		if(installer.checkPermission()) {
			setSuccess(_rawState.value.mutableUpdates().setIsInstalling(update.id, true))
			downloadAndInstall(update.id, update.packageName, update.link)
		}
	}

	override fun downloadAndShizukuInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		setSuccess(_rawState.value.mutableUpdates().setIsInstalling(update.id, true))
		downloadAndShizukuInstall(update.id, update.packageName, update.link)
	}

	private fun groupUpdates(updates: List<AppUpdate>): List<GroupedAppUpdate> {
		val ignoredVersions = prefs.ignoredVersions.get()
		return updates
			.filter { !ignoredVersions.contains(it.id) }
			.groupBy { it.packageName }
			.map { (packageName, list) ->
				GroupedAppUpdate(
					packageName = packageName,
					updates = list.sortedWith(
						compareByDescending<AppUpdate> { it.versionCode }
							.thenBy { it.source != PlaySource } // Prefer Play Store
					)
				)
			}.sortedBy { it.primary.name }
	}

	private fun setSuccess(updates: List<GroupedAppUpdate>) {
		_rawState.value = UpdatesUiState.Success(updates)
		badger.changeUpdatesBadge(updates.size.toString()) // Badge counts unique apps
		updateSelfUpdateCandidate(updates.flatMap { it.updates })
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

	fun downloadToStorage(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		setSuccess(_rawState.value.mutableUpdates().setIsDownloading(update.id, true))
		try {
			when (val link = update.link) {
				is Link.Url -> saveStream(update, link.link)
				is Link.Xapk -> saveStream(update, link.link)
				is Link.Play -> savePlayFiles(update, link)
				else -> snackBar.snackBar(viewModelScope, TextSnack("Сохранение не поддерживается для этого источника"))
			}
		} finally {
			setSuccess(_rawState.value.mutableUpdates().setIsDownloading(update.id, false))
		}
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
				ZipOutputStream(pos).use { zos ->
					var currentOffset = 0L
					files.forEach { file: com.aurora.gplayapi.data.models.File ->
						zos.putNextEntry(ZipEntry(file.name))
						downloader.downloadStream(update.id, file.url)?.use { input: java.io.InputStream ->
							input.copyToAndNotify(zos, update.id, installLog, totalSize, currentOffset)
						}
						zos.closeEntry()
						currentOffset += file.size
					}
				}
			}
			val fileName = "${update.name}_${update.version}.apks"
			if (downloadStorage.save(fileName, "application/octet-stream", pis, update.id)) {
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

	private fun saveStream(update: AppUpdate, url: String) {
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
		val ok = downloadStorage.save(fileName, mime, result.stream, update.id, installLog, result.contentLength)
		snackBar.snackBar(
			viewModelScope,
			if (ok) TextSnack(stringer.get(R.string.download_success, fileName)) else TextSnack(stringer.get(R.string.download_failure, fileName))
		)
	}

}
