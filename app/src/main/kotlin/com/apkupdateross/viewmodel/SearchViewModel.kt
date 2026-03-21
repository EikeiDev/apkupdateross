package com.apkupdateross.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apkupdateross.R
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.SearchUiState
import com.apkupdateross.data.ui.indexOf
import com.apkupdateross.data.ui.removeId
import com.apkupdateross.data.ui.removePackageName
import com.apkupdateross.data.ui.setIsDownloading
import com.apkupdateross.data.ui.setIsInstalling
import com.apkupdateross.data.ui.setProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.SearchRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.sync.Mutex

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val installer: SessionInstaller,
    private val badger: Badger,
    downloader: Downloader,
    downloadStorage: DownloadStorage,
    private val prefs: Prefs,
    snackBar: SnackBar,
    stringer: Stringer,
    installLog: InstallLog
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog, downloadStorage) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Success(emptyList()))
    private val _filters = MutableStateFlow(loadSavedFilters())
    val useCompactView = prefs.useCompactViewFlow
    val portraitColumns = prefs.portraitColumnsFlow
    val landscapeColumns = prefs.landscapeColumnsFlow
    private var job: Job? = null
    private var lastQuery: String = ""

    init {
        subscribeToInstallStatus(_state.value.updates().flatMap { it.updates })
        subscribeToInstallProgress { progress ->
            _state.value = SearchUiState.Success(_state.value.mutableUpdates().setProgress(progress))
        }
    }

    private fun loadSavedFilters(): Set<SearchSourceFilter> {
        val saved = prefs.searchFilters.get()
        val mapped = saved.mapNotNull { runCatching { SearchSourceFilter.valueOf(it) }.getOrNull() }.toSet()
        return mapped.ifEmpty { SearchSourceFilter.defaultSelection }
    }

    private fun saveFilters(filters: Set<SearchSourceFilter>) {
        prefs.searchFilters.put(filters.map { it.name })
    }

    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    val filters: StateFlow<Set<SearchSourceFilter>> = _filters.asStateFlow()

    fun search(text: String) {
        val query = text.trim()
        lastQuery = query
        if (query.length < 3) {
            return
        }
        job?.cancel()
        job = searchJob(query, _filters.value)
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
                    updates = list.sortedWith(
                        compareByDescending<AppUpdate> { it.versionCode }
                            .thenBy { it.source != PlaySource }
                    )
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
        _state.value = SearchUiState.Success(
            _state.value.mutableUpdates().setIsInstalling(id, false).toMutableList().setIsDownloading(id, false)
        )
        cancelDownload(id)
        installer.finish()
    }

    override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        val current = _state.value.mutableUpdates()
        val finishedUpdate = current.flatMap { it.updates }.find { it.id == id }
        val updates = finishedUpdate?.let { current.toMutableList().removePackageName(it.packageName) } ?: current.removeId(id)
        _state.value = SearchUiState.Success(updates)
        badger.changeSearchBadge(updates.size.toString())
        clearDownload(id)
        installer.finish()
    }

    override fun downloadAndRootInstall(update: AppUpdate): Job =
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            _state.value = SearchUiState.Success(_state.value.mutableUpdates().setIsInstalling(update.id, true))
            downloadAndRootInstall(update.id, update.link)
        })

    override fun downloadAndInstall(update: AppUpdate): Job =
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            if(installer.checkPermission()) {
                _state.value = SearchUiState.Success(_state.value.mutableUpdates().setIsInstalling(update.id, true))
                downloadAndInstall(update.id, update.packageName, update.link)
            }
        })

    override fun downloadAndShizukuInstall(update: AppUpdate): Job =
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            _state.value = SearchUiState.Success(_state.value.mutableUpdates().setIsInstalling(update.id, true))
            downloadAndShizukuInstall(update.id, update.packageName, update.link)
        })

    fun downloadToStorage(update: AppUpdate) {
        trackJob(update.id, viewModelScope.launch(Dispatchers.IO) {
            _state.value = SearchUiState.Success(_state.value.mutableUpdates().setIsDownloading(update.id, true))
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
                        Log.e("SearchViewModel", "Error in downloadToStorage", it)
                    }
                }
            } finally {
                _state.value = SearchUiState.Success(_state.value.mutableUpdates().setIsDownloading(update.id, false))
                clearDownload(update.id)
            }
        })
    }

}
