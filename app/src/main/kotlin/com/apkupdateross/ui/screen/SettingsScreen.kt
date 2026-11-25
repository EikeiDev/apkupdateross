package com.apkupdateross.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdateross.R
import com.apkupdateross.data.fdroid.FdroidRepo
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.ui.HistoryEventType
import com.apkupdateross.data.ui.InstallHistoryEntry
import com.apkupdateross.data.ui.SettingsUiState
import com.apkupdateross.ui.component.AppListSurface
import com.apkupdateross.ui.component.AppScreen
import com.apkupdateross.ui.component.AppStatusBadge
import com.apkupdateross.ui.component.AppTone
import com.apkupdateross.ui.component.AppTopBar
import java.text.DateFormat
import java.util.Date
import com.apkupdateross.ui.component.LoadingImageApp
import com.apkupdateross.ui.component.LocalTvMode
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
import com.apkupdateross.ui.screen.settings.About
import com.apkupdateross.ui.screen.settings.CustomRepoDialog
import com.apkupdateross.ui.screen.settings.DataLogsSettings
import com.apkupdateross.ui.screen.settings.FdroidRepoDialog
import com.apkupdateross.ui.screen.settings.InstallSettings
import com.apkupdateross.ui.screen.settings.InterfaceSettings
import com.apkupdateross.ui.screen.settings.NetworkSettings
import com.apkupdateross.ui.screen.settings.NotificationsSettings
import com.apkupdateross.ui.screen.settings.SourcesSettings
import com.apkupdateross.ui.screen.settings.UpdatesSettings
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.viewmodel.SettingsViewModel
import com.apkupdateross.viewmodel.UpdateMetrics
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
	viewModel: SettingsViewModel = koinViewModel(),
	sharedRepoUrl: String? = null,
	onSharedRepoConsumed: () -> Unit = {}
) = AppScreen {
	val uiState = viewModel.state.collectAsStateWithLifecycle().value
	val ruStoreCacheCount = viewModel.ruStore404Count.collectAsStateWithLifecycle().value
	val updateMetrics = viewModel.updateMetrics.collectAsStateWithLifecycle().value
	val customRepos = viewModel.customGitRepos.collectAsStateWithLifecycle().value
	val fdroidRepos = viewModel.fdroidRepos.collectAsStateWithLifecycle().value
	var dialogRepo by remember { mutableStateOf<CustomGitRepo?>(null) }
	var dialogRepoInitialUrl by remember { mutableStateOf<String?>(null) }
	var dialogFdroidRepo by remember { mutableStateOf<FdroidRepo?>(null) }
	var dialogIgnoredUpdates by remember { mutableStateOf(false) }
	var dialogInstallHistory by remember { mutableStateOf(false) }
	var dialogSourcePriority by remember { mutableStateOf(false) }
	var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.Home) }
	var lastHomeSelection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
	val isTv = LocalTvMode.current
	val sectionFocusRequester = remember { FocusRequester() }

	LaunchedEffect(sharedRepoUrl) {
		if (!sharedRepoUrl.isNullOrBlank()) {
			viewModel.setSettings()
			selectedSection = SettingsSection.Sources
			dialogRepoInitialUrl = sharedRepoUrl
			dialogRepo = viewModel.createEmptyCustomRepo()
			onSharedRepoConsumed()
		}
	}

	LaunchedEffect(isTv, uiState, selectedSection) {
		if (isTv && (uiState != SettingsUiState.Settings || selectedSection != SettingsSection.Home)) {
			sectionFocusRequester.requestFocus()
		}
	}

	BackHandler(enabled = uiState != SettingsUiState.Settings || selectedSection != SettingsSection.Home) {
		if (uiState != SettingsUiState.Settings) {
			viewModel.setSettings()
		} else {
			selectedSection = SettingsSection.Home
		}
	}
	if (uiState == SettingsUiState.Settings) {
		DisposableEffect(Unit) {
			viewModel.startMetricsAutoRefresh()
			onDispose { viewModel.stopMetricsAutoRefresh() }
		}
		SettingsTopBar(
			title = stringResource(selectedSection.titleRes),
			onBack = if (selectedSection == SettingsSection.Home) null else ({ selectedSection = SettingsSection.Home }),
			focusRequester = sectionFocusRequester
		)
		Settings(
			viewModel = viewModel,
			ruStoreCacheCount = ruStoreCacheCount,
			updateMetrics = updateMetrics,
			customRepos = customRepos,
			fdroidRepos = fdroidRepos,
			onAddRepo = { dialogRepo = viewModel.createEmptyCustomRepo() },
			onEditRepo = { dialogRepo = it },
			onDeleteRepo = { viewModel.removeCustomRepo(it.id) },
			onAddFdroidRepo = { dialogFdroidRepo = FdroidRepo(name = "", url = "") },
			onEditFdroidRepo = { dialogFdroidRepo = it },
			onDeleteFdroidRepo = { viewModel.removeFdroidRepo(it) },
			onToggleFdroidRepo = { id, enabled -> viewModel.toggleFdroidRepo(id, enabled) },
			onManageIgnoredUpdates = { dialogIgnoredUpdates = true },
			onOpenInstallHistory = { dialogInstallHistory = true },
			onOpenSourcePriority = { dialogSourcePriority = true },
			selectedSection = selectedSection,
			onSelectSection = {
				lastHomeSelection = it
				selectedSection = it
			},
			onAbout = {
				lastHomeSelection = SettingsSection.Home
				viewModel.setAbout()
			},
			homeFocusTarget = if (isTv) lastHomeSelection else null
		)
	} else {
		AboutTopBar(onBack = { viewModel.setSettings() }, focusRequester = sectionFocusRequester)
		About()
	}
	dialogRepo?.let { repo ->
		CustomRepoDialog(
			repo = repo,
			viewModel = viewModel,
			initialUrlOverride = dialogRepoInitialUrl,
			onDismiss = {
				dialogRepo = null
				dialogRepoInitialUrl = null
			},
			onSave = {
				viewModel.addOrUpdateCustomRepo(it)
				dialogRepo = null
				dialogRepoInitialUrl = null
			}
		)
	}
	dialogFdroidRepo?.let { repo ->
		FdroidRepoDialog(
			repo = repo,
			onDismiss = { dialogFdroidRepo = null },
			onSave = {
				viewModel.addOrUpdateFdroidRepo(it)
				dialogFdroidRepo = null
			}
		)
	}
	if (dialogIgnoredUpdates) {
		IgnoredUpdatesDialog(
			viewModel = viewModel,
			onDismiss = { dialogIgnoredUpdates = false }
		)
	}
	if (dialogInstallHistory) {
		InstallHistoryDialog(
			viewModel = viewModel,
			onDismiss = { dialogInstallHistory = false }
		)
	}
	if (dialogSourcePriority) {
		SourcePriorityDialog(
			viewModel = viewModel,
			onDismiss = { dialogSourcePriority = false }
		)
	}
}

