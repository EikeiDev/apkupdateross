package com.apkupdateross.ui.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.SearchUiState
import com.apkupdateross.ui.activity.UptodownDownloadActivity
import com.apkupdateross.ui.component.AppScreen
import com.apkupdateross.ui.component.AppStateMessage
import com.apkupdateross.ui.component.AppTone
import com.apkupdateross.ui.component.DefaultErrorScreen
import com.apkupdateross.ui.component.DownloadChoiceDialog
import com.apkupdateross.ui.component.GridItem
import com.apkupdateross.ui.component.InstalledGrid
import com.apkupdateross.ui.component.LoadingGrid
import com.apkupdateross.ui.component.LocalTvMode
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.apkupdateross.ui.component.SearchItem
import com.apkupdateross.ui.component.SourceStatusChipsRow
import com.apkupdateross.ui.component.tvFocusOutline
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel()
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val compactMode by viewModel.useCompactView.collectAsStateWithLifecycle()
    val portraitColumns by viewModel.portraitColumns.collectAsStateWithLifecycle()
    val landscapeColumns by viewModel.landscapeColumns.collectAsStateWithLifecycle()
    val currentFilters by viewModel.filters.collectAsStateWithLifecycle()
    val loadingSources by viewModel.loadingSources.collectAsStateWithLifecycle()
    val failedSources by viewModel.failedSources.collectAsStateWithLifecycle()

    AppScreen {
        SearchTopBar(viewModel)
        SearchQuickFilterRow(
            selected = currentFilters,
            onToggle = viewModel::toggleFilter
        )
        SourceStatusChipsRow(
            loadingSources = loadingSources,
            failedSources = failedSources,
            onRetrySource = viewModel::retrySource
        )
        state.onError {
            DefaultErrorScreen()
        }.onSuccess {
            SearchScreenSuccess(it, compactMode, portraitColumns, landscapeColumns, viewModel)
        }.onLoading {
            LoadingGrid()
        }
    }
}

