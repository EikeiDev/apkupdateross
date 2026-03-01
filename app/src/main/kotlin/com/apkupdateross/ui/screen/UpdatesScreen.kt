package com.apkupdateross.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.items
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.EmptyGrid
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.RefreshIcon
import com.apkupdateross.ui.component.UpdateItem
import com.apkupdateross.ui.theme.statusBarColor
import com.apkupdateross.viewmodel.UpdatesViewModel


@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		UpdatesScreenLoading(viewModel)
	}.onError {
		UpdatesScreenError()
	}.onSuccess {
		UpdatesScreenSuccess(viewModel, it.updates)
	}
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
fun UpdatesScreenLoading(viewModel: UpdatesViewModel) = Column {
	UpdatesTopBar(viewModel)
	LoadingGrid()
}

@Composable
fun UpdatesScreenError() = DefaultErrorScreen()

@Composable
fun UpdatesScreenSuccess(
	viewModel: UpdatesViewModel,
	updates: List<AppUpdate>
) = Column {
	val handler = LocalUriHandler.current

	UpdatesTopBar(viewModel)

	when {
		updates.isEmpty() -> EmptyGrid()
		else -> Grid(viewModel, updates, handler)
	}
}

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
