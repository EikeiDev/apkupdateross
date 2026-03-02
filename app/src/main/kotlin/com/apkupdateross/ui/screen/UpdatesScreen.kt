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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.items
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.UpdatesUiState
import androidx.compose.ui.text.font.FontWeight
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.EmptyGrid
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.RefreshIcon
import com.apkupdateross.ui.component.UpdateItem
import com.apkupdateross.ui.theme.statusBarColor
import androidx.compose.ui.unit.dp
import com.apkupdateross.viewmodel.UpdatesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	val state = viewModel.state().collectAsStateWithLifecycle().value
	val isRefreshing = viewModel.isRefreshing().collectAsStateWithLifecycle().value
	val selfUpdate = viewModel.selfUpdate().collectAsStateWithLifecycle().value
	val uriHandler = LocalUriHandler.current

	LaunchedEffect(Unit) {
		if (viewModel.state().value is UpdatesUiState.Loading) viewModel.refresh()
	}

	Column {
		UpdatesTopBar(viewModel)
		SelfUpdateDialog(
			update = selfUpdate,
			onUpdate = { viewModel.install(it, uriHandler) },
			onLater = { viewModel.snoozeSelfUpdate(it.versionCode) }
		)
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
fun UpdatesTopBar(viewModel: UpdatesViewModel) = TopAppBar(
	title = {
		Text(stringResource(R.string.tab_updates), style = MaterialTheme.typography.titleLarge)
	},
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	actions = {
		IconButton(onClick = { viewModel.refresh() }) {
			RefreshIcon(stringResource(R.string.refresh_updates))
		}
	}
)

@Composable
fun Grid(
	viewModel: UpdatesViewModel,
	updates: List<AppUpdate>,
	handler: UriHandler
) = InstalledGrid {
	items(updates) { update ->
		UpdateItem(
			update,
			{ viewModel.install(update, handler) },
			{ viewModel.ignoreVersion(update.id)},
			{ viewModel.cancel(update) }
		)
	}
}
