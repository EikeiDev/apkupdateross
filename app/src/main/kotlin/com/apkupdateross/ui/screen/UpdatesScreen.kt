package com.apkupdateross.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.lazy.grid.items
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.UpdatesUiState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.EmptyGrid
import com.apkupdateross.ui.component.GridItem
import com.apkupdateross.ui.component.GridItem
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.RefreshIcon
import com.apkupdateross.ui.component.UpdateItem
import com.apkupdateross.ui.theme.statusBarColor
import androidx.compose.ui.unit.dp
import com.apkupdateross.viewmodel.UpdatesViewModel
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
import com.apkupdateross.data.ui.Source

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val isRefreshing = viewModel.isRefreshing.collectAsStateWithLifecycle().value
	val selfUpdate = viewModel.selfUpdate.collectAsStateWithLifecycle().value
	val loadingSources by viewModel.loadingSources.collectAsStateWithLifecycle()
	val failedSources by viewModel.failedSources.collectAsStateWithLifecycle()
	val uriHandler = LocalUriHandler.current

	LaunchedEffect(Unit) {
		if (state is UpdatesUiState.Loading) viewModel.refresh()
	}

	Column {
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
					else -> Grid(viewModel, it.updates, uriHandler)
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
					Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
						unfocusedBorderColor = Color.Transparent
					),
					maxLines = 1,
					singleLine = true,
					trailingIcon = {
						if (query.isNotEmpty()) {
							IconButton(onClick = { viewModel.setFilterQuery("") }) {
								Icon(Icons.Default.Close, contentDescription = "Clear")
							}
						}
					}
				)
				LaunchedEffect(Unit) {
					focusRequester.requestFocus()
				}
			} else {
				Text(stringResource(R.string.tab_updates), style = MaterialTheme.typography.titleLarge)
			}
		},
		colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
		actions = {
			if (!isSearchMode) {
				IconButton(onClick = { isSearchMode = true }) {
					Icon(Icons.Default.Search, contentDescription = "Search")
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
	handler: UriHandler
) {
	val compactMode by viewModel.useCompactView.collectAsStateWithLifecycle()
	val portraitColumns by viewModel.portraitColumns.collectAsStateWithLifecycle()
	val landscapeColumns by viewModel.landscapeColumns.collectAsStateWithLifecycle()

	InstalledGrid(
		compactMode = compactMode,
		portraitColumns = portraitColumns,
		landscapeColumns = landscapeColumns
	) {
		items(updates, key = { it.packageName }) { grouped ->
			val update = grouped.primary
			SwipeToIgnoreBox(
				onIgnore = { viewModel.ignoreVersion(update.id) },
				shape = androidx.compose.foundation.shape.RoundedCornerShape(if (compactMode) 12.dp else 16.dp)
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
						onClick = { viewModel.install(update, handler) },
						updates = grouped.updates,
						onUpdateIgnore = { viewModel.ignoreVersion(it) },
						onUpdateOpenPage = { viewModel.openSourcePage(it, handler) },
						onUpdateClick = { viewModel.install(it, handler) }
					)
				} else {
					UpdateItem(
						grouped,
						compactMode,
						{ viewModel.install(it, handler) },
						{ viewModel.ignoreVersion(it)},
						{ viewModel.cancel(it) },
						onDownload = { viewModel.downloadToStorage(it) },
						onOpenPage = { viewModel.openSourcePage(it, handler) }
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToIgnoreBox(
	onIgnore: () -> Unit,
	shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
	content: @Composable () -> Unit
) {
	val dismissState = rememberSwipeToDismissBoxState(
		confirmValueChange = { dismissValue ->
			if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
				onIgnore()
				true
			} else {
				false
			}
		}
	)

	SwipeToDismissBox(
		state = dismissState,
		backgroundContent = {
			val color by animateColorAsState(
				when (dismissState.targetValue) {
					SwipeToDismissBoxValue.Settled -> Color.Transparent
					else -> MaterialTheme.colorScheme.errorContainer
				}
			)
			Box(
				Modifier
					.fillMaxSize()
					.clip(shape)
					.background(color)
					.padding(horizontal = 20.dp),
				contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
			) {
				Icon(
					Icons.Default.Delete,
					contentDescription = "Ignore",
					tint = MaterialTheme.colorScheme.onErrorContainer
				)
			}
		},
		content = {
			content()
		}
	)
}
