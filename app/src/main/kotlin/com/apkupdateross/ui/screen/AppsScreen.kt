package com.apkupdateross.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppsUiState
import com.apkupdateross.ui.component.AppScreen
import com.apkupdateross.ui.component.AppTopBar
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.ExcludeAppStoreIcon
import com.apkupdateross.ui.component.ExcludeDisabledIcon
import com.apkupdateross.ui.component.ExcludeSystemIcon
import com.apkupdateross.ui.component.GridItem
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.InstalledItem
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.SwipeToIgnoreBox
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.viewmodel.AppsViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
	viewModel: AppsViewModel = koinViewModel()
) {
	val state = viewModel.state.collectAsStateWithLifecycle().value
	val compactMode by viewModel.useCompactView.collectAsStateWithLifecycle()
	val portraitColumns by viewModel.portraitColumns.collectAsStateWithLifecycle()
	val landscapeColumns by viewModel.landscapeColumns.collectAsStateWithLifecycle()
	val swipeIgnoreEnabled by viewModel.swipeIgnoreEnabled.collectAsStateWithLifecycle()
	val swipeIgnoreDirection by viewModel.swipeIgnoreDirection.collectAsStateWithLifecycle()
	val context = LocalContext.current

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

	AppScreen {
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
				InstalledGrid(
					compactMode = compactMode,
					portraitColumns = portraitColumns,
					landscapeColumns = landscapeColumns
				) {
					items(it.apps, key = { app -> app.packageName }) { app ->
						SwipeToIgnoreBox(
							title = stringResource(R.string.ignore_app_title),
							message = stringResource(R.string.ignore_app_message),
							confirmLabel = stringResource(R.string.hide_app_updates),
							cancelLabel = stringResource(R.string.settings_custom_repo_cancel),
							onIgnore = { viewModel.ignore(app.packageName) },
							enabled = swipeIgnoreEnabled && !app.ignored,
							swipeDirection = swipeIgnoreDirection,
							shape = ApkTheme.shapes.md
						) {
							if (compactMode) {
								GridItem(
									packageName = app.packageName,
									name = app.name,
									version = app.version,
									uri = null,
									isIgnored = app.ignored,
									onIgnore = { viewModel.ignore(app.packageName) },
									onClick = {
										val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
										if (intent != null) {
											context.startActivity(intent)
										}
									}
								)
							} else {
								InstalledItem(app, compactMode) { viewModel.ignore(app.packageName) }
							}
						}
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
) {
	if (isSearchActive) {
		val focusRequester = remember { FocusRequester() }
		var localSearchText by rememberSaveable { mutableStateOf(searchQuery) }

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding()
				.heightIn(min = 64.dp)
				.padding(horizontal = 16.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			OutlinedTextField(
				value = localSearchText,
				onValueChange = { localSearchText = it },
				modifier = Modifier
					.weight(1f)
					.focusRequester(focusRequester),
				placeholder = { Text(stringResource(R.string.tab_search)) },
				singleLine = true,
				shape = ApkTheme.shapes.lg,
				colors = OutlinedTextFieldDefaults.colors(
					focusedBorderColor = ApkTheme.colors.accent.copy(alpha = 0.55f),
					unfocusedBorderColor = ApkTheme.colors.divider,
					focusedContainerColor = ApkTheme.colors.surfaceElevated,
					unfocusedContainerColor = ApkTheme.colors.surfaceElevated,
					focusedTextColor = ApkTheme.colors.textPrimary,
					unfocusedTextColor = ApkTheme.colors.textPrimary,
					focusedPlaceholderColor = ApkTheme.colors.textTertiary,
					unfocusedPlaceholderColor = ApkTheme.colors.textTertiary,
					cursorColor = ApkTheme.colors.accent
				)
			)
			IconButton(onClick = { 
				viewModel.onSearchQueryChange("")
				onSearchToggle(false) 
			}) {
				Icon(
					Icons.Filled.Close,
					contentDescription = stringResource(R.string.close_search),
					tint = ApkTheme.colors.textSecondary
				)
			}
		}

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
		AppTopBar(
			title = stringResource(R.string.tab_apps)
		) {
			IconButton(onClick = { onSearchToggle(true) }) {
				Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.tab_search))
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
}