private enum class SettingsSection(
	@StringRes val titleRes: Int,
	@StringRes val subtitleRes: Int,
	@DrawableRes val iconRes: Int
) {
	Home(R.string.tab_settings, R.string.tab_settings, R.drawable.ic_system),
	Sources(R.string.settings_sources, R.string.settings_category_sources_subtitle, R.drawable.ic_appstore),
	Updates(R.string.tab_updates, R.string.settings_category_updates_subtitle, R.drawable.ic_refresh),
	Notifications(R.string.settings_notifications, R.string.settings_category_notifications_subtitle, R.drawable.ic_alarm),
	Install(R.string.settings_category_install, R.string.settings_category_install_subtitle, R.drawable.ic_install),
	Interface(R.string.settings_ui, R.string.settings_category_interface_subtitle, R.drawable.ic_theme),
	Network(R.string.settings_network, R.string.settings_category_network_subtitle, R.drawable.ic_system),
	DataLogs(R.string.settings_category_data_logs, R.string.settings_category_data_logs_subtitle, R.drawable.ic_safe)
}

@Composable
private fun Settings(
	viewModel: SettingsViewModel,
	ruStoreCacheCount: Int,
	updateMetrics: UpdateMetrics,
	customRepos: List<CustomGitRepo>,
	fdroidRepos: List<FdroidRepo>,
	onEditRepo: (CustomGitRepo) -> Unit,
	onDeleteRepo: (CustomGitRepo) -> Unit,
	onAddRepo: () -> Unit,
	onAddFdroidRepo: () -> Unit,
	onEditFdroidRepo: (FdroidRepo) -> Unit,
	onDeleteFdroidRepo: (String) -> Unit,
	onToggleFdroidRepo: (String, Boolean) -> Unit,
	onManageIgnoredUpdates: () -> Unit,
	onOpenInstallHistory: () -> Unit,
	onOpenSourcePriority: () -> Unit,
	selectedSection: SettingsSection,
	onSelectSection: (SettingsSection) -> Unit,
	onAbout: () -> Unit,
	homeFocusTarget: SettingsSection?
) {
	val installMode = viewModel.installModeFlow.collectAsStateWithLifecycle().value
	val context = LocalContext.current
	val downloadFolderUri = viewModel.downloadFolderUri.collectAsStateWithLifecycle().value
	val installHistory = viewModel.installHistory.collectAsStateWithLifecycle().value
	val downloadCacheSizeBytes = viewModel.downloadCacheSizeBytes.collectAsStateWithLifecycle().value
	val storeNotFoundCacheCount = viewModel.storeNotFoundCacheCount.collectAsStateWithLifecycle().value
	LaunchedEffect(selectedSection) {
		when (selectedSection) {
			SettingsSection.DataLogs -> {
				viewModel.refreshDownloadCacheSize()
				viewModel.refreshStoreNotFoundCacheCount()
			}
			SettingsSection.Sources -> {
				viewModel.refreshRuStore404Count()
			}
			else -> Unit
		}
	}
	val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		uri?.let { viewModel.exportSettings(it, context) }
	}
	val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		uri?.let { viewModel.importSettings(it, context) }
	}
	val localPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		uri?.let { viewModel.installLocalPackage(it) }
	}
	val downloadFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		uri?.let { viewModel.setDownloadFolderUri(context, it) }
	}

	when (selectedSection) {
		SettingsSection.Home -> SettingsHome(onSelectSection, onAbout, homeFocusTarget)
		SettingsSection.Sources -> SourcesSettings(
			viewModel = viewModel,
			ruStore404Count = ruStoreCacheCount,
			customRepos = customRepos,
			fdroidRepos = fdroidRepos,
			onEditRepo = onEditRepo,
			onDeleteRepo = onDeleteRepo,
			onAddRepo = onAddRepo,
			onAddFdroidRepo = onAddFdroidRepo,
			onEditFdroidRepo = onEditFdroidRepo,
			onDeleteFdroidRepo = onDeleteFdroidRepo,
			onToggleFdroidRepo = onToggleFdroidRepo
		)
		SettingsSection.Updates -> UpdatesSettings(
			viewModel = viewModel,
			updateMetrics = updateMetrics,
			onManageIgnoredUpdates = onManageIgnoredUpdates,
			onOpenSourcePriority = onOpenSourcePriority
		)
		SettingsSection.Notifications -> NotificationsSettings(viewModel, installMode)
		SettingsSection.Install -> InstallSettings(
			viewModel = viewModel,
			onInstallLocalPackage = {
				localPackageLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "*/*"))
			}
		)
		SettingsSection.Interface -> InterfaceSettings(viewModel)
		SettingsSection.Network -> NetworkSettings(viewModel)
		SettingsSection.DataLogs -> DataLogsSettings(
			viewModel = viewModel,
			downloadFolderLabel = viewModel.getDownloadFolderLabel(downloadFolderUri),
			hasCustomDownloadFolder = downloadFolderUri.isNotBlank(),
			installHistoryCount = installHistory.size,
			downloadCacheSizeBytes = downloadCacheSizeBytes,
			storeNotFoundCacheCount = storeNotFoundCacheCount,
			onOpenInstallHistory = onOpenInstallHistory,
			onChooseDownloadFolder = { downloadFolderLauncher.launch(null) },
			onResetDownloadFolder = { viewModel.resetDownloadFolder(context) },
			onExport = { exportLauncher.launch("apkupdateross_backup.json") },
			onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) }
		)
	}
}

