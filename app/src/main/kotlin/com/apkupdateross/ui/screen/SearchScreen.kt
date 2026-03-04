package com.apkupdateross.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdateross.R
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.SearchUiState
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.SearchItem
import com.apkupdateross.ui.theme.statusBarColor
import com.apkupdateross.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel()
) = Column {
    SearchTopBar(viewModel)
    viewModel.state().collectAsStateWithLifecycle().value.onError {
        DefaultErrorScreen()
    }.onSuccess {
        SearchScreenSuccess(it, viewModel)
    }.onLoading {
        LoadingGrid()
    }
}

@Composable
fun SearchScreenSuccess(
    state: SearchUiState.Success,
    viewModel: SearchViewModel
) = Column {
    val uriHandler = LocalUriHandler.current
    val updates = state.updates

    InstalledGrid {
        items(updates) { update ->
            SearchItem(update, {
                viewModel.install(update, uriHandler)
            }, { viewModel.cancel(update) })
        }
    }

    if (updates.isEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        SearchNoResultsBanner()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(viewModel: SearchViewModel) = TopAppBar(
    title = { SearchText(viewModel) },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
    actions = { SearchFilterAction(viewModel) }
)

@Composable
fun SearchText(viewModel: SearchViewModel) = Box {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var value by rememberSaveable { mutableStateOf("") }
    fun submitSearch() {
        val query = value.trim()
        if (query.length >= 3) {
            viewModel.search(query)
            keyboardController?.hide()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth().padding(0.dp).focusRequester(focusRequester),
        label = { Text(stringResource(R.string.tab_search)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        maxLines = 1,
        singleLine = true
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(value) {
        val query = value.trim()
        if (query.length >= MIN_SEARCH_LENGTH) {
            delay(SEARCH_DEBOUNCE_MS)
            viewModel.search(query)
        }
    }
}

private const val SEARCH_DEBOUNCE_MS = 500L
private const val MIN_SEARCH_LENGTH = 3

@Composable
private fun SearchFilterAction(viewModel: SearchViewModel) {
    val currentFilters by viewModel.filters().collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.search_filter_button)
        )
    }

    if (showDialog) {
        SearchFilterDialog(
            selected = currentFilters,
            onDismiss = { showDialog = false },
            onSelect = {
                showDialog = false
                viewModel.setFilters(it)
            }
        )
    }
}

@Composable
private fun SearchFilterDialog(
    selected: Set<SearchSourceFilter>,
    onDismiss: () -> Unit,
    onSelect: (Set<SearchSourceFilter>) -> Unit
) {
    var current by remember(selected) { mutableStateOf(selected.ifEmpty { SearchSourceFilter.defaultSelection }) }

    fun updateSelection(filter: SearchSourceFilter, enabled: Boolean) {
        current = when {
            enabled -> current + filter
            !enabled && current.contains(filter) && current.size > 1 -> current - filter
            else -> current
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSelect(current) }) {
                Text(stringResource(R.string.search_filter_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.search_filter_cancel))
            }
        },
        title = { Text(stringResource(R.string.search_filter_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val allSelected = current.size == SearchSourceFilter.entries.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { current = if (allSelected) emptySet() else SearchSourceFilter.defaultSelection },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            current = if (checked) SearchSourceFilter.defaultSelection else emptySet()
                        }
                    )
                    Text(text = stringResource(R.string.search_filter_toggle_all))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SearchSourceFilter.entries.forEach { filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { updateSelection(filter, !current.contains(filter)) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = current.contains(filter),
                                onCheckedChange = { checked -> updateSelection(filter, checked) }
                            )
                            Text(text = stringResource(filter.labelRes))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.search_filter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun SearchNoResultsBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = stringResource(R.string.search_no_results),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
