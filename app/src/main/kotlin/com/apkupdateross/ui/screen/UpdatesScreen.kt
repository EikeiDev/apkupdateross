package com.apkupdateross.ui.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import com.apkupdateross.ui.component.TvAwareButton as Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.Source
import com.apkupdateross.data.ui.UpdatesUiState
import com.apkupdateross.ui.activity.UptodownDownloadActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.apkupdateross.ui.component.AppScreen
import com.apkupdateross.ui.component.AppStatusBadge
import com.apkupdateross.ui.component.AppTone
import com.apkupdateross.ui.component.AppTopBar
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.EmptyGrid
import com.apkupdateross.ui.component.GridItem
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.RefreshIcon
import com.apkupdateross.ui.component.ReleaseNotesContent
import com.apkupdateross.ui.component.SourceStatusChipsRow
import com.apkupdateross.ui.component.SwipeToIgnoreBox
import com.apkupdateross.ui.component.tvFocusOutline
import com.apkupdateross.ui.component.UpdateItem
import androidx.compose.ui.unit.dp
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.viewmodel.UpdatesSortMode
import com.apkupdateross.viewmodel.UpdatesViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val isRefreshing = viewModel.isRefreshing.collectAsStateWithLifecycle().value
	val isInstallingAll = viewModel.isInstallingAll.collectAsStateWithLifecycle().value
	val selfUpdate = viewModel.selfUpdate.collectAsStateWithLifecycle().value
	val loadingSources by viewModel.loadingSources.collectAsStateWithLifecycle()
	val failedSources by viewModel.failedSources.collectAsStateWithLifecycle()
	val selectedSourceFilter by viewModel.selectedSourceFilter.collectAsStateWithLifecycle()
	val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
	val availableSources by viewModel.availableSources.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val uriHandler = LocalUriHandler.current
	val visibleUpdates = (state as? UpdatesUiState.Success)?.updates.orEmpty()
	val installAllCount = viewModel.installAllCount(visibleUpdates)

	LaunchedEffect(Unit) {
		viewModel.onScreenEntered()
	}

	AppScreen {
		UpdatesTopBar(viewModel)
		SelfUpdateDialog(
			update = selfUpdate,
			onUpdate = {
				viewModel.snoozeSelfUpdate(it.versionCode)
				viewModel.install(it, uriHandler)
			},
			onLater = { viewModel.snoozeSelfUpdate(it.versionCode) }
		)
		SourceStatusChipsRow(
			loadingSources = loadingSources,
			failedSources = failedSources,
			onRetrySource = viewModel::retrySource
		)
		if (visibleUpdates.isNotEmpty() || selectedSourceFilter != null) {
			UpdatesFilterSortRow(
				availableSources = availableSources,
				selectedSourceFilter = selectedSourceFilter,
				sortMode = sortMode,
				onSelectSource = viewModel::setSourceFilter,
				onSelectSortMode = viewModel::setSortMode
			)
		}
		PullToRefreshBox(
			isRefreshing = isRefreshing,
			onRefresh = { viewModel.refresh() },
			modifier = Modifier.fillMaxSize()
		) {
			state.onLoading {
				LoadingGrid()
			}.onError {
				DefaultErrorScreen()
			}.onSuccess {
				when {
					it.updates.isEmpty() && isRefreshing -> EmptyGrid(
						text = stringResource(R.string.checking_updates),
						tone = AppTone.Accent,
						loading = true
					)
					it.updates.isEmpty() -> EmptyGrid(
						text = stringResource(R.string.updates_empty),
						icon = R.drawable.ic_state_up_to_date,
						tone = AppTone.Success
					)
					else -> Grid(
						viewModel = viewModel,
						updates = it.updates,
						context = context,
						handler = uriHandler,
						installAllCount = installAllCount,
						isInstallingAll = isInstallingAll,
						onInstallAll = { viewModel.installAll(visibleUpdates) },
						onCancelInstallAll = { viewModel.cancelInstallAll() }
					)
				}
			}
		}
	}
}

