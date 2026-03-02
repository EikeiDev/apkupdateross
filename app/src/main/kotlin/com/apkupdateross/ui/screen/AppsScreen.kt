package com.apkupdateross.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.apkupdateross.R
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

	Column {
		AppsTopBar(viewModel, excludeSystem, excludeAppStore, excludeDisabled)
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
	excludeDisabled: Boolean
) = TopAppBar(
	title = { Text(stringResource(R.string.tab_apps), style = MaterialTheme.typography.titleLarge) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	actions = {
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
)

