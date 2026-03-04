package com.apkupdateross.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.SearchUiState
import com.apkupdateross.data.ui.removeId
import com.apkupdateross.data.ui.setIsInstalling
import com.apkupdateross.data.ui.setProgress
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.SearchRepository
import com.apkupdateross.util.Badger
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val installer: SessionInstaller,
    private val badger: Badger,
    downloader: Downloader,
    private val prefs: Prefs,
    snackBar: SnackBar,
    stringer: Stringer,
    installLog: InstallLog
) : InstallViewModel(downloader, installer, prefs, snackBar, stringer, installLog) {

    private val mutex = Mutex()

    private val state = MutableStateFlow<SearchUiState>(SearchUiState.Success(emptyList()))
    private val _filters = MutableStateFlow(loadSavedFilters())
    private var job: Job? = null
    private var lastQuery: String = ""

    init {
        subscribeToInstallStatus(state.value.updates())
        subscribeToInstallProgress { progress ->
            state.value = SearchUiState.Success(state.value.mutableUpdates().setProgress(progress))
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

    fun state(): StateFlow<SearchUiState> = state
    fun filters(): StateFlow<Set<SearchSourceFilter>> = _filters

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
        state.value = SearchUiState.Loading
        badger.changeSearchBadge("")
        searchRepository.search(text, filters).collect {
            it.onSuccess { apps ->
                state.value = SearchUiState.Success(apps)
                badger.changeSearchBadge(apps.size.toString())
            }.onFailure {
                badger.changeSearchBadge("!")
                state.value = SearchUiState.Error
            }
        }
    }

    override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        state.value = SearchUiState.Success(state.value.mutableUpdates().setIsInstalling(id, false))
        cancelDownload(id)
        installer.finish()
    }

    override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        val updates = state.value.mutableUpdates().removeId(id)
        state.value = SearchUiState.Success(updates)
        badger.changeSearchBadge(updates.size.toString())
        clearDownload(id)
        installer.finish()
    }

    override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        state.value = SearchUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
        downloadAndRootInstall(update.id, update.link)
    }

    override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        if(installer.checkPermission()) {
            state.value = SearchUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
            downloadAndInstall(update.id, update.packageName, update.link)
        }
    }

    override fun downloadAndShizukuInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        state.value = SearchUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
        downloadAndShizukuInstall(update.id, update.packageName, update.link)
    }

}
