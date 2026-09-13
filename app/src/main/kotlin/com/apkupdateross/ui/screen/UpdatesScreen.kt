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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
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
import com.apkupdateross.ui.component.SwipeToIgnoreBox
import com.apkupdateross.ui.component.UpdateItem
import androidx.compose.ui.unit.dp
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.viewmodel.UpdatesViewModel
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
	val context = LocalContext.current
	val uriHandler = LocalUriHandler.current
	val visibleUpdates = (state as? UpdatesUiState.Success)?.updates.orEmpty()
	val installAllCount = viewModel.installAllCount(visibleUpdates)

	LaunchedEffect(Unit) {
		if (state is UpdatesUiState.Loading) viewModel.refresh()
	}

	AppScreen {
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
						leadingIcon = { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp) },
						colors = AssistChipDefaults.assistChipColors(
							containerColor = ApkTheme.colors.surfaceSecondary,
							labelColor = ApkTheme.colors.textSecondary
						)
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
								tint = ApkTheme.colors.error
							)
						},
						colors = AssistChipDefaults.assistChipColors(
							containerColor = ApkTheme.colors.error.copy(alpha = 0.18f),
							labelColor = ApkTheme.colors.error
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
					SelfUpdateReleaseNotes(
						markdown = update.whatsNew,
						modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
					)
				}
			}
		}
	)
}

@Composable
private fun SelfUpdateReleaseNotes(
	markdown: String,
	modifier: Modifier = Modifier
) {
	val lines = remember(markdown) { markdown.toReleaseNoteLines() }
	Column(modifier = modifier) {
		lines.forEach { line ->
			when (line) {
				is ReleaseNoteLine.Heading -> Text(
					text = line.text,
					style = if (line.level <= 2) {
						MaterialTheme.typography.titleSmall
					} else {
						MaterialTheme.typography.labelLarge
					},
					fontWeight = FontWeight.SemiBold,
					color = ApkTheme.colors.textPrimary,
					modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
				)
				is ReleaseNoteLine.Bullet -> Text(
					text = "\u2022 ${line.text}",
					style = MaterialTheme.typography.bodyMedium,
					color = ApkTheme.colors.textSecondary,
					modifier = Modifier.padding(start = (8 + line.indent * 12).dp, top = 2.dp)
				)
				is ReleaseNoteLine.Paragraph -> Text(
					text = line.text,
					style = MaterialTheme.typography.bodyMedium,
					color = ApkTheme.colors.textSecondary,
					modifier = Modifier.padding(top = 4.dp)
				)
			}
		}
	}
}

private sealed interface ReleaseNoteLine {
	data class Heading(val text: String, val level: Int) : ReleaseNoteLine
	data class Bullet(val text: String, val indent: Int) : ReleaseNoteLine
	data class Paragraph(val text: String) : ReleaseNoteLine
}

private fun String.toReleaseNoteLines(): List<ReleaseNoteLine> {
	val result = mutableListOf<ReleaseNoteLine>()
	var inCodeFence = false

	lineSequence().forEach { rawLine ->
		val trimmed = rawLine.trim()
		if (trimmed.startsWith("```")) {
			inCodeFence = !inCodeFence
			return@forEach
		}
		if (trimmed.isBlank()) return@forEach

		val heading = markdownHeadingRegex.matchEntire(trimmed)
		if (!inCodeFence && heading != null) {
			val text = heading.groupValues[2].cleanInlineMarkdown()
			if (result.isEmpty() && text.isSelfUpdateReleaseHeading()) return@forEach
			result += ReleaseNoteLine.Heading(text, heading.groupValues[1].length)
			return@forEach
		}

		val bullet = markdownBulletRegex.matchEntire(trimmed)
		if (!inCodeFence && bullet != null) {
			val indent = (rawLine.takeWhile { it == ' ' || it == '\t' }.length / 2).coerceIn(0, 2)
			result += ReleaseNoteLine.Bullet(bullet.groupValues[1].cleanInlineMarkdown(), indent)
			return@forEach
		}

		val numbered = markdownNumberedRegex.matchEntire(trimmed)
		if (!inCodeFence && numbered != null) {
			val indent = (rawLine.takeWhile { it == ' ' || it == '\t' }.length / 2).coerceIn(0, 2)
			result += ReleaseNoteLine.Bullet(numbered.groupValues[1].cleanInlineMarkdown(), indent)
			return@forEach
		}

		result += ReleaseNoteLine.Paragraph(trimmed.cleanInlineMarkdown())
	}

	return result.ifEmpty { listOf(ReleaseNoteLine.Paragraph(trim().cleanInlineMarkdown())) }
}

private fun String.cleanInlineMarkdown(): String =
	replace(markdownImageRegex, "$1")
		.replace(markdownLinkRegex, "$1")
		.replace(markdownCodeRegex, "$1")
		.replace("**", "")
		.replace("__", "")
		.replace(Regex("""(^|[^\\])~~"""), "$1")
		.trim()

private fun String.isSelfUpdateReleaseHeading(): Boolean {
	val normalized = lowercase(Locale.ROOT)
	return normalized.contains("apkupdater oss") || normalized.contains("apkupdateross")
}

private val markdownHeadingRegex = Regex("""^(#{1,6})\s+(.+)$""")
private val markdownBulletRegex = Regex("""^[-*+]\s+(.+)$""")
private val markdownNumberedRegex = Regex("""^\d+[.)]\s+(.+)$""")
private val markdownImageRegex = Regex("""!\[([^\]]*)]\([^)]+\)""")
private val markdownLinkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
private val markdownCodeRegex = Regex("""`([^`]*)`""")

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
		AppTopBar(title = stringResource(R.string.tab_updates)) {
			IconButton(onClick = { isSearchMode = true }) {
				Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tab_search))
			}
			IconButton(onClick = { viewModel.refresh() }) {
				RefreshIcon(stringResource(R.string.refresh_updates))
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
	val enabled = count > 0 && !isInstalling
	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = ApkTheme.shapes.lg,
		color = if (enabled) ApkTheme.colors.surfaceHighlight else ApkTheme.colors.surfaceSecondary,
		contentColor = if (enabled) ApkTheme.colors.accent else ApkTheme.colors.disabled,
		border = androidx.compose.foundation.BorderStroke(
			1.dp,
			if (enabled) ApkTheme.colors.accent.copy(alpha = 0.42f) else ApkTheme.colors.divider
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
					color = ApkTheme.colors.accent,
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
				text = if (count > 0) {
					"${stringResource(R.string.update_all)} ($count)"
				} else {
					stringResource(R.string.update_all)
				},
				style = MaterialTheme.typography.labelLarge
			)
		}
	}
}
