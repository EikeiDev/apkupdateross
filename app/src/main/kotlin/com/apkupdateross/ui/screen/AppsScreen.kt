package com.apkupdateross.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.apkupdateross.viewmodel.AppsSortMode
import com.apkupdateross.viewmodel.AppsViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
	viewModel: AppsViewModel = koinViewModel(),
	onCheckAppUpdates: ((String) -> Unit)? = null,
	onInstallLocalPackage: ((Uri) -> Unit)? = null
) {
	val state = viewModel.state.collectAsStateWithLifecycle().value
	val compactMode by viewModel.useCompactView.collectAsStateWithLifecycle()
	val portraitColumns by viewModel.portraitColumns.collectAsStateWithLifecycle()
	val landscapeColumns by viewModel.landscapeColumns.collectAsStateWithLifecycle()
	val swipeIgnoreEnabled by viewModel.swipeIgnoreEnabled.collectAsStateWithLifecycle()
	val swipeIgnoreDirection by viewModel.swipeIgnoreDirection.collectAsStateWithLifecycle()
	val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
	val context = LocalContext.current
	var pendingBackupApp by remember { mutableStateOf<AppInstalled?>(null) }
	val requestStoragePermission = rememberStoragePermissionAction {
		pendingBackupApp?.let { viewModel.backupApk(it) }
		pendingBackupApp = null
	}
	val backupAction: (AppInstalled) -> Unit = { app ->
		pendingBackupApp = app
		requestStoragePermission()
	}
	val localPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		uri?.let { onInstallLocalPackage?.invoke(it) }
	}

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
		AppsQuickActionsRow(
			sortMode = sortMode,
			onSelectSortMode = viewModel::setSortMode,
			onPickLocalPackage = if (onInstallLocalPackage != null) {
				{ localPackageLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "*/*")) }
			} else null
		)
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
							confirmLabel = stringResource(R.string.hide_app_updates),
							onIgnore = { viewModel.ignore(app.packageName) },
							modifier = Modifier.animateItem(),
							enabled = swipeIgnoreEnabled && !app.ignored,
							swipeDirection = swipeIgnoreDirection,
							dismissOnIgnore = false,
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
								InstalledItem(
									app = app,
									compactMode = compactMode,
									onIgnore = { viewModel.ignore(app.packageName) },
									onCheckUpdate = onCheckAppUpdates,
									onBackupApk = backupAction
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun AppsQuickActionsRow(
	sortMode: AppsSortMode,
	onSelectSortMode: (AppsSortMode) -> Unit,
	onPickLocalPackage: (() -> Unit)?
) {
	var sortMenuExpanded by remember { mutableStateOf(false) }
	val sortLabel = when (sortMode) {
		AppsSortMode.NAME -> stringResource(R.string.sort_by_name)
		AppsSortMode.UPDATED_DESC -> stringResource(R.string.sort_by_updated)
		AppsSortMode.IGNORED_FIRST -> stringResource(R.string.sort_by_ignored)
	}
	LazyRow(
		modifier = Modifier.fillMaxWidth(),
		contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		item(key = "apps_sort") {
			Box {
				AssistChip(
					onClick = { sortMenuExpanded = true },
					label = { Text(sortLabel, style = MaterialTheme.typography.labelMedium) },
					trailingIcon = {
						Icon(
							Icons.Default.KeyboardArrowDown,
							contentDescription = null,
							modifier = Modifier.size(16.dp)
						)
					},
					colors = AssistChipDefaults.assistChipColors(
						containerColor = ApkTheme.colors.surfaceElevated,
						labelColor = ApkTheme.colors.textPrimary,
						trailingIconContentColor = ApkTheme.colors.textSecondary
					),
					border = AssistChipDefaults.assistChipBorder(
						enabled = true,
						borderColor = ApkTheme.colors.divider
					)
				)
				DropdownMenu(
					expanded = sortMenuExpanded,
					onDismissRequest = { sortMenuExpanded = false }
				) {
					listOf(
						AppsSortMode.NAME to R.string.sort_by_name,
						AppsSortMode.UPDATED_DESC to R.string.sort_by_updated,
						AppsSortMode.IGNORED_FIRST to R.string.sort_by_ignored
					).forEach { (mode, labelRes) ->
						DropdownMenuItem(
							text = { Text(stringResource(labelRes)) },
							trailingIcon = if (sortMode == mode) {
								{ Icon(Icons.Default.Check, contentDescription = null, tint = ApkTheme.colors.accent) }
							} else null,
							onClick = {
								onSelectSortMode(mode)
								sortMenuExpanded = false
							}
						)
					}
				}
			}
		}
		if (onPickLocalPackage != null) {
			item(key = "local_install") {
				AssistChip(
					onClick = onPickLocalPackage,
					label = { Text(stringResource(R.string.install_local_package_short), style = MaterialTheme.typography.labelMedium) },
					leadingIcon = {
						Icon(
							painter = painterResource(R.drawable.ic_install),
							contentDescription = null,
							modifier = Modifier.size(15.dp)
						)
					},
					colors = AssistChipDefaults.assistChipColors(
						containerColor = ApkTheme.colors.surfaceHighlight,
						labelColor = ApkTheme.colors.accent,
						leadingIconContentColor = ApkTheme.colors.accent
					),
					border = AssistChipDefaults.assistChipBorder(
						enabled = true,
						borderColor = ApkTheme.colors.accent.copy(alpha = 0.35f)
					)
				)
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