@Composable
private fun SelfUpdateDialog(
	update: AppUpdate?,
	onUpdate: (AppUpdate) -> Unit,
	onLater: (AppUpdate) -> Unit
) {
	if (update == null) return
	val scrollState = rememberScrollState()
	AlertDialog(
		onDismissRequest = { onLater(update) },
		confirmButton = {
			Button(onClick = { onUpdate(update) }) {
				Text(text = stringResource(R.string.self_update_button_update))
			}
		},
		dismissButton = {
			TextButton(onClick = { onLater(update) }) {
				Text(text = stringResource(R.string.self_update_button_later))
			}
		},
		title = {
			Text(
				text = stringResource(R.string.self_update_title),
				style = MaterialTheme.typography.titleLarge,
				color = ApkTheme.colors.textPrimary
			)
		},
		text = {
			Column(modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)) {
				Text(
					text = stringResource(R.string.self_update_message, update.version, update.versionCode),
					style = MaterialTheme.typography.bodyLarge,
					color = ApkTheme.colors.textSecondary,
					fontWeight = FontWeight.Medium
				)
				if (update.whatsNew.isNotBlank()) {
					Text(
						text = stringResource(R.string.self_update_whats_new),
						style = MaterialTheme.typography.titleMedium,
						color = ApkTheme.colors.textPrimary,
						modifier = Modifier.padding(top = 16.dp)
					)
					ReleaseNotesContent(
						markdown = update.whatsNew,
						modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
						stripSelfUpdateHeader = true
					)
				}
			}
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesTopBar(viewModel: UpdatesViewModel) {
	var isSearchMode by rememberSaveable { mutableStateOf(false) }
	val query by viewModel.filterQuery.collectAsStateWithLifecycle()
	val focusRequester = remember { FocusRequester() }

	if (isSearchMode) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding()
				.heightIn(min = 64.dp)
				.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			IconButton(onClick = {
				isSearchMode = false
				viewModel.setFilterQuery("")
			}) {
				Icon(
					Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = stringResource(R.string.back),
					tint = ApkTheme.colors.textSecondary
				)
			}
			OutlinedTextField(
				value = query,
				onValueChange = { viewModel.setFilterQuery(it) },
				modifier = Modifier
					.weight(1f)
					.focusRequester(focusRequester),
				placeholder = { Text(stringResource(R.string.filter_updates)) },
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
				),
				shape = ApkTheme.shapes.lg,
				maxLines = 1,
				singleLine = true,
				trailingIcon = {
					if (query.isNotEmpty()) {
						IconButton(onClick = { viewModel.setFilterQuery("") }) {
							Icon(
								Icons.Default.Close,
								contentDescription = stringResource(R.string.clear),
								tint = ApkTheme.colors.textSecondary
							)
						}
					}
				}
			)
		}
		LaunchedEffect(Unit) {
			focusRequester.requestFocus()
		}
	} else {
		val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
		AppTopBar(title = stringResource(R.string.tab_updates)) {
			IconButton(onClick = { isSearchMode = true }) {
				Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tab_search))
			}
			if (isRefreshing) {
				IconButton(onClick = { viewModel.cancelRefresh() }) {
					Icon(
						Icons.Default.Close,
						contentDescription = stringResource(R.string.cancel_refresh_updates),
						tint = ApkTheme.colors.accent
					)
				}
			} else {
				IconButton(onClick = { viewModel.refresh() }) {
					RefreshIcon(stringResource(R.string.refresh_updates))
				}
			}
		}
	}
}

@Composable
fun Grid(
	viewModel: UpdatesViewModel,
	updates: List<GroupedAppUpdate>,
	context: Context,
	handler: UriHandler,
	installAllCount: Int,
	isInstallingAll: Boolean,
	onInstallAll: () -> Unit,
	onCancelInstallAll: () -> Unit
) {
	val compactMode by viewModel.useCompactView.collectAsStateWithLifecycle()
	val portraitColumns by viewModel.portraitColumns.collectAsStateWithLifecycle()
	val landscapeColumns by viewModel.landscapeColumns.collectAsStateWithLifecycle()
	val swipeIgnoreEnabled by viewModel.swipeIgnoreEnabled.collectAsStateWithLifecycle()
	val swipeIgnoreDirection by viewModel.swipeIgnoreDirection.collectAsStateWithLifecycle()
	val pinnedAppSources by viewModel.pinnedAppSources.collectAsStateWithLifecycle()
	var pendingDownload by remember { mutableStateOf<AppUpdate?>(null) }
	val requestStoragePermission = rememberStoragePermissionAction {
		pendingDownload?.let { downloadUpdate(context, viewModel, it) }
		pendingDownload = null
	}
	val downloadAction: (AppUpdate) -> Unit = { update ->
		pendingDownload = update
		requestStoragePermission()
	}

	InstalledGrid(
		compactMode = compactMode,
		portraitColumns = portraitColumns,
		landscapeColumns = landscapeColumns
	) {
		if (installAllCount > 0 || isInstallingAll) {
			item(span = { GridItemSpan(maxLineSpan) }) {
				InstallAllButton(
					count = installAllCount,
					isInstalling = isInstallingAll,
					onClick = if (isInstallingAll) onCancelInstallAll else onInstallAll
				)
			}
		}
		items(updates, key = { it.packageName }) { grouped ->
			val update = grouped.primary
			SwipeToIgnoreBox(
				confirmLabel = stringResource(R.string.hide_update),
				onIgnore = { viewModel.ignoreVersion(update.id) },
				modifier = Modifier.animateItem(),
				enabled = swipeIgnoreEnabled,
				swipeDirection = swipeIgnoreDirection,
				dismissOnIgnore = true,
				shape = ApkTheme.shapes.md
			) {
				if (compactMode) {
					GridItem(
						packageName = update.packageName,
						name = update.name,
						version = update.version,
						uri = null,
						source = update.source,
						onIgnore = { viewModel.ignoreVersion(update.id) },
						onOpenPage = { viewModel.openSourcePage(update, handler) },
						onClick = { installUpdate(context, viewModel, update, handler) },
						updates = grouped.updates,
						onUpdateIgnore = { viewModel.ignoreVersion(it) },
						onUpdateOpenPage = { viewModel.openSourcePage(it, handler) },
						onUpdateClick = { installUpdate(context, viewModel, it, handler) }
					)
				} else {
					UpdateItem(
						grouped,
						compactMode,
						{ installUpdate(context, viewModel, it, handler) },
						{ viewModel.ignoreVersion(it)},
						{ viewModel.cancel(it) },
						onDownload = downloadAction,
						onOpenPage = { viewModel.openSourcePage(it, handler) },
						pinnedSourceName = pinnedAppSources[grouped.packageName],
						onTogglePinSource = { viewModel.togglePinSource(it) }
					)
				}
			}
		}
	}
}

