package com.apkupdateross.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
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
import com.apkupdateross.data.ui.UpdatesUiState
import com.apkupdateross.ui.activity.UptodownDownloadActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.EmptyGrid
import com.apkupdateross.ui.component.GridItem
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.RefreshIcon
import com.apkupdateross.ui.component.SwipeToIgnoreBox
import com.apkupdateross.ui.component.UpdateItem
import androidx.compose.ui.unit.dp
import com.apkupdateross.viewmodel.UpdatesViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val isRefreshing = viewModel.isRefreshing.collectAsStateWithLifecycle().value
	val isInstallingAll = viewModel.isInstallingAll.collectAsStateWithLifecycle().value
	val selfUpdate = viewModel.selfUpdate.collectAsStateWithLifecycle().value
	val loadingSources by viewModel.loadingSources.collectAsStateWithLifecycle()
	val failedSources by viewModel.failedSources.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val uriHandler = LocalUriHandler.current
	val visibleUpdates = (state as? UpdatesUiState.Success)?.updates.orEmpty()
	val installAllCount = viewModel.installAllCount(visibleUpdates)

	LaunchedEffect(Unit) {
		if (state is UpdatesUiState.Loading) viewModel.refresh()
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) {
		UpdatesTopBar(viewModel)
		SelfUpdateDialog(
			update = selfUpdate,
			onUpdate = { viewModel.install(it, uriHandler) },
			onLater = { viewModel.snoozeSelfUpdate(it.versionCode) }
		)
		AnimatedVisibility(
			visible = loadingSources.isNotEmpty() || failedSources.isNotEmpty(),
			enter = expandVertically(),
			exit = shrinkVertically()
		) {
			LazyRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
			) {
				items(loadingSources.toList(), key = { "loading_${it.name}" }) { source ->
					AssistChip(
						onClick = {},
						label = { Text(source.name, style = MaterialTheme.typography.labelSmall) },
						leadingIcon = { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp) }
					)
				}
				items(failedSources.toList(), key = { "failed_${it.name}" }) { source ->
					AssistChip(
						onClick = {},
						label = { Text(source.name, style = MaterialTheme.typography.labelSmall) },
						leadingIcon = {
							Icon(
								Icons.Filled.Warning,
								contentDescription = null,
								modifier = Modifier.size(12.dp),
								tint = MaterialTheme.colorScheme.error
							)
						},
						colors = AssistChipDefaults.assistChipColors(
							containerColor = MaterialTheme.colorScheme.errorContainer,
							labelColor = MaterialTheme.colorScheme.onErrorContainer
						)
					)
				}
			}
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
					it.updates.isEmpty() && isRefreshing -> EmptyGrid(text = stringResource(R.string.checking_updates))
					it.updates.isEmpty() -> EmptyGrid(text = stringResource(R.string.updates_empty))
					else -> Grid(
						viewModel = viewModel,
						updates = it.updates,
						context = context,
						handler = uriHandler,
						installAllCount = installAllCount,
						isInstallingAll = isInstallingAll,
						onInstallAll = { viewModel.installAll(visibleUpdates) }
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
				style = MaterialTheme.typography.titleLarge
			)
		},
		text = {
			Column(modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)) {
				Text(
					text = stringResource(R.string.self_update_message, update.version, update.versionCode),
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium
				)
				if (update.whatsNew.isNotBlank()) {
					Text(
						text = stringResource(R.string.self_update_whats_new),
						style = MaterialTheme.typography.titleMedium,
						modifier = Modifier.padding(top = 16.dp)
					)
					Text(
						text = update.whatsNew.trim(),
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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

	TopAppBar(
		navigationIcon = {
			if (isSearchMode) {
				IconButton(onClick = {
					isSearchMode = false
					viewModel.setFilterQuery("")
				}) {
					Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
				}
			}
		},
		title = {
			if (isSearchMode) {
				OutlinedTextField(
					value = query,
					onValueChange = { viewModel.setFilterQuery(it) },
					modifier = Modifier
						.fillMaxWidth()
						.padding(0.dp)
						.focusRequester(focusRequester),
					placeholder = { Text(stringResource(R.string.filter_updates)) },
					colors = OutlinedTextFieldDefaults.colors(
						focusedBorderColor = Color.Transparent,
						unfocusedBorderColor = Color.Transparent,
						focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
						unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
					),
					shape = MaterialTheme.shapes.medium,
					maxLines = 1,
					singleLine = true,
					trailingIcon = {
						if (query.isNotEmpty()) {
							IconButton(onClick = { viewModel.setFilterQuery("") }) {
								Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
							}
						}
					}
				)
				LaunchedEffect(Unit) {
					focusRequester.requestFocus()
				}
			} else {
				Text(stringResource(R.string.tab_updates), style = MaterialTheme.typography.headlineSmall)
			}
		},
		colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
		actions = {
			if (!isSearchMode) {
				IconButton(onClick = { isSearchMode = true }) {
					Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tab_search))
				}
			}
			IconButton(onClick = { viewModel.refresh() }) {
				RefreshIcon(stringResource(R.string.refresh_updates))
			}
		}
	)
}

@Composable
fun Grid(
	viewModel: UpdatesViewModel,
	updates: List<GroupedAppUpdate>,
	context: Context,
	handler: UriHandler,
	installAllCount: Int,
	isInstallingAll: Boolean,
	onInstallAll: () -> Unit
) {
	val compactMode by viewModel.useCompactView.collectAsStateWithLifecycle()
	val portraitColumns by viewModel.portraitColumns.collectAsStateWithLifecycle()
	val landscapeColumns by viewModel.landscapeColumns.collectAsStateWithLifecycle()
	val swipeIgnoreEnabled by viewModel.swipeIgnoreEnabled.collectAsStateWithLifecycle()
	val swipeIgnoreDirection by viewModel.swipeIgnoreDirection.collectAsStateWithLifecycle()

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
					onClick = onInstallAll
				)
			}
		}
		items(updates, key = { it.packageName }) { grouped ->
			val update = grouped.primary
			SwipeToIgnoreBox(
				title = stringResource(R.string.ignore_update_title),
				message = stringResource(R.string.ignore_update_message),
				confirmLabel = stringResource(R.string.hide_update),
				cancelLabel = stringResource(R.string.settings_custom_repo_cancel),
				onIgnore = { viewModel.ignoreVersion(update.id) },
				enabled = swipeIgnoreEnabled,
				swipeDirection = swipeIgnoreDirection,
				shape = MaterialTheme.shapes.medium
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
						onDownload = { downloadUpdate(context, viewModel, it) },
						onOpenPage = { viewModel.openSourcePage(it, handler) }
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
	FilledTonalButton(
		onClick = onClick,
		enabled = count > 0 && !isInstalling,
		modifier = Modifier.fillMaxWidth()
	) {
		if (isInstalling) {
			CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
		} else {
			Icon(
				painter = painterResource(R.drawable.ic_install),
				contentDescription = null,
				modifier = Modifier.size(18.dp)
			)
		}
		Spacer(Modifier.width(8.dp))
		Text(
			text = if (count > 0) {
				"${stringResource(R.string.update_all)} ($count)"
			} else {
				stringResource(R.string.update_all)
			}
		)
	}
}