@Composable
private fun SettingsHome(
	onSelectSection: (SettingsSection) -> Unit,
	onAbout: () -> Unit,
	focusTarget: SettingsSection?
) {
	val sections = listOf(
		SettingsSection.Sources,
		SettingsSection.Updates,
		SettingsSection.Notifications,
		SettingsSection.Install,
		SettingsSection.Interface,
		SettingsSection.Network,
		SettingsSection.DataLogs
	)
	val homeFocusRequester = remember(focusTarget) { FocusRequester() }
	val listState = rememberLazyListState()
	LaunchedEffect(focusTarget) {
		if (focusTarget != null) {
			val targetIndex = if (focusTarget == SettingsSection.Home) {
				sections.size + 1
			} else {
				sections.indexOf(focusTarget).coerceAtLeast(0) + 1
			}
			listState.scrollToItem(targetIndex)
			withFrameNanos { }
			homeFocusRequester.requestFocus()
		}
	}
	LazyColumn(state = listState) {
		item { Spacer(Modifier.height(12.dp)) }
		sections.forEach { section ->
			item {
				SettingsCategoryItem(
					title = stringResource(section.titleRes),
					subtitle = stringResource(section.subtitleRes),
					icon = section.iconRes,
					onClick = { onSelectSection(section) },
					modifier = if (focusTarget == section) Modifier.focusRequester(homeFocusRequester) else Modifier
				)
			}
		}
		item {
			SettingsCategoryItem(
				title = stringResource(R.string.about),
				subtitle = stringResource(R.string.settings_category_about_subtitle),
				icon = R.drawable.ic_info,
				onClick = onAbout,
				modifier = if (focusTarget == SettingsSection.Home) Modifier.focusRequester(homeFocusRequester) else Modifier
			)
		}
		item { Spacer(Modifier.height(24.dp)) }
	}
}

