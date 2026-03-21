package com.apkupdateross.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppsUiState
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.AppsRepository
import com.apkupdateross.util.Badger
import com.apkupdateross.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

class AppsViewModel(
	private val repository: AppsRepository,
	private val prefs: Prefs,
	private val badger: Badger
) : ViewModel() {

	private val mutex = Mutex()
	private val _searchQuery = MutableStateFlow("")
	private var fullAppsList = emptyList<AppInstalled>()
	val useCompactView = prefs.useCompactViewFlow
	val portraitColumns = prefs.portraitColumnsFlow
	val landscapeColumns = prefs.landscapeColumnsFlow
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

	private fun updateState() {
		val query = _searchQuery.value.trim()
		val filteredApps = if (query.isEmpty()) {
			fullAppsList
		} else {
			fullAppsList.filter {
				it.name.contains(query, ignoreCase = true) ||
						it.packageName.contains(query, ignoreCase = true)
			}
		}

		_state.value = AppsUiState.Success(
			filteredApps,
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

	fun ignore(packageName: String) = viewModelScope.launchWithMutex(mutex, Dispatchers.Default) {
		val ignored = prefs.ignoredApps.get().toMutableList()
		if (ignored.contains(packageName)) {
			ignored.remove(packageName)
		} else {
			ignored.add(packageName)
		}
		prefs.ignoredApps.put(ignored)
		refresh(false)
	}

	private fun buildLoadingState() = AppsUiState.Loading(
		prefs.excludeSystem.get(),
		prefs.excludeStore.get(),
		prefs.excludeDisabled.get(),
		_searchQuery.value
	)

}
