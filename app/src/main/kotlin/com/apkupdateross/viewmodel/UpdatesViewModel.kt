package com.apkupdateross.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.FourPdaSource
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.Source
import com.apkupdateross.data.ui.toAppUpdate
import com.apkupdateross.data.ui.toCachedEntry
import com.apkupdateross.data.ui.preferredUpdateComparator
import com.apkupdateross.data.ui.updateComparator
import com.apkupdateross.data.ui.UpdatesUiState
import com.apkupdateross.data.ui.indexOf
import com.apkupdateross.data.ui.removeId
import com.apkupdateross.data.ui.removePackageName
import com.apkupdateross.data.ui.setIsDownloading
import com.apkupdateross.data.ui.setIsInstalling
import com.apkupdateross.data.ui.setProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.FourPdaRepository
import com.apkupdateross.repository.UpdatesRepository
import com.apkupdateross.util.AppInstallExecutor
import com.apkupdateross.util.Badger
import com.apkupdateross.util.DownloadStorage
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.apkupdateross.data.snack.TextSnack


enum class UpdatesSortMode {
	NAME,
	SOURCE
}

class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	installExecutor: AppInstallExecutor,
	private val prefs: Prefs,
	private val badger: Badger,
	downloader: Downloader,
	downloadStorage: DownloadStorage,
	snackBar: SnackBar,
	stringer: Stringer,
	installLog: InstallLog,
	private val fourPdaRepository: FourPdaRepository? = null
) : InstallViewModel(downloader, installer, installExecutor, prefs, snackBar, stringer, installLog, downloadStorage) {

	private val mutex = Mutex()
	private val initialCachedUpdates = runCatching {
		prefs.cachedUpdates.get().map { it.toAppUpdate() }
	}.getOrDefault(emptyList())
	private val _updates = MutableStateFlow<List<AppUpdate>>(initialCachedUpdates)
	private val _filterQuery = MutableStateFlow("")
	private val _selectedSourceFilter = MutableStateFlow<String?>(null)
	private val _sortMode = MutableStateFlow(UpdatesSortMode.NAME)
	private val _isInitialLoading = MutableStateFlow(initialCachedUpdates.isEmpty() && prefs.autoCheckOnStart.get())
	private val _isRefreshing = MutableStateFlow(false)
	private val _isInstallingAll = MutableStateFlow(false)
	private val _selfUpdate = MutableStateFlow<AppUpdate?>(null)
	private var snoozedSelfUpdateVersionCode: Long? = null
	private var installAllJob: Job? = null
	private var hasAutoCheckedOnStart = false
	val useCompactView = prefs.useCompactViewFlow
	val portraitColumns = prefs.portraitColumnsFlow
	val landscapeColumns = prefs.landscapeColumnsFlow
	val swipeIgnoreEnabled = prefs.swipeIgnoreEnabledFlow
	val swipeIgnoreDirection = prefs.swipeIgnoreDirectionFlow
	val pinnedAppSources = prefs.pinnedAppSourcesFlow
	val loadingSources = updatesRepository.loadingSources
	val failedSources = updatesRepository.failedSources
	val selectedSourceFilter: StateFlow<String?> = _selectedSourceFilter.asStateFlow()
	val sortMode: StateFlow<UpdatesSortMode> = _sortMode.asStateFlow()

	val availableSources: StateFlow<List<Source>> = combine(_updates, prefs.ignoredVersionsFlow) { all, ignoredIds ->
		all.filter { !ignoredIds.contains(it.id) }
			.map { it.source }
			.distinctBy { it.name }
			.sortedBy { it.name.lowercase() }
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val state: StateFlow<UpdatesUiState> = combine(
		_updates,
		_filterQuery,
		prefs.ignoredVersionsFlow,
		_isInitialLoading,
		prefs.sourcePriorityOrderFlow,
		prefs.pinnedAppSourcesFlow,
		_selectedSourceFilter,
		_sortMode
	) { values ->
		@Suppress("UNCHECKED_CAST")
		val all = values[0] as List<AppUpdate>
		val query = values[1] as String
		@Suppress("UNCHECKED_CAST")
		val ignoredIds = values[2] as List<Int>
		val loading = values[3] as Boolean
		@Suppress("UNCHECKED_CAST")
		val priorityOrder = values[4] as List<String>
		@Suppress("UNCHECKED_CAST")
		val pinnedSources = values[5] as Map<String, String>
		val sourceFilter = values[6] as String?
		val sort = values[7] as UpdatesSortMode

		if (loading && all.isEmpty()) return@combine UpdatesUiState.Loading

		val filtered = all.filter { !ignoredIds.contains(it.id) }
		val grouped = groupUpdates(filtered, priorityOrder, pinnedSources, sourceFilter, sort)
		
		val result = if (query.isNotBlank()) {
			grouped.filter { g ->
				g.primary.name.contains(query, ignoreCase = true) ||
						g.packageName.contains(query, ignoreCase = true)
			}
		} else {
			grouped
		}
		
		UpdatesUiState.Success(result)
	}
	.flowOn(Dispatchers.Default)
	.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), if (initialCachedUpdates.isEmpty() && prefs.autoCheckOnStart.get()) UpdatesUiState.Loading else UpdatesUiState.Success(emptyList()))

	init {
		_updates.onEach { updates ->
			updateSelfUpdateCandidate(updates)
		}.launchIn(viewModelScope)

		kotlinx.coroutines.flow.combine(_updates, prefs.ignoredVersionsFlow) { updates, ignoredIds ->
			val filtered = updates.filter { !ignoredIds.contains(it.id) }
			val count = groupUpdates(filtered).size
			badger.changeUpdatesBadge(if (count > 0) count.toString() else "")
		}.launchIn(viewModelScope)


		// Static subscription for status (efficient)
		installLog.status().onEach { status ->
			val updates = _updates.value
			val app = updates.find { it.id == status.id }
			if (status.snack && status.reason != InstallFailureReason.CANCELLED) {
				val details = if (status.success) "" else localizedFailureReason(status.reason, status.message)
				recordForegroundInstallResult(status.id, status.success, app, details)
			} else {
				clearInFlightUpdate(status.id)
			}
			if (status.snack && app != null && status.reason != InstallFailureReason.CANCELLED) {
				if (status.success) {
					snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.install_success, app.name)))
				} else {
					showFailureSnack(app, app.name, status.reason, status.message)
				}
			}
			if (status.success) {
				finishInstall(status.id).join()
			} else {
				installLog.emitProgress(AppInstallProgress(status.id, 0L))
				cancelInstall(status.id).join()
			}
			downloader.cleanup(status.id)
		}.launchIn(viewModelScope)

		subscribeToInstallProgress { progress ->
			_updates.update { current ->
				if (current.none { it.id == progress.id }) current
				else current.toMutableList().setProgress(progress)
			}
		}
	}

	val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
	val isInstallingAll: StateFlow<Boolean> = _isInstallingAll.asStateFlow()
	val selfUpdate: StateFlow<AppUpdate?> = _selfUpdate.asStateFlow()
	val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

	fun setFilterQuery(query: String) {
		_filterQuery.value = query
	}

	fun setSourceFilter(sourceName: String?) {
		_selectedSourceFilter.value = if (_selectedSourceFilter.value == sourceName) null else sourceName
	}

	fun setSortMode(mode: UpdatesSortMode) {
		_sortMode.value = mode
	}

	fun onScreenEntered() {
		if (!hasAutoCheckedOnStart) {
			hasAutoCheckedOnStart = true
			if (prefs.autoCheckOnStart.get()) {
				refresh(load = _updates.value.isEmpty())
			}
		}
	}

	fun retrySource(source: Source) = viewModelScope.launch(Dispatchers.IO) {
		updatesRepository.retrySource(source).collect { newEntries ->
			mutex.withLock {
				val current = _updates.value.filterNot { it.source.name == source.name }
				val merged = current + newEntries
				_updates.value = merged
				persistUpdatesCache(merged)
			}
		}
	}

	fun checkSingleApp(packageName: String) = viewModelScope.launch(Dispatchers.IO) {
		_isRefreshing.value = true
		try {
			var foundAny = false
			updatesRepository.updates(force = false, targetPackageName = packageName).collect { entries ->
				if (entries.isNotEmpty()) foundAny = true
				mutex.withLock {
					val withoutTarget = _updates.value.filterNot { it.packageName == packageName }
					val merged = withoutTarget + entries
					_updates.value = merged
					persistUpdatesCache(merged)
				}
			}
			if (!foundAny) {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.notification_no_updates_message)))
			}
		} finally {
			_isRefreshing.value = false
		}
	}

	private fun persistUpdatesCache(entries: List<AppUpdate>) {
		runCatching {
			prefs.cachedUpdates.put(entries.map { it.toCachedEntry() })
		}
	}

	private var refreshJob: Job? = null

	override fun onSignatureConflictAction(app: AppUpdate): (() -> Unit) = {
		ignoreVersion(app.id)
	}

	fun cancelRefresh() {
		refreshJob?.cancel()
		refreshJob = null
		_isRefreshing.value = false
		_isInitialLoading.value = false
	}

	fun onExternalPackageChanged(
		packageName: String,
		removed: Boolean,
		installedVersionCode: Long? = null,
		installedVersionName: String? = null
	) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		var changed = false
		_updates.update { current ->
			if (current.none { it.packageName == packageName }) return@update current
			val updated = if (removed) {
				current.filterNot { it.packageName == packageName }
			} else if (installedVersionCode != null) {
				current.filterNot { entry ->
					if (entry.packageName != packageName) return@filterNot false
					if (entry.versionCode > 0L && installedVersionCode <= Int.MAX_VALUE.toLong()) {
						entry.versionCode in 1..installedVersionCode
					} else {
						!installedVersionName.isNullOrBlank() && entry.version == installedVersionName
					}
				}
			} else {
				current
			}
			changed = updated.size != current.size
			updated
		}
		if (changed) {
			persistUpdatesCache(_updates.value)
		}
	}

	fun refresh(load: Boolean = true): Job? {
		if (refreshJob?.isActive == true) return refreshJob
		refreshJob = viewModelScope.launch(Dispatchers.IO) {
			if (load && _updates.value.isEmpty()) _isInitialLoading.value = true
			_isRefreshing.value = true
		try {
			updatesRepository.updates(force = load).collect { entries ->
				mutex.withLock {
					val current = _updates.value
					val updated = entries.map { entry ->
						current.find { it.id == entry.id }?.let { existing ->
							entry.copy(
								isInstalling = existing.isInstalling,
								isDownloading = existing.isDownloading,
								progress = existing.progress,
								total = if (existing.total > 0) existing.total else entry.total
							)
						} ?: entry
					}
					_updates.value = updated
					persistUpdatesCache(updated)
				}
				_isInitialLoading.value = false
			}
		} finally {
			_isRefreshing.value = false
			_isInitialLoading.value = false
		}
		}
		return refreshJob
	}

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val allUpdates = _updates.value
		val reference = allUpdates.find { it.id == id } ?: return@launchWithMutex
		
		val ignored = prefs.ignoredVersions.get().toMutableList()
		val targetIds = allUpdates.filter { 
			it.packageName == reference.packageName
		}.map { it.id }
		
		val targetInfos = allUpdates.filter { it.packageName == reference.packageName }
			.map { com.apkupdateross.data.ui.IgnoredUpdateInfo(it.id, it.packageName, it.name, it.version) }

		val ignoredInfos = prefs.ignoredUpdateInfos.get().toMutableList()

		if (ignored.contains(id)) {
			ignored.removeAll(targetIds.toSet())
			ignoredInfos.removeAll { info -> targetIds.contains(info.id) }
		} else {
			ignored.addAll(targetIds)
			ignoredInfos.addAll(targetInfos)
			snackBar.snackBar(
				viewModelScope,
				com.apkupdateross.data.snack.ActionSnack(
					message = stringer.get(R.string.ignored_toast, reference.name),
					actionLabel = stringer.get(R.string.undo),
					action = { unignoreVersion(id) }
				)
			)
		}
		prefs.setIgnoredVersions(ignored.distinct(), ignoredInfos.distinctBy { it.id })
	}

	fun unignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val allUpdates = _updates.value
		val reference = allUpdates.find { it.id == id } ?: return@launchWithMutex
		val targetIds = allUpdates.filter { it.packageName == reference.packageName }.map { it.id }
		
		val ignored = prefs.ignoredVersions.get().toMutableList()
		val ignoredInfos = prefs.ignoredUpdateInfos.get().toMutableList()

		ignored.removeAll(targetIds.toSet())
		ignoredInfos.removeAll { info -> targetIds.contains(info.id) }
		
		prefs.setIgnoredVersions(ignored.distinct(), ignoredInfos.distinctBy { it.id })
	}

	fun installAllCount(groups: List<GroupedAppUpdate>): Int =
		bulkInstallTargets(groups, prefs.installMode.get()).size

	fun installAll(groups: List<GroupedAppUpdate>): Job? {
		if (installAllJob?.isActive == true) return installAllJob

		val installMode = prefs.installMode.get()
		val targets = bulkInstallTargets(groups, installMode)
		if (targets.isEmpty()) return null

		if (installMode == 0 && !installer.checkPermission()) return null
		if (installMode == 2 && !installer.isShizukuAvailable()) {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.shizuku_not_running)))
			return null
		}

		installAllJob = viewModelScope.launch(Dispatchers.IO) {
			_isInstallingAll.value = true
			try {
				for (target in targets) {
					if (!isActive) break

					val update = _updates.value.firstOrNull { it.id == target.id }
					if (update == null || !update.canBulkInstall(installMode)) continue

					startBulkInstall(update, installMode)?.join()
				}
			} finally {
				_isInstallingAll.value = false
			}
		}
		return installAllJob
	}

	fun cancelInstallAll() {
		installAllJob?.cancel()
		installAllJob = null
		val idsToCancel = _updates.value
			.filter { it.isInstalling || it.isDownloading }
			.map { it.id }
		idsToCancel.forEach { cancelInstall(it) }
		_isInstallingAll.value = false
	}

	override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		activeJobs.remove(id)?.cancel()
		_updates.update { current ->
			current.toMutableList()
				.setIsInstalling(id, false)
				.setIsDownloading(id, false)
		}
		cancelDownload(id)
		installer.finish(id)
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		var finishedUpdate: AppUpdate? = null
		_updates.update { current ->
			val mutable = current.toMutableList()
			finishedUpdate = mutable.find { it.id == id }
			finishedUpdate?.let { mutable.removePackageName(it.packageName) } ?: mutable.removeId(id)
		}
		persistUpdatesCache(_updates.value)
		clearDownload(id)
		installer.finish(id)
		finishedUpdate?.let {
			if (it.packageName == BuildConfig.APPLICATION_ID) {
				markSelfUpdateInstalled(it.versionCode)
			}
		}
	}

	override fun downloadAndRootInstall(update: AppUpdate): Job =
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			_updates.update { it.toMutableList().setIsInstalling(update.id, true) }
			downloadAndRootInstall(update.id, update.packageName, update.link)
		})

	override fun downloadAndInstall(update: AppUpdate): Job =
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			if(installer.checkPermission()) {
				_updates.update { it.toMutableList().setIsInstalling(update.id, true) }
				downloadAndInstall(update.id, update.packageName, update.link)
			}
		})

	override fun downloadAndShizukuInstall(update: AppUpdate): Job =
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			_updates.update { it.toMutableList().setIsInstalling(update.id, true) }
			downloadAndShizukuInstall(update.id, update.packageName, update.link)
		})

	private fun bulkInstallTargets(groups: List<GroupedAppUpdate>, installMode: Int): List<AppUpdate> =
		groups
			.mapNotNull { group -> group.updates.firstOrNull { it.canBulkInstall(installMode) } }
			.sortedWith(
				compareBy<AppUpdate> { it.packageName == BuildConfig.APPLICATION_ID }
					.thenBy { it.name.lowercase() }
			)

	private fun AppUpdate.canBulkInstall(installMode: Int): Boolean = when {
		isPaid || isInstalling || isDownloading -> false
		source == FourPdaSource -> false
		downloadChoices.size > 1 -> false
		link is Link.Empty -> false
		link is Link.BrowserDownload -> false
		else -> true
	}

	private fun startBulkInstall(update: AppUpdate, installMode: Int): Job? {
		markInFlightUpdate(update)
		return when (installMode) {
			2 -> downloadAndShizukuInstall(update)
			1 -> downloadAndRootInstall(update)
			else -> downloadAndInstall(update)
		}
	}

	fun togglePinSource(app: AppUpdate) {
		val currentlyPinned = prefs.pinnedAppSourcesFlow.value[app.packageName]
		if (currentlyPinned == app.source.name) {
			prefs.setPinnedAppSource(app.packageName, null)
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.source_unpinned_toast, app.name)))
		} else {
			prefs.setPinnedAppSource(app.packageName, app.source.name)
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.source_pinned_toast, app.source.name, app.name)))
		}
	}

	private fun groupUpdates(
		updates: List<AppUpdate>,
		sourcePriorityOrder: List<String> = prefs.sourcePriorityOrderFlow.value,
		pinnedSources: Map<String, String> = prefs.pinnedAppSourcesFlow.value,
		sourceFilter: String? = null,
		sortMode: UpdatesSortMode = UpdatesSortMode.NAME
	): List<GroupedAppUpdate> {
		val ignoredVersions = try { prefs.ignoredVersions.get() } catch (e: Exception) { emptyList() }
		val grouped = updates
			.filter { !ignoredVersions.contains(it.id) }
			.filter { sourceFilter == null || it.source.name == sourceFilter }
			.groupBy { it.packageName }
			.map { (packageName, list) ->
				val comparator = updateComparator(sourcePriorityOrder, pinnedSources[packageName])
				GroupedAppUpdate(
					packageName = packageName,
					updates = list.sortedWith(comparator)
				)
			}
		return when (sortMode) {
			UpdatesSortMode.NAME -> grouped.sortedBy { it.primary.name.lowercase() }
			UpdatesSortMode.SOURCE -> grouped.sortedWith(
				compareBy<GroupedAppUpdate> { it.primary.source.name.lowercase() }
					.thenBy { it.primary.name.lowercase() }
			)
		}
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
		if (BuildConfig.DEBUG) {
			_selfUpdate.value = null
			snoozedSelfUpdateVersionCode = null
			return
		}
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

	suspend fun loadFourPdaPostNotes(postId: Long): String? =
		fourPdaRepository?.loadPostNotes(postId)

	fun downloadToStorage(update: AppUpdate) {
		trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
			_updates.update { it.toMutableList().setIsDownloading(update.id, true) }
			try {
				runCatching {
					when (val link = update.link) {
						is Link.Url -> saveStream(update, link)
						is Link.Xapk -> saveStream(update, link.link, forceSplitPackage = true)
						is Link.Play -> savePlayFiles(update, link)
						else -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_not_supported_for_source)))
					}
				}.onFailure {
					if (it !is CancellationException) {
						Log.e("UpdatesViewModel", "Error in downloadToStorage", it)
					}
				}
			} finally {
				_updates.update { it.toMutableList().setIsDownloading(update.id, false) }
				clearDownload(update.id)
			}
		})
	}

}
