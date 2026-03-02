package com.apkupdateross.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkupdateross.BuildConfig
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.UpdatesUiState
import com.apkupdateross.data.ui.removeId
import com.apkupdateross.data.ui.setIsInstalling
import com.apkupdateross.data.ui.setProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.util.Badger
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex


class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	private val prefs: Prefs,
	private val badger: Badger,
	downloader: Downloader,
	snackBar: SnackBar,
	stringer: Stringer,
	installLog: InstallLog
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog) {

	private val mutex = Mutex()
	private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
	private val _isRefreshing = MutableStateFlow(false)
	private val _selfUpdate = MutableStateFlow<AppUpdate?>(null)
	private var snoozedSelfUpdateVersionCode: Long? = null

	init {
		subscribeToInstallStatus(state.value.updates())
		subscribeToInstallProgress { progress ->
			setSuccess(state.value.mutableUpdates().setProgress(progress))
		}
	}

	fun state(): StateFlow<UpdatesUiState> = state
	fun isRefreshing(): StateFlow<Boolean> = _isRefreshing.asStateFlow()
	fun selfUpdate(): StateFlow<AppUpdate?> = _selfUpdate.asStateFlow()

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if (load) state.value = UpdatesUiState.Loading
		_isRefreshing.value = true
		badger.changeUpdatesBadge("")
		try {
			updatesRepository.updates(force = load).collect { updates ->
				setSuccess(updates)
			}
		} finally {
			_isRefreshing.value = false
		}
	}

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val ignored = prefs.ignoredVersions.get().toMutableList()
		if (ignored.contains(id)) ignored.remove(id) else ignored.add(id)
		prefs.ignoredVersions.put(ignored)
		setSuccess(state.value.mutableUpdates())
	}

	override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		setSuccess(state.value.mutableUpdates().setIsInstalling(id, false))
		cancelDownload(id)
		installer.finish()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val current = state.value.mutableUpdates()
		val finishedUpdate = current.find { it.id == id }
		setSuccess(current.removeId(id))
		clearDownload(id)
		installer.finish()
		finishedUpdate?.let {
			if (it.packageName == BuildConfig.APPLICATION_ID) {
				markSelfUpdateInstalled(it.versionCode)
			}
		}
	}

	override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		setSuccess(state.value.mutableUpdates().setIsInstalling(update.id, true))
		downloadAndRootInstall(update.id, update.link)
	}

	override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		if(installer.checkPermission()) {
			setSuccess(state.value.mutableUpdates().setIsInstalling(update.id, true))
			downloadAndInstall(update.id, update.packageName, update.link)
		}
	}

	override fun downloadAndShizukuInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		setSuccess(state.value.mutableUpdates().setIsInstalling(update.id, true))
		downloadAndShizukuInstall(update.id, update.packageName, update.link)
	}

	private fun List<AppUpdate>.filterIgnoredVersions(ignoredVersions: List<Int>) = this
		.filter { !ignoredVersions.contains(it.id) }

	private fun setSuccess(updates: List<AppUpdate>) {
		val filtered = updates.filterIgnoredVersions(prefs.ignoredVersions.get())
		state.value = UpdatesUiState.Success(filtered)
		badger.changeUpdatesBadge(filtered.size.toString())
		updateSelfUpdateCandidate(filtered)
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

}