@Composable
private fun SettingsCategoryItem(
	title: String,
	subtitle: String,
	@DrawableRes icon: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) = AppListSurface(
	modifier = modifier
		.fillMaxWidth()
		.padding(horizontal = 16.dp, vertical = 6.dp),
	onClick = onClick
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 76.dp)
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SettingsIcon(icon, title, Modifier.padding(end = 16.dp))
		Column(Modifier.weight(1f)) {
			Text(
				title,
				style = MaterialTheme.typography.bodyLarge,
				color = ApkTheme.colors.textPrimary
			)
			Text(
				subtitle,
				style = MaterialTheme.typography.bodyMedium,
				color = ApkTheme.colors.textSecondary
			)
		}
		Icon(
			Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = ApkTheme.colors.textTertiary
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
	title: String,
	onBack: (() -> Unit)? = null,
	focusRequester: FocusRequester? = null
) = AppTopBar(
	title = title,
	navigationIcon = {
		if (onBack != null) {
			IconButton(
				onClick = onBack,
				modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			) {
				Icon(
					Icons.AutoMirrored.Filled.KeyboardArrowLeft,
					stringResource(R.string.back),
					tint = ApkTheme.colors.textSecondary
				)
			}
		}
	}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutTopBar(onBack: () -> Unit, focusRequester: FocusRequester? = null) = AppTopBar(
	title = stringResource(R.string.about),
	navigationIcon = {
		IconButton(
			onClick = onBack,
			modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
		) {
			Icon(
				Icons.AutoMirrored.Filled.KeyboardArrowLeft,
				stringResource(R.string.back),
				tint = ApkTheme.colors.textSecondary
			)
		}
	}
)

@Composable
fun IgnoredUpdatesDialog(
	viewModel: SettingsViewModel,
	onDismiss: () -> Unit
) {
	val ignoredInfos by viewModel.ignoredUpdateInfos.collectAsStateWithLifecycle()

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.ignored_updates)) },
		text = {
			if (ignoredInfos.isEmpty()) {
				Text(
					text = stringResource(R.string.ignored_updates_empty),
					modifier = Modifier.padding(vertical = 16.dp),
					color = ApkTheme.colors.textSecondary
				)
			} else {
				LazyColumn(
					modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
				) {
					items(ignoredInfos.distinctBy { it.packageName }) { info ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							LoadingImageApp(
								packageName = info.packageName,
								modifier = Modifier.size(40.dp)
							)
							Spacer(modifier = Modifier.width(12.dp))
							Column(modifier = Modifier.weight(1f)) {
								val pm = LocalContext.current.packageManager
								Text(
									text = info.name.ifEmpty {
										try {
											pm.getApplicationInfo(info.packageName, 0).loadLabel(pm).toString()
										} catch (e: Exception) {
											info.packageName
										}
									},
									style = MaterialTheme.typography.bodyLarge,
									maxLines = if (ApkTheme.accessibility.isLargeText) 2 else 1,
									overflow = TextOverflow.Ellipsis
								)
								Text(
									text = info.packageName,
									style = MaterialTheme.typography.bodyMedium,
									color = ApkTheme.colors.textSecondary,
									maxLines = if (ApkTheme.accessibility.isLargeText) 2 else 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							IconButton(
								onClick = { viewModel.removeIgnoredUpdateInfo(info.packageName) }
							) {
								Icon(
									imageVector = Icons.Default.Close,
									contentDescription = stringResource(R.string.restore_update),
									tint = ApkTheme.colors.textSecondary
								)
							}
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.ok))
			}
		},
		dismissButton = {
			if (ignoredInfos.isNotEmpty()) {
				TextButton(
					onClick = { viewModel.clearAllIgnoredUpdates() }
				) {
					Text(
						stringResource(R.string.clear_all),
						color = MaterialTheme.colorScheme.error
					)
				}
			}
		}
	)
}

