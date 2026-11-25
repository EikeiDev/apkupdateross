package com.apkupdateross.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdateross.R
import com.apkupdateross.data.snack.ActionSnack
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppsUiState
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.AppsRepository
import com.apkupdateross.util.Badger
import com.apkupdateross.util.DownloadStorage
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class AppsSortMode {
	NAME,
	UPDATED_DESC,
	IGNORED_FIRST
}

class AppsViewModel(
	private val repository: AppsRepository,
	private val prefs: Prefs,
	private val badger: Badger,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	private val downloadStorage: DownloadStorage,
	private val context: Context
) : ViewModel() {

	private val mutex = Mutex()
	private val _searchQuery = MutableStateFlow("")
	private val _sortMode = MutableStateFlow(AppsSortMode.NAME)
	val sortMode: StateFlow<AppsSortMode> = _sortMode.asStateFlow()
	private var fullAppsList = emptyList<AppInstalled>()
	val useCompactView = prefs.useCompactViewFlow
	val portraitColumns = prefs.portraitColumnsFlow
	val landscapeColumns = prefs.landscapeColumnsFlow
	val swipeIgnoreEnabled = prefs.swipeIgnoreEnabledFlow
	val swipeIgnoreDirection = prefs.swipeIgnoreDirectionFlow
	private val _state = MutableStateFlow<AppsUiState>(buildLoadingState())
	val state: StateFlow<AppsUiState> = _state.asStateFlow()

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if (load) _state.value = buildLoadingState()
		badger.changeAppsBadge("")
		repository.getApps().collect {
			it.onSuccess { apps ->
				fullAppsList = apps
				updateState()
				badger.changeAppsBadge(apps.size.toString())
			}.onFailure { ex ->
				_state.value = AppsUiState.Error
				badger.changeAppsBadge("!")
				Log.e("InstalledViewModel", "Error getting apps.", ex)
			}
		}
	}

	fun onSearchQueryChange(query: String) = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		_searchQuery.value = query
		updateState()
	}

	fun setSortMode(mode: AppsSortMode) = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		_sortMode.value = mode
		updateState()
	}

	fun backupApk(app: AppInstalled) = viewModelScope.launch(Dispatchers.IO) {
		val baseFile = app.sourceDir.takeIf { it.isNotBlank() }?.let { File(it) }
		if (baseFile == null || !baseFile.isFile || !baseFile.canRead()) {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, app.name)))
			return@launch
		}
		val splitFiles = app.splitSourceDirs.map { File(it) }.filter { it.isFile && it.canRead() }
		val versionPart = app.version.ifBlank { app.versionCode.toString() }
		val saved = runCatching {
			if (splitFiles.isEmpty()) {
				val fileName = "${app.name}_$versionPart.apk"
				downloadStorage.save(fileName, "application/vnd.android.package-archive", baseFile.inputStream())
			} else {
				val tempZip = File.createTempFile("backup_", ".apks", context.cacheDir)
				try {
					ZipOutputStream(tempZip.outputStream()).use { zip ->
						val allFiles = listOf(baseFile) + splitFiles
						allFiles.forEachIndexed { index, apk ->
							val entryName = if (index == 0) "base.apk" else apk.name.ifBlank { "split_$index.apk" }
							zip.putNextEntry(ZipEntry(entryName))
							apk.inputStream().use { input -> input.copyTo(zip) }
							zip.closeEntry()
						}
					}
					val fileName = "${app.name}_$versionPart.apks"
					downloadStorage.save(fileName, "application/octet-stream", tempZip.inputStream())
				} finally {
					tempZip.delete()
				}
			}
		}.getOrDefault(false)

		val msg = if (saved) R.string.backup_apk_success else R.string.download_failure
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(msg, app.name)))
	}

	private fun updateState() {
		val query = _searchQuery.value.trim()
		val filteredApps = if (query.isEmpty()) {
			fullAppsList
		} else {
			fullAppsList.filter {
				it.name.contains(query, ignoreCase = true) ||
						it.packageName.contains(query, ignoreCase = true) ||
						it.installerSource.contains(query, ignoreCase = true)
			}
		}
		val sortedApps = when (_sortMode.value) {
			AppsSortMode.NAME -> filteredApps.sortedWith(
				compareBy<AppInstalled> { it.ignored }.thenBy { it.name.lowercase() }
			)
			AppsSortMode.UPDATED_DESC -> filteredApps.sortedWith(
				compareByDescending<AppInstalled> { it.lastUpdateTime }.thenBy { it.name.lowercase() }
			)
			AppsSortMode.IGNORED_FIRST -> filteredApps.sortedWith(
				compareByDescending<AppInstalled> { it.ignored }.thenBy { it.name.lowercase() }
			)
		}

		_state.value = AppsUiState.Success(
			sortedApps,
			prefs.excludeSystem.get(),
			prefs.excludeStore.get(),
			prefs.excludeDisabled.get(),
			query
		)
	}

	fun onSystemClick() = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		prefs.excludeSystem.put(!prefs.excludeSystem.get())
		refresh(false)
	}

	fun onAppStoreClick() = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		prefs.excludeStore.put(!prefs.excludeStore.get())
		refresh(false)
	}

	fun onDisabledClick() = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		prefs.excludeDisabled.put(!prefs.excludeDisabled.get())
		refresh(false)
	}

	fun ignore(packageName: String): Job = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		val ignored = prefs.ignoredApps.get().toMutableList()
		val added = if (ignored.contains(packageName)) {
			ignored.remove(packageName)
			false
		} else {
			ignored.add(packageName)
			true
		}
		prefs.ignoredApps.put(ignored)
		if (added) {
			val appName = fullAppsList.find { it.packageName == packageName }?.name ?: packageName
			snackBar.snackBar(
				viewModelScope,
				ActionSnack(
					message = stringer.get(R.string.ignored_toast, appName),
					actionLabel = stringer.get(R.string.undo),
					action = { ignore(packageName) }
				)
			)
		}
		refresh(false)
	}

	private fun buildLoadingState() = AppsUiState.Loading(
		prefs.excludeSystem.get(),
		prefs.excludeStore.get(),
		prefs.excludeDisabled.get(),
		_searchQuery.value
	)

}