@Composable
private fun SearchQuickFilterRow(
    selected: Set<SearchSourceFilter>,
    onToggle: (SearchSourceFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
    ) {
        items(SearchSourceFilter.entries, key = { it.name }) { filter ->
            val isSelected = selected.contains(filter)
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.tvFocusOutline(ApkTheme.shapes.sm),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ApkTheme.colors.surfaceSecondary,
                    labelColor = ApkTheme.colors.textSecondary,
                    selectedContainerColor = ApkTheme.colors.surfaceHighlight,
                    selectedLabelColor = ApkTheme.colors.accent
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = ApkTheme.colors.divider,
                    selectedBorderColor = ApkTheme.colors.accent.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun SearchScreenSuccess(
    state: SearchUiState.Success,
    compactMode: Boolean,
    portraitColumns: Int,
    landscapeColumns: Int,
    viewModel: SearchViewModel
) = Column {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updates = state.updates
    var pendingDownload by remember { mutableStateOf<AppUpdate?>(null) }
    var choiceDialogState by remember { mutableStateOf<Pair<AppUpdate, Boolean>?>(null) }
    val requestStoragePermission = rememberStoragePermissionAction {
        pendingDownload?.let { downloadSearchUpdate(context, viewModel, it) }
        pendingDownload = null
    }
    val runDownload: (AppUpdate) -> Unit = { update ->
        pendingDownload = update
        requestStoragePermission()
    }
    val installAction: (AppUpdate) -> Unit = { update ->
        if (update.downloadChoices.size > 1) {
            choiceDialogState = update to false
        } else {
            installSearchUpdate(context, viewModel, update, uriHandler)
        }
    }
    val downloadAction: (AppUpdate) -> Unit = { update ->
        if (update.downloadChoices.size > 1) {
            choiceDialogState = update to true
        } else {
            runDownload(update)
        }
    }

    choiceDialogState?.let { (targetUpdate, isSaveMode) ->
        DownloadChoiceDialog(
            update = targetUpdate,
            onDismiss = { choiceDialogState = null },
            onLoadPostNotes = viewModel::loadFourPdaPostNotes,
            onOpenPost = { url -> runCatching { uriHandler.openUri(url) } },
            onSelect = { resolved ->
                choiceDialogState = null
                if (isSaveMode) runDownload(resolved) else installSearchUpdate(context, viewModel, resolved, uriHandler)
            }
        )
    }

    if (updates.isEmpty()) {
        AppStateMessage(
            title = stringResource(R.string.search_no_results),
            icon = R.drawable.ic_state_search,
            tone = AppTone.Accent
        )
        return@Column
    }

    InstalledGrid(
        compactMode = compactMode,
        portraitColumns = portraitColumns,
        landscapeColumns = landscapeColumns
    ) {
        items(updates, key = { it.packageName }) { grouped ->
            val update = grouped.primary
            if (compactMode) {
                GridItem(
                    packageName = update.packageName,
                    name = update.name,
                    version = update.version,
                    uri = update.iconUri,
                    source = update.source,
                    onClick = { installAction(update) },
                    updates = grouped.updates,
                    onUpdateOpenPage = { viewModel.openSourcePage(it, uriHandler) },
                    onUpdateClick = { installAction(it) }
                )
            } else {
                SearchItem(grouped, compactMode, installAction, { viewModel.cancel(it) },
                    onDownload = downloadAction,
                    onOpenPage = { viewModel.openSourcePage(it, uriHandler) }
                )
            }
        }
    }
}

private fun installSearchUpdate(
    context: Context,
    viewModel: SearchViewModel,
    update: AppUpdate,
    handler: androidx.compose.ui.platform.UriHandler
) {
    if (update.link is Link.BrowserDownload) {
        context.startActivity(UptodownDownloadActivity.intent(context, update, UptodownDownloadActivity.Mode.Install))
    } else {
        viewModel.install(update, handler)
    }
}

private fun downloadSearchUpdate(
    context: Context,
    viewModel: SearchViewModel,
    update: AppUpdate
) {
    if (update.link is Link.BrowserDownload) {
        context.startActivity(UptodownDownloadActivity.intent(context, update, UptodownDownloadActivity.Mode.Save))
    } else {
        viewModel.downloadToStorage(update)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(viewModel: SearchViewModel) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .heightIn(min = 64.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    SearchText(viewModel)
}

@Composable
fun SearchText(viewModel: SearchViewModel) = Box {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    val externalQuery by viewModel.query.collectAsStateWithLifecycle()
    var value by rememberSaveable { mutableStateOf(externalQuery) }
    LaunchedEffect(externalQuery) {
        if (externalQuery.isNotBlank() && externalQuery != value.trim()) {
            value = externalQuery
        }
    }
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
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.tab_search)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.tab_search),
                tint = ApkTheme.colors.textSecondary
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { 
                    value = ""
                    viewModel.search("") 
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear),
                        tint = ApkTheme.colors.textSecondary
                    )
                }
            } else {
                SearchFilterAction(viewModel)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
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
        ),
        maxLines = 1,
        singleLine = true
    )
    LaunchedEffect(Unit) {
        if (!isTv) focusRequester.requestFocus()
    }
    LaunchedEffect(value) {
        val query = value.trim()
        if (query.length >= MIN_SEARCH_LENGTH) {
            delay(SEARCH_DEBOUNCE_MS)
            viewModel.search(query)
        } else if (query.isEmpty() && externalQuery.isNotEmpty()) {
            viewModel.search("")
        }
    }
}

private const val SEARCH_DEBOUNCE_MS = 500L
private const val MIN_SEARCH_LENGTH = 3

@Composable
private fun SearchFilterAction(viewModel: SearchViewModel) {
    val currentFilters by viewModel.filters.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(R.string.search_filter_button),
            tint = ApkTheme.colors.textSecondary
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
            TextButton(
                enabled = current.isNotEmpty(),
                onClick = { onSelect(current) }
            ) {
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val allSelected = current.size == SearchSourceFilter.entries.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusOutline(ApkTheme.shapes.sm)
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
                                .tvFocusOutline(ApkTheme.shapes.sm)
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
                    color = ApkTheme.colors.textSecondary
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
        shape = ApkTheme.shapes.md,
        color = ApkTheme.colors.surfaceSecondary
    ) {
        Text(
            text = stringResource(R.string.search_no_results),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = ApkTheme.colors.textSecondary
        )
    }
}