@Composable
private fun InstallHistoryDialog(
	viewModel: SettingsViewModel,
	onDismiss: () -> Unit
) {
	val entries by viewModel.installHistory.collectAsStateWithLifecycle()

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.install_history_title)) },
		text = {
			if (entries.isEmpty()) {
				Text(
					text = stringResource(R.string.install_history_empty),
					modifier = Modifier.padding(vertical = 16.dp),
					color = ApkTheme.colors.textSecondary
				)
			} else {
				LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
					items(entries, key = { it.id }) { entry ->
						InstallHistoryRow(entry)
						Divider(color = ApkTheme.colors.divider)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.ok))
			}
		},
		dismissButton = {
			if (entries.isNotEmpty()) {
				TextButton(onClick = { viewModel.clearInstallHistory() }) {
					Text(
						stringResource(R.string.clear_all),
						color = MaterialTheme.colorScheme.error
					)
				}
			}
		}
	)
}

@Composable
private fun InstallHistoryRow(entry: InstallHistoryEntry) {
	val title = when (entry.eventType) {
		HistoryEventType.BACKGROUND_CHECK -> stringResource(R.string.install_history_background_check)
		HistoryEventType.BACKGROUND_INSTALL -> "${entry.appName.ifBlank { entry.packageName }} (${stringResource(R.string.install_history_auto_tag)})"
		HistoryEventType.FOREGROUND_INSTALL -> entry.appName.ifBlank { entry.packageName }
	}
	val subtitle = when (entry.eventType) {
		HistoryEventType.BACKGROUND_CHECK -> if (entry.success) {
			val count = entry.details.toIntOrNull() ?: 0
			if (count == 0) {
				stringResource(R.string.notification_no_updates_message)
			} else {
				stringResource(R.string.install_history_found_updates, count)
			}
		} else {
			entry.details.ifBlank { stringResource(R.string.notification_check_failed_message) }
		}
		HistoryEventType.BACKGROUND_INSTALL,
		HistoryEventType.FOREGROUND_INSTALL -> listOf(
			entry.version,
			entry.sourceName,
			entry.details
		).filter { it.isNotBlank() }.joinToString(" • ")
	}
	val formattedDate = remember(entry.timestamp) {
		DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp))
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 10.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.SemiBold,
				color = ApkTheme.colors.textPrimary,
				modifier = Modifier.weight(1f),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			AppStatusBadge(
				text = stringResource(if (entry.success) R.string.install_history_status_ok else R.string.install_history_status_failed),
				tone = if (entry.success) AppTone.Success else AppTone.Error
			)
		}
		if (subtitle.isNotBlank()) {
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = ApkTheme.colors.textSecondary,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis
			)
		}
		Text(
			text = formattedDate,
			style = MaterialTheme.typography.labelSmall,
			color = ApkTheme.colors.textTertiary
		)
	}
}