private fun installUpdate(
	context: Context,
	viewModel: UpdatesViewModel,
	update: AppUpdate,
	handler: UriHandler
) {
	if (update.link is Link.BrowserDownload) {
		context.startActivity(UptodownDownloadActivity.intent(context, update, UptodownDownloadActivity.Mode.Install))
	} else {
		viewModel.install(update, handler)
	}
}

private fun downloadUpdate(
	context: Context,
	viewModel: UpdatesViewModel,
	update: AppUpdate
) {
	if (update.link is Link.BrowserDownload) {
		context.startActivity(UptodownDownloadActivity.intent(context, update, UptodownDownloadActivity.Mode.Save))
	} else {
		viewModel.downloadToStorage(update)
	}
}

@Composable
private fun InstallAllButton(
	count: Int,
	isInstalling: Boolean,
	onClick: () -> Unit
) {
	val enabled = count > 0 || isInstalling
	val accentColor = if (isInstalling) ApkTheme.colors.error else ApkTheme.colors.accent
	val containerColor = when {
		isInstalling -> ApkTheme.colors.error.copy(alpha = 0.16f)
		enabled -> ApkTheme.colors.surfaceHighlight
		else -> ApkTheme.colors.surfaceSecondary
	}
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.tvFocusOutline(ApkTheme.shapes.lg, enabled),
		shape = ApkTheme.shapes.lg,
		color = containerColor,
		contentColor = if (enabled) accentColor else ApkTheme.colors.disabled,
		border = androidx.compose.foundation.BorderStroke(
			1.dp,
			if (enabled) accentColor.copy(alpha = 0.42f) else ApkTheme.colors.divider
		)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable(enabled = enabled, onClick = onClick)
				.padding(horizontal = 16.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically
		) {
			if (isInstalling) {
				CircularProgressIndicator(
					Modifier.size(18.dp),
					color = accentColor,
					strokeWidth = 2.dp
				)
			} else {
				Icon(
					painter = painterResource(R.drawable.ic_install),
					contentDescription = null,
					modifier = Modifier.size(18.dp)
				)
			}
			Spacer(Modifier.width(8.dp))
			Text(
				text = when {
					isInstalling -> stringResource(R.string.settings_custom_repo_cancel)
					count > 0 -> "${stringResource(R.string.update_all)} ($count)"
					else -> stringResource(R.string.update_all)
				},
				style = MaterialTheme.typography.labelLarge
			)
		}
	}
}

@Composable
private fun UpdatesFilterSortRow(
	availableSources: List<Source>,
	selectedSourceFilter: String?,
	sortMode: UpdatesSortMode,
	onSelectSource: (String?) -> Unit,
	onSelectSortMode: (UpdatesSortMode) -> Unit
) {
	var sortMenuExpanded by remember { mutableStateOf(false) }
	val sortLabel = when (sortMode) {
		UpdatesSortMode.NAME -> stringResource(R.string.sort_by_name)
		UpdatesSortMode.SOURCE -> stringResource(R.string.sort_by_source)
	}
	LazyRow(
		modifier = Modifier.fillMaxWidth(),
		contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		item(key = "sort_chip") {
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
						UpdatesSortMode.NAME to R.string.sort_by_name,
						UpdatesSortMode.SOURCE to R.string.sort_by_source
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
		if (availableSources.size > 1 || selectedSourceFilter != null) {
			item(key = "filter_all") {
				FilterChip(
					selected = selectedSourceFilter == null,
					onClick = { onSelectSource(null) },
					label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelMedium) },
					colors = FilterChipDefaults.filterChipColors(
						selectedContainerColor = ApkTheme.colors.accent.copy(alpha = 0.18f),
						selectedLabelColor = ApkTheme.colors.accent
					)
				)
			}
			items(availableSources, key = { "source_${it.name}" }) { source ->
				FilterChip(
					selected = selectedSourceFilter == source.name,
					onClick = { onSelectSource(source.name) },
					label = { Text(source.name, style = MaterialTheme.typography.labelMedium) },
					colors = FilterChipDefaults.filterChipColors(
						selectedContainerColor = ApkTheme.colors.accent.copy(alpha = 0.18f),
						selectedLabelColor = ApkTheme.colors.accent
					)
				)
			}
		}
	}
}
