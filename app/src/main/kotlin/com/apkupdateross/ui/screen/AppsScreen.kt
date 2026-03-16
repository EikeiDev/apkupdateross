package com.apkupdateross.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppsUiState
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.ExcludeAppStoreIcon
import com.apkupdateross.ui.component.ExcludeDisabledIcon
import com.apkupdateross.ui.component.ExcludeSystemIcon
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.InstalledItem
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.theme.statusBarColor
import com.apkupdateross.viewmodel.AppsViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
	viewModel: AppsViewModel = koinViewModel()
) {
	val state = viewModel.state().collectAsStateWithLifecycle().value

	val (excludeSystem, excludeAppStore, excludeDisabled) = when (state) {
		is AppsUiState.Loading -> Triple(state.excludeSystem, state.excludeAppStore, state.excludeDisabled)
		is AppsUiState.Success -> Triple(state.excludeSystem, state.excludeAppStore, state.excludeDisabled)
		else -> Triple(false, false, false)
	}

	val searchQuery = when (state) {
		is AppsUiState.Loading -> state.searchQuery
		is AppsUiState.Success -> state.searchQuery
		else -> ""
	}

	var isSearchActive by remember { mutableStateOf(false) }

	Column {
		AppsTopBar(viewModel, excludeSystem, excludeAppStore, excludeDisabled, isSearchActive, searchQuery, onSearchToggle = { isSearchActive = it })
		PullToRefreshBox(
			isRefreshing = state is AppsUiState.Loading,
			onRefresh = { viewModel.refresh() },
			modifier = Modifier.fillMaxSize()
		) {
			state.onLoading {
				LoadingGrid()
			}.onError {
				DefaultErrorScreen()
			}.onSuccess {
				InstalledGrid {
					items(it.apps) { app ->
						InstalledItem(app) { viewModel.ignore(app.packageName) }
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTopBar(
	viewModel: AppsViewModel,
	excludeSystem: Boolean,
	excludeAppStore: Boolean,
	excludeDisabled: Boolean,
	isSearchActive: Boolean,
	searchQuery: String,
	onSearchToggle: (Boolean) -> Unit
) = TopAppBar(
	title = {
		if (isSearchActive) {
			val focusRequester = remember { FocusRequester() }
			var localSearchText by rememberSaveable { mutableStateOf(searchQuery) }

			OutlinedTextField(
				value = localSearchText,
				onValueChange = { localSearchText = it },
				modifier = Modifier
					.fillMaxWidth()
					.focusRequester(focusRequester),
				placeholder = { Text(stringResource(R.string.tab_search)) },
				singleLine = true,
				colors = OutlinedTextFieldDefaults.colors(
					focusedBorderColor = Color.Transparent,
					unfocusedBorderColor = Color.Transparent
				)
			)

			LaunchedEffect(localSearchText) {
				if (localSearchText != searchQuery) {
					delay(100)
					viewModel.onSearchQueryChange(localSearchText)
				}
			}

			LaunchedEffect(isSearchActive) {
				if (isSearchActive) {
					focusRequester.requestFocus()
				}
			}
		} else {
			Text(stringResource(R.string.tab_apps), style = MaterialTheme.typography.titleLarge)
		}
	},
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	actions = {
		if (isSearchActive) {
			IconButton(onClick = { 
				viewModel.onSearchQueryChange("")
				onSearchToggle(false) 
			}) {
				Icon(Icons.Filled.Close, contentDescription = "Close Search")
			}
		} else {
			IconButton(onClick = { onSearchToggle(true) }) {
				Icon(Icons.Filled.Search, contentDescription = "Search")
			}
			IconButton(onClick = { viewModel.onSystemClick() }) {
				ExcludeSystemIcon(excludeSystem)
			}
			IconButton(onClick = { viewModel.onAppStoreClick() }) {
				ExcludeAppStoreIcon(excludeAppStore)
			}
			IconButton(onClick = { viewModel.onDisabledClick() }) {
				ExcludeDisabledIcon(excludeDisabled)
			}
		}
	}
)

