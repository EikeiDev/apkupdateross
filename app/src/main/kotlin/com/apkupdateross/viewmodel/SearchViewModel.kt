package com.apkupdateross.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apkupdateross.R
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.preferredUpdateComparator
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.SearchUiState
import com.apkupdateross.data.ui.indexOf
import com.apkupdateross.data.ui.removeId
import com.apkupdateross.data.ui.removePackageName
import com.apkupdateross.data.ui.setIsDownloading
import com.apkupdateross.data.ui.setIsInstalling
import com.apkupdateross.data.ui.setProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.FourPdaRepository
import com.apkupdateross.repository.SearchRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val installer: SessionInstaller,
    installExecutor: AppInstallExecutor,
    private val badger: Badger,
    downloader: Downloader,
    downloadStorage: DownloadStorage,
    private val prefs: Prefs,
    snackBar: SnackBar,
    stringer: Stringer,
    installLog: InstallLog,
    private val fourPdaRepository: FourPdaRepository? = null
) : InstallViewModel(downloader, installer, installExecutor, prefs, snackBar, stringer, installLog, downloadStorage) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Success(emptyList()))
    private val _filters = MutableStateFlow(loadSavedFilters())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val useCompactView = prefs.useCompactViewFlow
    val portraitColumns = prefs.portraitColumnsFlow
    val landscapeColumns = prefs.landscapeColumnsFlow
    private var job: Job? = null
    private val retryJobs = ConcurrentHashMap<String, Job>()
    @Volatile private var lastQuery: String = ""

    init {
        subscribeToInstallStatus { _state.value.updates().flatMap { it.updates } }
        subscribeToInstallProgress { progress ->
            _state.update { current ->
                if (current is SearchUiState.Success) {
                    val hasTarget = current.updates.any { group -> group.updates.any { it.id == progress.id } }
                    if (hasTarget) SearchUiState.Success(current.updates.toMutableList().setProgress(progress))
                    else current
                } else current
            }
        }
    }

    private fun loadSavedFilters(): Set<SearchSourceFilter> {
        val saved = prefs.searchFilters.get()
        val mapped = saved.mapNotNull { runCatching { SearchSourceFilter.valueOf(it) }.getOrNull() }.toSet()
        var filters = mapped.ifEmpty { SearchSourceFilter.defaultSelection }
        var changed = false

        if (!prefs.searchFiltersHuaweiMigrated.get()) {
            filters = filters + SearchSourceFilter.HUAWEI
            prefs.searchFiltersHuaweiMigrated.put(true)
            changed = true
        }

        if (!prefs.searchFiltersUptodownMigrated.get()) {
            filters = filters + SearchSourceFilter.UPTODOWN
            prefs.searchFiltersUptodownMigrated.put(true)
            changed = true
        }

        if (!prefs.searchFiltersApk20Migrated.get()) {
            filters = filters + SearchSourceFilter.APK20
            prefs.searchFiltersApk20Migrated.put(true)
            changed = true
        }

        if (!prefs.searchFiltersSamsungMigrated.get()) {
            filters = filters + SearchSourceFilter.SAMSUNG
            prefs.searchFiltersSamsungMigrated.put(true)
            changed = true
        }

        if (!prefs.searchFiltersCodebergMigrated.get()) {
            filters = filters + SearchSourceFilter.CODEBERG
            prefs.searchFiltersCodebergMigrated.put(true)
            changed = true
        }

        if (!prefs.searchFiltersFourPdaMigrated.get()) {
            filters = filters + SearchSourceFilter.FOURPDA
            prefs.searchFiltersFourPdaMigrated.put(true)
            changed = true
        }

        if (changed) saveFilters(filters)
        return filters
    }

    private fun saveFilters(filters: Set<SearchSourceFilter>) {
        prefs.searchFilters.put(filters.map { it.name })
    }

    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    val filters: StateFlow<Set<SearchSourceFilter>> = _filters.asStateFlow()
    val loadingSources = searchRepository.loadingSources
    val failedSources = searchRepository.failedSources

    private fun cancelRetryJobs() {
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
    }

    fun search(text: String) {
        val query = text.trim()
        lastQuery = query
        _query.value = query
        if (query.isEmpty()) {
            job?.cancel()
            cancelRetryJobs()
            searchRepository.clearStatus()
            badger.changeSearchBadge("")
            _state.value = SearchUiState.Success(emptyList())
            return
        }
        if (query.length < 3) {
            return
        }
        job?.cancel()
        cancelRetryJobs()
        job = searchJob(query, _filters.value)
    }

    fun toggleFilter(filter: SearchSourceFilter) {
        val current = _filters.value
        val next = when {
            current.contains(filter) && current.size > 1 -> current - filter
            !current.contains(filter) -> current + filter
            else -> return
        }
        setFilters(next)
    }

    fun retrySource(source: com.apkupdateross.data.ui.Source) {
        val query = lastQuery
        if (query.length < 3) return
        retryJobs[source.name]?.cancel()
        val retryJob = viewModelScope.launch(Dispatchers.IO) {
            searchRepository.retrySource(query, source).collect { result ->
                result.onSuccess { extra ->
                    if (extra.isNotEmpty()) {
                        mutex.withLock {
                            if (lastQuery != query) return@withLock
                            val existing = _state.value.updates().flatMap { it.updates }
                                .filterNot { it.source.name == source.name }
                            val grouped = groupResults(existing + extra, query)
                            _state.value = SearchUiState.Success(grouped)
                            badger.changeSearchBadge(grouped.size.toString())
                        }
                    }
                }
            }
        }
        retryJobs[source.name] = retryJob
        retryJob.invokeOnCompletion { retryJobs.remove(source.name, retryJob) }
    }

    fun setFilters(filters: Set<SearchSourceFilter>) {
        if (_filters.value == filters) return
        _filters.value = filters
        saveFilters(filters)
        if (lastQuery.length >= 3) {
            search(lastQuery)
        }
    }

    private fun searchJob(text: String, filters: Set<SearchSourceFilter>) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        _state.value = SearchUiState.Loading
        badger.changeSearchBadge("")
        searchRepository.search(text, filters).collect { result ->
            result.onSuccess { apps ->
                val grouped = groupResults(apps, text)
                _state.value = SearchUiState.Success(grouped)
                badger.changeSearchBadge(grouped.size.toString())
            }.onFailure {
                badger.changeSearchBadge("!")
                _state.value = SearchUiState.Error
            }
        }
    }

    private fun groupResults(updates: List<AppUpdate>, query: String): List<GroupedAppUpdate> {
        return updates.groupBy { it.packageName }
            .map { (packageName, list) ->
                GroupedAppUpdate(
                    packageName = packageName,
                    updates = list.sortedWith(preferredUpdateComparator)
                )
            }.sortedWith(
                compareByDescending<GroupedAppUpdate> { it.primary.name.equals(query, ignoreCase = true) }
                    .thenByDescending { it.packageName.equals(query, ignoreCase = true) }
                    .thenByDescending { it.primary.name.startsWith(query, ignoreCase = true) }
                    .thenByDescending { it.primary.name.contains(query, ignoreCase = true) }
                    .thenByDescending { it.packageName.contains(query, ignoreCase = true) }
                    .thenBy { it.primary.name }
            )
    }

    override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        activeJobs.remove(id)?.cancel()
        _state.update { current ->
            if (current is SearchUiState.Success) {
                SearchUiState.Success(
                    current.updates.toMutableList().setIsInstalling(id, false).toMutableList().setIsDownloading(id, false)
                )
            } else current
        }
        cancelDownload(id)
        installer.finish(id)
    }

    override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        var newSize: Int? = null
        _state.update { current ->
            if (current is SearchUiState.Success) {
                val list = current.updates.toMutableList()
                val finishedUpdate = list.flatMap { it.updates }.find { it.id == id }
                val updates = finishedUpdate?.let { list.toMutableList().removePackageName(it.packageName) } ?: list.removeId(id)
                newSize = updates.size
                SearchUiState.Success(updates)
            } else current
        }
        newSize?.let { badger.changeSearchBadge(it.toString()) }
        clearDownload(id)
        installer.finish(id)
    }

    override fun downloadAndRootInstall(update: AppUpdate): Job =
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            _state.update { current ->
                if (current is SearchUiState.Success) SearchUiState.Success(current.updates.toMutableList().setIsInstalling(update.id, true))
                else current
            }
            downloadAndRootInstall(update.id, update.packageName, update.link)
        })

    override fun downloadAndInstall(update: AppUpdate): Job =
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            if(installer.checkPermission()) {
                _state.update { current ->
                    if (current is SearchUiState.Success) SearchUiState.Success(current.updates.toMutableList().setIsInstalling(update.id, true))
                    else current
                }
                downloadAndInstall(update.id, update.packageName, update.link)
            }
        })

    override fun downloadAndShizukuInstall(update: AppUpdate): Job =
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            _state.update { current ->
                if (current is SearchUiState.Success) SearchUiState.Success(current.updates.toMutableList().setIsInstalling(update.id, true))
                else current
            }
            downloadAndShizukuInstall(update.id, update.packageName, update.link)
        })

    suspend fun loadFourPdaPostNotes(postId: Long): String? =
        fourPdaRepository?.loadPostNotes(postId)

    fun downloadToStorage(update: AppUpdate) {
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            _state.update { current ->
                if (current is SearchUiState.Success) SearchUiState.Success(current.updates.toMutableList().setIsDownloading(update.id, true))
                else current
            }
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
                        Log.e("SearchViewModel", "Error in downloadToStorage", it)
                    }
                }
            } finally {
                _state.update { current ->
                    if (current is SearchUiState.Success) SearchUiState.Success(current.updates.toMutableList().setIsDownloading(update.id, false))
                    else current
                }
                clearDownload(update.id)
            }
        })
    }

}