@Composable
private fun SourcePriorityDialog(
	viewModel: SettingsViewModel,
	onDismiss: () -> Unit
) {
	val order by viewModel.sourcePriorityOrder.collectAsStateWithLifecycle()
	val pinnedSources by viewModel.pinnedAppSources.collectAsStateWithLifecycle()

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.settings_source_priority)) },
		text = {
			LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
				item {
					Text(
						text = stringResource(R.string.settings_source_priority_desc),
						style = MaterialTheme.typography.bodySmall,
						color = ApkTheme.colors.textSecondary,
						modifier = Modifier.padding(bottom = 8.dp)
					)
				}
				items(order.size, key = { order[it] }) { index ->
					val sourceName = order[index]
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = "${index + 1}. $sourceName",
							style = MaterialTheme.typography.bodyLarge,
							color = ApkTheme.colors.textPrimary,
							modifier = Modifier.weight(1f)
						)
						IconButton(
							onClick = { viewModel.moveSourcePriority(index, -1) },
							enabled = index > 0
						) {
							Icon(
								imageVector = Icons.Default.KeyboardArrowUp,
								contentDescription = stringResource(R.string.move_up_cd),
								tint = if (index > 0) ApkTheme.colors.textSecondary else ApkTheme.colors.disabled
							)
						}
						IconButton(
							onClick = { viewModel.moveSourcePriority(index, 1) },
							enabled = index < order.lastIndex
						) {
							Icon(
								imageVector = Icons.Default.KeyboardArrowDown,
								contentDescription = stringResource(R.string.move_down_cd),
								tint = if (index < order.lastIndex) ApkTheme.colors.textSecondary else ApkTheme.colors.disabled
							)
						}
					}
					Divider(color = ApkTheme.colors.divider)
				}
				if (pinnedSources.isNotEmpty()) {
					item {
						Text(
							text = stringResource(R.string.settings_pinned_app_sources),
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
							color = ApkTheme.colors.textPrimary,
							modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
						)
					}
					items(pinnedSources.entries.toList(), key = { it.key }) { (pkg, src) ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = pkg,
									style = MaterialTheme.typography.bodyMedium,
									color = ApkTheme.colors.textPrimary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
								Text(
									text = src,
									style = MaterialTheme.typography.bodySmall,
									color = ApkTheme.colors.accent
								)
							}
							IconButton(onClick = { viewModel.clearPinnedAppSource(pkg) }) {
								Icon(
									imageVector = Icons.Default.Close,
									contentDescription = stringResource(R.string.unpin_source_cd),
									tint = ApkTheme.colors.textSecondary
								)
							}
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.ok))
			}
		},
		dismissButton = {
			TextButton(onClick = { viewModel.resetSourcePriorityOrder() }) {
				Text(stringResource(R.string.reset_default))
			}
		}
	)
}
