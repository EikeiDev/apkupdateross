package com.apkupdateross.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.fdroid.FdroidRepo
import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.git.parseRepoUrl
import com.apkupdateross.repository.DetectedGitRepo
import com.apkupdateross.repository.GitRepoDetectionFailure
import com.apkupdateross.repository.GitRepoDetectionResult
import com.apkupdateross.data.ui.SettingsUiState
import com.apkupdateross.data.ui.SwipeIgnoreDirection
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.AppListSurface
import com.apkupdateross.ui.component.AppScreen
import com.apkupdateross.ui.component.AppTopBar
import com.apkupdateross.ui.component.LargeTitle
import com.apkupdateross.ui.component.LoadingImageApp
import com.apkupdateross.ui.component.LocalTvMode
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SliderSetting
import com.apkupdateross.ui.component.SourceIcon
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.component.SwitchSetting
import com.apkupdateross.ui.component.tvFocusOutline
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_ACCENT
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_BACKGROUND
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_NAVIGATION
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_SURFACE
import com.apkupdateross.ui.theme.THEME_MODE_CUSTOM
import com.apkupdateross.ui.theme.colorFromHex
import com.apkupdateross.ui.theme.normalizeHexColor
import com.apkupdateross.viewmodel.SettingsViewModel
import com.apkupdateross.viewmodel.UpdateMetrics
import java.text.DateFormat
import java.util.Date
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlinx.coroutines.delay


@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) = AppScreen {
	val uiState = viewModel.state.collectAsStateWithLifecycle().value
	val ruStoreCacheCount = viewModel.ruStore404Count.collectAsStateWithLifecycle().value
	val updateMetrics = viewModel.updateMetrics.collectAsStateWithLifecycle().value
	val customRepos = viewModel.customGitRepos.collectAsStateWithLifecycle().value
	val fdroidRepos = viewModel.fdroidRepos.collectAsStateWithLifecycle().value
	var dialogRepo by remember { mutableStateOf<CustomGitRepo?>(null) }
	var dialogFdroidRepo by remember { mutableStateOf<FdroidRepo?>(null) }
	var dialogIgnoredUpdates by remember { mutableStateOf(false) }
	var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.Home) }
	var lastHomeSelection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
	val isTv = LocalTvMode.current
	val sectionFocusRequester = remember { FocusRequester() }

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
			viewModel,
			ruStoreCacheCount,
			updateMetrics,
			customRepos,
			fdroidRepos,
			onAddRepo = { dialogRepo = viewModel.createEmptyCustomRepo() },
			onEditRepo = { dialogRepo = it },
			onDeleteRepo = { viewModel.removeCustomRepo(it.id) },
			onAddFdroidRepo = { dialogFdroidRepo = FdroidRepo(name = "", url = "") },
			onEditFdroidRepo = { dialogFdroidRepo = it },
			onDeleteFdroidRepo = { viewModel.removeFdroidRepo(it) },
			onToggleFdroidRepo = { id, enabled -> viewModel.toggleFdroidRepo(id, enabled) },
			onManageIgnoredUpdates = { dialogIgnoredUpdates = true },
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
			onDismiss = { dialogRepo = null },
			onSave = {
				viewModel.addOrUpdateCustomRepo(it)
				dialogRepo = null
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
}

@Composable
fun About() = Column(
	modifier = Modifier
		.fillMaxSize()
		.verticalScroll(rememberScrollState())
) {
	Spacer(Modifier.height(8.dp))
	AboutIdentity()

	SettingsGroup(R.string.about_project) {
		AboutLinkRow(
			icon = R.drawable.ic_github,
			title = stringResource(R.string.github),
			subtitle = stringResource(R.string.about_github_description),
			url = stringResource(R.string.github_url)
		)
		Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
		AboutLinkRow(
			imageVector = Icons.Default.Favorite,
			title = stringResource(R.string.donate),
			subtitle = stringResource(R.string.about_donate_description),
			url = stringResource(R.string.donate_url)
		)
		Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
		AboutLinkRow(
			icon = R.drawable.ic_safe,
			title = stringResource(R.string.about_license),
			subtitle = stringResource(R.string.about_license_name),
			url = stringResource(R.string.license_url)
		)
	}

	SettingsGroup(R.string.about_credits) {
		MetricInlineRow(
			text = stringResource(R.string.about_maintained_by),
			value = stringResource(R.string.about_maintainer_name),
			icon = R.drawable.ic_info
		)
		Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
		MetricInlineRow(
			text = stringResource(R.string.about_based_on),
			value = stringResource(R.string.about_original_project),
			icon = R.drawable.ic_github
		)
	}

	Spacer(Modifier.height(24.dp))
}

@Composable
private fun AboutIdentity() {
	val largeText = ApkTheme.accessibility.isLargeText
	if (largeText) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			AboutLogo(88.dp)
			Spacer(Modifier.height(16.dp))
			AboutIdentityText(centered = true)
		}
	} else {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			AboutLogo(88.dp)
			Spacer(Modifier.width(20.dp))
			AboutIdentityText(modifier = Modifier.weight(1f))
		}
	}
}

@Composable
private fun AboutLogo(size: androidx.compose.ui.unit.Dp) = Surface(
	modifier = Modifier.size(size),
	shape = ApkTheme.shapes.lg,
	color = ApkTheme.colors.surfaceSecondary,
	border = BorderStroke(1.dp, ApkTheme.colors.divider)
) {
	Box(contentAlignment = Alignment.Center) {
		LoadingImageApp(BuildConfig.APPLICATION_ID, Modifier.size(size * 0.72f))
	}
}

@Composable
private fun AboutIdentityText(
	modifier: Modifier = Modifier,
	centered: Boolean = false
) = Column(
	modifier = modifier,
	horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
) {
	Text(
		text = stringResource(R.string.app_name),
		style = MaterialTheme.typography.headlineMedium,
		fontWeight = FontWeight.Bold,
		color = ApkTheme.colors.textOnBackground
	)
	Spacer(Modifier.height(6.dp))
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Text(
			text = stringResource(R.string.about_version),
			style = MaterialTheme.typography.bodyMedium,
			color = ApkTheme.colors.textSecondary
		)
		Text(
			text = BuildConfig.VERSION_NAME,
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.SemiBold,
			color = ApkTheme.colors.accent
		)
	}
}

@Composable
private fun AboutLinkRow(
	@DrawableRes icon: Int? = null,
	imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
	title: String,
	subtitle: String,
	url: String
) {
	val context = LocalContext.current
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 76.dp)
			.tvFocusOutline(ApkTheme.shapes.sm)
			.clickable {
				runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
			}
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Surface(
			modifier = Modifier.size(42.dp),
			shape = ApkTheme.shapes.sm,
			color = ApkTheme.colors.surfaceHighlight
		) {
			Box(contentAlignment = Alignment.Center) {
				when {
					icon != null -> Icon(
						painter = painterResource(icon),
						contentDescription = null,
						modifier = Modifier.size(24.dp),
						tint = ApkTheme.colors.accent
					)
					imageVector != null -> Icon(
						imageVector = imageVector,
						contentDescription = null,
						modifier = Modifier.size(24.dp),
						tint = ApkTheme.colors.accent
					)
				}
			}
		}
		Spacer(Modifier.width(16.dp))
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.bodyLarge, color = ApkTheme.colors.textPrimary)
			Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ApkTheme.colors.textSecondary)
		}
		Icon(
			painter = painterResource(R.drawable.ic_open_in_new),
			contentDescription = null,
			modifier = Modifier.padding(start = 12.dp).size(20.dp),
			tint = ApkTheme.colors.textTertiary
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
	ruStore404Count: Int,
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
	selectedSection: SettingsSection,
	onSelectSection: (SettingsSection) -> Unit,
	onAbout: () -> Unit,
	homeFocusTarget: SettingsSection?
) {
	val installMode = viewModel.installModeFlow.collectAsStateWithLifecycle().value
	val context = LocalContext.current
	val downloadFolderUri = viewModel.downloadFolderUri.collectAsStateWithLifecycle().value
	val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		uri?.let { viewModel.exportSettings(it, context) }
	}
	val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		uri?.let { viewModel.importSettings(it, context) }
	}
	val downloadFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		uri?.let { viewModel.setDownloadFolderUri(context, it) }
	}

	when (selectedSection) {
		SettingsSection.Home -> SettingsHome(onSelectSection, onAbout, homeFocusTarget)
		SettingsSection.Sources -> SourcesSettings(
			viewModel = viewModel,
			ruStore404Count = ruStore404Count,
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
			onManageIgnoredUpdates = onManageIgnoredUpdates
		)
		SettingsSection.Notifications -> NotificationsSettings(viewModel, installMode)
		SettingsSection.Install -> InstallSettings(viewModel)
		SettingsSection.Interface -> InterfaceSettings(viewModel)
		SettingsSection.Network -> NetworkSettings(viewModel)
		SettingsSection.DataLogs -> DataLogsSettings(
			viewModel = viewModel,
			downloadFolderLabel = viewModel.getDownloadFolderLabel(downloadFolderUri),
			hasCustomDownloadFolder = downloadFolderUri.isNotBlank(),
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

@Composable
private fun SettingsGroup(
	@StringRes title: Int,
	content: @Composable () -> Unit
) {
	LargeTitle(stringResource(title), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
	Surface(
		shape = ApkTheme.shapes.md,
		color = ApkTheme.colors.surface,
		border = BorderStroke(1.dp, ApkTheme.colors.divider),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
	) {
		Column { content() }
	}
}

@Composable
private fun InterfaceSettings(viewModel: SettingsViewModel) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_interface_behavior) {
			val currentTheme = viewModel.getTheme()
			val swipeIgnoreEnabled by viewModel.swipeIgnoreEnabled.collectAsStateWithLifecycle()
			SegmentedButtonSetting(
				stringResource(R.string.theme),
				listOf(
					stringResource(R.string.theme_system),
					stringResource(R.string.theme_dark),
					stringResource(R.string.theme_light),
					stringResource(R.string.theme_custom)
				),
				{ viewModel.getTheme() },
				{ viewModel.setTheme(it) },
				R.drawable.ic_theme
			)
			if (currentTheme == THEME_MODE_CUSTOM) {
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				CustomThemeSettings(viewModel)
			}
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SwitchSetting(
				{ viewModel.getSwipeIgnoreEnabled() },
				{ viewModel.setSwipeIgnoreEnabled(it) },
				stringResource(R.string.settings_swipe_controls),
				R.drawable.ic_disabled
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SegmentedButtonSetting(
				stringResource(R.string.settings_swipe_hide_direction),
				SwipeIgnoreDirection.entries.map { stringResource(it.labelRes) },
				{ viewModel.getSwipeIgnoreDirection() },
				{ viewModel.setSwipeIgnoreDirection(it) },
				R.drawable.ic_disabled,
				enabled = swipeIgnoreEnabled
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SwitchSetting(
				{ viewModel.getPlayTextAnimations() },
				{ viewModel.setPlayTextAnimations(it) },
				stringResource(R.string.play_text_animations),
				R.drawable.ic_animation
			)
		}
	}
	item {
		SettingsGroup(R.string.settings_interface_layout) {
			SwitchSetting(
				{ viewModel.getUseCompactView() },
				{ viewModel.setUseCompactView(it) },
				stringResource(R.string.settings_compact_view),
				R.drawable.ic_visible
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SliderSetting(
				{ viewModel.getPortraitColumns().toFloat() },
				{ viewModel.setPortraitColumns(it.toInt()) },
				stringResource(R.string.settings_portrait_columns),
				1f..6f,
				R.drawable.ic_system
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SliderSetting(
				{ viewModel.getLandscapeColumns().toFloat() },
				{ viewModel.setLandscapeColumns(it.toInt()) },
				stringResource(R.string.settings_landscape_columns),
				2f..8f,
				R.drawable.ic_system
			)
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun CustomThemeSettings(viewModel: SettingsViewModel) {
	var accent by rememberSaveable { mutableStateOf(viewModel.getCustomThemeAccent()) }
	var background by rememberSaveable { mutableStateOf(viewModel.getCustomThemeBackground()) }
	var surface by rememberSaveable { mutableStateOf(viewModel.getCustomThemeSurface()) }
	var navigation by rememberSaveable { mutableStateOf(viewModel.getCustomThemeNavigation()) }

	CustomThemePreview(
		accent = accent,
		background = background,
		surface = surface,
		navigation = navigation
	)
	CustomColorSetting(
		label = stringResource(R.string.theme_custom_accent),
		value = accent,
		swatches = AccentSwatches,
		onValueChange = { input ->
			accent = sanitizeHexInput(input)
			normalizeHexColor(accent)?.let(viewModel::setCustomThemeAccent)
		},
		onColorPicked = {
			accent = it
			viewModel.setCustomThemeAccent(it)
		}
	)
	CustomColorSetting(
		label = stringResource(R.string.theme_custom_background),
		value = background,
		swatches = BackgroundSwatches,
		onValueChange = { input ->
			background = sanitizeHexInput(input)
			normalizeHexColor(background)?.let(viewModel::setCustomThemeBackground)
		},
		onColorPicked = {
			background = it
			viewModel.setCustomThemeBackground(it)
		}
	)
	CustomColorSetting(
		label = stringResource(R.string.theme_custom_surface),
		value = surface,
		swatches = SurfaceSwatches,
		onValueChange = { input ->
			surface = sanitizeHexInput(input)
			normalizeHexColor(surface)?.let(viewModel::setCustomThemeSurface)
		},
		onColorPicked = {
			surface = it
			viewModel.setCustomThemeSurface(it)
		}
	)
	CustomColorSetting(
		label = stringResource(R.string.theme_custom_navigation),
		value = navigation,
		swatches = NavigationSwatches,
		onValueChange = { input ->
			navigation = sanitizeHexInput(input)
			normalizeHexColor(navigation)?.let(viewModel::setCustomThemeNavigation)
		},
		onColorPicked = {
			navigation = it
			viewModel.setCustomThemeNavigation(it)
		}
	)
	ButtonSetting(
		stringResource(R.string.theme_custom_reset),
		{
			viewModel.resetCustomTheme()
			accent = DEFAULT_CUSTOM_ACCENT
			background = DEFAULT_CUSTOM_BACKGROUND
			surface = DEFAULT_CUSTOM_SURFACE
			navigation = DEFAULT_CUSTOM_NAVIGATION
		},
		R.drawable.ic_refresh
	)
}

@Composable
private fun CustomThemePreview(
	accent: String,
	background: String,
	surface: String,
	navigation: String
) {
	val backgroundColor = colorFromHex(background, Color(0xFF101312))
	val surfaceColor = colorFromHex(surface, Color(0xFF151917))
	val accentColor = colorFromHex(accent, Color(0xFF74D7B2))
	val navigationColor = colorFromHex(navigation, Color(0xFF15231D))

	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 12.dp),
		shape = ApkTheme.shapes.md,
		color = backgroundColor,
		border = BorderStroke(1.dp, readableColorFor(backgroundColor).copy(alpha = 0.22f))
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Text(
				text = stringResource(R.string.theme_custom_preview),
				style = MaterialTheme.typography.labelLarge,
				color = readableColorFor(backgroundColor)
			)
			Surface(
				modifier = Modifier.fillMaxWidth(),
				shape = ApkTheme.shapes.sm,
				color = surfaceColor,
				border = BorderStroke(1.dp, readableColorFor(surfaceColor).copy(alpha = 0.22f))
			) {
				Row(
					modifier = Modifier.padding(10.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Surface(shape = CircleShape, color = accentColor, modifier = Modifier.size(28.dp)) {}
					Spacer(Modifier.width(10.dp))
					Column(Modifier.weight(1f)) {
						Text(
							text = stringResource(R.string.app_name),
							style = MaterialTheme.typography.bodyLarge,
							color = readableColorFor(surfaceColor)
						)
						Text(
							text = stringResource(R.string.theme_custom_preview_version),
							style = MaterialTheme.typography.bodyMedium,
							color = readableColorFor(surfaceColor).copy(alpha = 0.72f)
						)
					}
				}
			}
			Surface(
				modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
				shape = ApkTheme.shapes.sm,
				color = navigationColor
			) {
				Box(contentAlignment = Alignment.Center) {
					Text(
						text = stringResource(R.string.tab_updates),
						style = MaterialTheme.typography.labelMedium,
						color = accentColor
					)
				}
			}
		}
	}
}

@Composable
private fun CustomColorSetting(
	label: String,
	value: String,
	swatches: List<String>,
	onValueChange: (String) -> Unit,
	onColorPicked: (String) -> Unit
) {
	val normalized = normalizeHexColor(value)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = ApkTheme.colors.textSecondary
		)
		Row(verticalAlignment = Alignment.CenterVertically) {
			Surface(
				shape = CircleShape,
				color = colorFromHex(value, ApkTheme.colors.surfaceSecondary),
				border = BorderStroke(1.dp, ApkTheme.colors.divider),
				modifier = Modifier.size(32.dp)
			) {}
			Spacer(Modifier.width(12.dp))
			OutlinedTextField(
				value = value,
				onValueChange = onValueChange,
				modifier = Modifier.weight(1f),
				singleLine = true,
				isError = normalized == null,
				shape = ApkTheme.shapes.sm,
				colors = OutlinedTextFieldDefaults.colors(
					focusedBorderColor = ApkTheme.colors.accent.copy(alpha = 0.72f),
					unfocusedBorderColor = ApkTheme.colors.divider,
					focusedContainerColor = ApkTheme.colors.surfaceSecondary,
					unfocusedContainerColor = ApkTheme.colors.surfaceSecondary,
					focusedTextColor = ApkTheme.colors.textPrimary,
					unfocusedTextColor = ApkTheme.colors.textPrimary,
					focusedLabelColor = ApkTheme.colors.accent,
					unfocusedLabelColor = ApkTheme.colors.textSecondary,
					cursorColor = ApkTheme.colors.accent
				),
				supportingText = {
					if (normalized == null) {
						Text(stringResource(R.string.theme_custom_invalid))
					}
				}
			)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			swatches.forEach { swatch ->
				val selected = swatch.equals(normalized, ignoreCase = true)
				Surface(
					shape = CircleShape,
					color = colorFromHex(swatch, ApkTheme.colors.surfaceSecondary),
					border = BorderStroke(
						if (selected) 3.dp else 1.dp,
						if (selected) ApkTheme.colors.accent else ApkTheme.colors.divider
					),
					modifier = Modifier
						.size(32.dp)
						.tvFocusOutline(CircleShape, scale = 1.12f)
						.clickable { onColorPicked(swatch) }
				) {}
			}
		}
	}
}

private fun sanitizeHexInput(value: String): String {
	val raw = value
		.trim()
		.removePrefix("#")
		.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
		.take(6)
	return "#${raw.uppercase()}"
}

private fun readableColorFor(color: Color): Color =
	if (color.luminance() > 0.45f) Color(0xFF111418) else Color.White

private val AccentSwatches = listOf("#74D7B2", "#4FC3F7", "#FFD166", "#FF8A65", "#CE93D8", "#EF5350")
private val BackgroundSwatches = listOf("#101312", "#121212", "#0E1420", "#18151F", "#F7FAF6", "#FFFFFF")
private val SurfaceSwatches = listOf("#151917", "#222A26", "#1B2130", "#211B24", "#F0F4F1", "#FFFFFF")
private val NavigationSwatches = listOf("#15231D", "#161A18", "#101827", "#21151F", "#EAF1EC", "#FFFFFF")

@Composable
private fun SourceAccountSetting(
	stateKey: String,
	title: String,
	tokenLabel: String,
	@DrawableRes icon: Int,
	getEnabled: () -> Boolean,
	setEnabled: (Boolean) -> Unit,
	getToken: () -> String,
	setToken: (String) -> Unit
) {
	var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
	var token by rememberSaveable(stateKey) { mutableStateOf(getToken()) }
	var tokenVisible by rememberSaveable(stateKey) { mutableStateOf(false) }

	SwitchSetting(
		getValue = getEnabled,
		setValue = setEnabled,
		text = title,
		icon = icon,
		onClick = { expanded = !expanded },
		isExpanded = expanded
	)
	AnimatedVisibility(
		visible = expanded,
		enter = expandVertically(),
		exit = shrinkVertically()
	) {
		Column {
			Text(
				text = tokenLabel,
				style = MaterialTheme.typography.bodyMedium,
				color = ApkTheme.colors.textSecondary,
				modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
			)
			OutlinedTextField(
				value = token,
				onValueChange = {
					token = it
					setToken(it)
				},
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 8.dp),
				singleLine = true,
				visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
				trailingIcon = {
					IconButton(onClick = { tokenVisible = !tokenVisible }) {
						Icon(
							painter = painterResource(if (tokenVisible) R.drawable.ic_visible else R.drawable.ic_visible_off),
							contentDescription = stringResource(if (tokenVisible) R.string.hide_password else R.string.show_password),
							modifier = Modifier.size(24.dp)
						)
					}
				}
			)
		}
	}
}

@Composable
private fun SourcesSettings(
	viewModel: SettingsViewModel,
	ruStore404Count: Int,
	customRepos: List<CustomGitRepo>,
	fdroidRepos: List<FdroidRepo>,
	onEditRepo: (CustomGitRepo) -> Unit,
	onDeleteRepo: (CustomGitRepo) -> Unit,
	onAddRepo: () -> Unit,
	onAddFdroidRepo: () -> Unit,
	onEditFdroidRepo: (FdroidRepo) -> Unit,
	onDeleteFdroidRepo: (String) -> Unit,
	onToggleFdroidRepo: (String, Boolean) -> Unit
) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_sources_code_hosting) {
			SourceAccountSetting(
				stateKey = "codeberg",
				title = stringResource(R.string.source_codeberg),
				tokenLabel = stringResource(R.string.codeberg_token_label),
				icon = R.drawable.ic_codeberg,
				getEnabled = viewModel::getUseCodeberg,
				setEnabled = viewModel::setUseCodeberg,
				getToken = viewModel::getCodebergToken,
				setToken = viewModel::setCodebergToken
			)
			SourceAccountSetting(
				stateKey = "github",
				title = stringResource(R.string.source_github),
				tokenLabel = stringResource(R.string.github_token_label),
				icon = R.drawable.ic_github,
				getEnabled = viewModel::getUseGitHub,
				setEnabled = viewModel::setUseGitHub,
				getToken = viewModel::getGithubToken,
				setToken = viewModel::setGithubToken
			)
			SourceAccountSetting(
				stateKey = "gitlab",
				title = stringResource(R.string.source_gitlab),
				tokenLabel = stringResource(R.string.gitlab_token_label),
				icon = R.drawable.ic_gitlab,
				getEnabled = viewModel::getUseGitLab,
				setEnabled = viewModel::setUseGitLab,
				getToken = viewModel::getGitlabToken,
				setToken = viewModel::setGitlabToken
			)

			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			CustomGitReposSection(
				repos = customRepos,
				onAdd = onAddRepo,
				onEdit = onEditRepo,
				onDelete = onDeleteRepo
			)
		}
	}

	item {
		SettingsGroup(R.string.settings_sources_app_stores) {
			SwitchSetting({ viewModel.getUseApk20() }, { viewModel.setUseApk20(it) }, stringResource(R.string.source_apk20), R.drawable.ic_apk20)
			SwitchSetting({ viewModel.getUseApkMirror() }, { viewModel.setUseApkMirror(it) }, stringResource(R.string.source_apkmirror), R.drawable.ic_apkmirror)
			SwitchSetting({ viewModel.getUseApkPure() }, { viewModel.setUseApkPure(it) }, stringResource(R.string.source_apkpure), R.drawable.ic_apkpure)

			var huaweiExpanded by remember { mutableStateOf(false) }
			SwitchSetting(
				{ viewModel.getUseHuawei() },
				{ viewModel.setUseHuawei(it) },
				stringResource(R.string.source_huawei),
				R.drawable.ic_huawei,
				onClick = { huaweiExpanded = !huaweiExpanded },
				isExpanded = huaweiExpanded
			)
			AnimatedVisibility(
				visible = huaweiExpanded,
				enter = expandVertically(),
				exit = shrinkVertically()
			) {
				HuaweiRegionSetting(viewModel)
			}

			SwitchSetting({ viewModel.getUseAptoide() }, { viewModel.setUseAptoide(it) }, stringResource(R.string.source_aptoide), R.drawable.ic_aptoide)

			var fdroidExpanded by remember { mutableStateOf(false) }
			SwitchSetting(
				{ viewModel.getUseFdroid() },
				{ viewModel.setUseFdroid(it) },
				stringResource(R.string.source_fdroid),
				R.drawable.ic_fdroid,
				onClick = { fdroidExpanded = !fdroidExpanded },
				isExpanded = fdroidExpanded
			)
			AnimatedVisibility(
				visible = fdroidExpanded,
				enter = expandVertically(),
				exit = shrinkVertically()
			) {
				Column {
					FdroidReposSection(
						repos = fdroidRepos,
						onAdd = onAddFdroidRepo,
						onEdit = onEditFdroidRepo,
						onDelete = onDeleteFdroidRepo,
						onToggle = onToggleFdroidRepo
					)
				}
			}

			var samsungExpanded by remember { mutableStateOf(false) }
			SwitchSetting(
				{ viewModel.getUseSamsungGalaxyStore() },
				{ viewModel.setUseSamsungGalaxyStore(it) },
				stringResource(R.string.source_samsung_galaxy_store),
				R.drawable.ic_samsung_galaxy_store,
				onClick = { samsungExpanded = !samsungExpanded },
				isExpanded = samsungExpanded
			)
			AnimatedVisibility(
				visible = samsungExpanded,
				enter = expandVertically(),
				exit = shrinkVertically()
			) {
				SegmentedButtonSetting(
					text = stringResource(R.string.settings_samsung_region),
					options = listOf(
						stringResource(R.string.samsung_region_global),
						stringResource(R.string.samsung_region_china)
					),
					getValue = viewModel::getSamsungGalaxyStoreRegion,
					setValue = viewModel::setSamsungGalaxyStoreRegion,
					icon = R.drawable.ic_samsung_galaxy_store
				)
			}
			SwitchSetting({ viewModel.getUsePlay() }, { viewModel.setUsePlay(it) }, stringResource(R.string.source_play), R.drawable.ic_play)

			var rustoreExpanded by remember { mutableStateOf(false) }
			SwitchSetting(
				{ viewModel.getUseRuStore() },
				{ viewModel.setUseRuStore(it) },
				stringResource(R.string.source_rustore),
				R.drawable.ic_rustore,
				onClick = { rustoreExpanded = !rustoreExpanded },
				isExpanded = rustoreExpanded
			)
			AnimatedVisibility(
				visible = rustoreExpanded,
				enter = expandVertically(),
				exit = shrinkVertically()
			) {
				Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
					val clearTextBase = stringResource(R.string.clear_rustore_cache)
					val clearText = if (ruStore404Count > 0) "$clearTextBase ($ruStore404Count)" else clearTextBase
					ButtonSetting(
						clearText,
						{ viewModel.clearRuStoreCache() },
						R.drawable.ic_rustore
					)
				}
			}

			SwitchSetting({ viewModel.getUseUptodown() }, { viewModel.setUseUptodown(it) }, stringResource(R.string.source_uptodown), R.drawable.ic_uptodown)
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
}

private data class HuaweiRegionOption(
	val code: String,
	val localizedName: String,
	val englishName: String
)

@Composable
private fun HuaweiRegionSetting(viewModel: SettingsViewModel) {
	var selectedCode by remember { mutableStateOf(viewModel.getHuaweiRegion()) }
	var showPicker by rememberSaveable { mutableStateOf(false) }
	val title = stringResource(R.string.settings_huawei_region)
	val auto = stringResource(R.string.huawei_region_auto)
	val selectedText = if (selectedCode == "AUTO") auto else selectedCode

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 72.dp)
			.tvFocusOutline(ApkTheme.shapes.sm)
			.clickable { showPicker = true }
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = CenterVertically
	) {
		SettingsIcon(R.drawable.ic_huawei, title, Modifier.padding(end = 16.dp))
		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge,
			color = ApkTheme.colors.textPrimary,
			modifier = Modifier.weight(1f)
		)
		Surface(
			shape = ApkTheme.shapes.sm,
			color = ApkTheme.colors.surfaceSecondary
		) {
			Row(
				modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
				verticalAlignment = CenterVertically
			) {
				Text(
					text = selectedText,
					style = MaterialTheme.typography.labelLarge,
					color = ApkTheme.colors.textPrimary
				)
				Icon(
					imageVector = Icons.Default.KeyboardArrowDown,
					contentDescription = null,
					modifier = Modifier.padding(start = 4.dp).size(18.dp),
					tint = ApkTheme.colors.textSecondary
				)
			}
		}
	}

	if (showPicker) {
		HuaweiRegionDialog(
			selectedCode = selectedCode,
			onDismiss = { showPicker = false },
			onSelect = { code ->
				selectedCode = code
				viewModel.setHuaweiRegion(code)
				showPicker = false
			}
		)
	}
}

@Composable
private fun HuaweiRegionDialog(
	selectedCode: String,
	onDismiss: () -> Unit,
	onSelect: (String) -> Unit
) {
	var query by rememberSaveable { mutableStateOf("") }
	val locale = Locale.getDefault()
	val localeTag = locale.toLanguageTag()
	val options = remember(localeTag) {
		Locale.getISOCountries()
			.map { code ->
				val regionLocale = Locale.Builder().setRegion(code).build()
				HuaweiRegionOption(
					code = code,
					localizedName = regionLocale.getDisplayCountry(locale).ifBlank { code },
					englishName = regionLocale.getDisplayCountry(Locale.ENGLISH).ifBlank { code }
				)
			}
			.sortedBy { it.localizedName.lowercase(locale) }
	}
	val normalizedQuery = query.trim().lowercase(locale)
	val filteredOptions = remember(options, normalizedQuery, localeTag) {
		if (normalizedQuery.isBlank()) {
			options
		} else {
			options.filter { option ->
				option.code.lowercase(Locale.ROOT).contains(normalizedQuery) ||
					option.localizedName.lowercase(locale).contains(normalizedQuery) ||
					option.englishName.lowercase(Locale.ENGLISH).contains(normalizedQuery)
			}
		}
	}
	val auto = stringResource(R.string.huawei_region_auto)
	val autoMatches = normalizedQuery.isBlank() ||
		auto.lowercase(locale).contains(normalizedQuery) ||
		"auto".contains(normalizedQuery)

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.search_filter_cancel))
			}
		},
		title = { Text(stringResource(R.string.settings_huawei_region)) },
		text = {
			Column(
				modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				OutlinedTextField(
					value = query,
					onValueChange = { query = it },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					label = { Text(stringResource(R.string.huawei_region_search)) },
					leadingIcon = {
						Icon(Icons.Default.Search, contentDescription = null)
					},
					trailingIcon = if (query.isNotEmpty()) {
						{
							IconButton(onClick = { query = "" }) {
								Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
							}
						}
					} else null
				)

				if (!autoMatches && filteredOptions.isEmpty()) {
					Text(
						text = stringResource(R.string.huawei_region_no_results),
						style = MaterialTheme.typography.bodyMedium,
						color = ApkTheme.colors.textSecondary,
						modifier = Modifier.padding(vertical = 16.dp)
					)
				} else {
					LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
						if (autoMatches) {
							item(key = "AUTO") {
								HuaweiRegionRow(
									name = auto,
									code = "AUTO",
									selected = selectedCode == "AUTO",
									onClick = { onSelect("AUTO") }
								)
							}
						}
						items(filteredOptions, key = { it.code }) { option ->
							HuaweiRegionRow(
								name = option.localizedName,
								code = option.code,
								selected = selectedCode == option.code,
								onClick = { onSelect(option.code) }
							)
						}
					}
				}
			}
		}
	)
}

@Composable
private fun HuaweiRegionRow(
	name: String,
	code: String,
	selected: Boolean,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 52.dp)
			.tvFocusOutline(ApkTheme.shapes.sm)
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = CenterVertically
	) {
		Text(
			text = name,
			style = MaterialTheme.typography.bodyLarge,
			color = ApkTheme.colors.textPrimary,
			modifier = Modifier.weight(1f)
		)
		if (code != "AUTO") {
			Text(
				text = code,
				style = MaterialTheme.typography.labelLarge,
				color = ApkTheme.colors.textSecondary,
				modifier = Modifier.padding(start = 12.dp)
			)
		}
		if (selected) {
			Icon(
				imageVector = Icons.Default.Check,
				contentDescription = null,
				tint = ApkTheme.colors.accent,
				modifier = Modifier.padding(start = 12.dp).size(20.dp)
			)
		}
	}
}

@Composable
private fun UpdatesSettings(
	viewModel: SettingsViewModel,
	updateMetrics: UpdateMetrics,
	onManageIgnoredUpdates: () -> Unit
) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_updates_release_filters) {
			SwitchSetting({ viewModel.getIgnoreAlpha() }, { viewModel.setIgnoreAlpha(it) }, stringResource(R.string.ignore_alpha), R.drawable.ic_alpha)
			SwitchSetting({ viewModel.getIgnoreBeta() }, { viewModel.setIgnoreBeta(it) }, stringResource(R.string.ignore_beta), R.drawable.ic_beta)
			SwitchSetting({ viewModel.getIgnorePreRelease() }, { viewModel.setIgnorePreRelease(it) }, stringResource(R.string.ignore_preRelease), R.drawable.ic_pre_release)
		}
	}
	item {
		SettingsGroup(R.string.settings_updates_ignored) {
			ButtonSetting(stringResource(R.string.manage_ignored_updates), onManageIgnoredUpdates, R.drawable.ic_disabled)
		}
	}
	item {
		SettingsGroup(R.string.settings_metrics) {
			MetricInlineRow(
				text = stringResource(R.string.metric_last_check_duration),
				value = updateMetrics.durationMs?.let { formatDuration(it) } ?: stringResource(R.string.metric_no_data),
				icon = R.drawable.ic_hour
			)
			MetricInlineRow(
				text = stringResource(R.string.metric_last_check_time),
				value = updateMetrics.timestamp?.let { formatTimestamp(it) } ?: stringResource(R.string.metric_no_data),
				icon = R.drawable.ic_info
			)
			MetricInlineRow(
				text = stringResource(R.string.metric_last_check_sources),
				value = updateMetrics.sources?.toString() ?: stringResource(R.string.metric_no_data),
				icon = R.drawable.ic_safe
			)
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun NotificationsSettings(
	viewModel: SettingsViewModel,
	installMode: Int
) {
	var checksEnabled by remember { mutableStateOf(viewModel.getEnableAlarm()) }
	var autoInstallEnabled by remember { mutableStateOf(viewModel.getAutoUpdateBackground()) }
	LazyColumn {
		item {
			LargeTitle(
				stringResource(R.string.settings_system_notifications),
				Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
			)
			NotificationStatusCard(viewModel)
		}
		item {
			SettingsGroup(R.string.settings_background_checks) {
				SwitchSetting(
					getValue = { checksEnabled },
					setValue = {
						checksEnabled = it
						viewModel.setEnableAlarm(it)
					},
					text = stringResource(R.string.settings_background_check),
					subtitle = stringResource(R.string.settings_background_check_desc),
					icon = R.drawable.ic_alarm
				)
				SegmentedButtonSetting(
					text = stringResource(R.string.frequency),
					options = listOf(
						stringResource(R.string.settings_alarm_daily),
						stringResource(R.string.settings_alarm_3day),
						stringResource(R.string.settings_alarm_weekly)
					),
					getValue = { viewModel.getAlarmFrequency() },
					setValue = { viewModel.setAlarmFrequency(it) },
					icon = R.drawable.ic_frequency,
					enabled = checksEnabled
				)
				SliderSetting(
					getValue = { viewModel.getAlarmHour().toFloat() },
					setValue = { viewModel.setAlarmHour(it.toInt()) },
					text = stringResource(R.string.settings_hour),
					valueRange = 0f..23f,
					icon = R.drawable.ic_hour,
					enabled = checksEnabled
				)
			}
		}
		item {
			SettingsGroup(R.string.settings_notification_events) {
				SwitchSetting(
					{ viewModel.getNotifyAvailableUpdates() },
					{ viewModel.setNotifyAvailableUpdates(it) },
					stringResource(R.string.settings_notify_available_updates),
					R.drawable.ic_refresh
				)
				SwitchSetting(
					{ viewModel.getNotifyAutoInstallSuccess() },
					{ viewModel.setNotifyAutoInstallSuccess(it) },
					stringResource(R.string.settings_notify_auto_install_success),
					R.drawable.ic_install
				)
				SwitchSetting(
					{ viewModel.getNotifyBackgroundErrors() },
					{ viewModel.setNotifyBackgroundErrors(it) },
					stringResource(R.string.settings_notify_background_errors),
					R.drawable.ic_disabled
				)
				SwitchSetting(
					{ viewModel.getNotifyNoUpdates() },
					{ viewModel.setNotifyNoUpdates(it) },
					stringResource(R.string.settings_notify_no_updates),
					R.drawable.ic_safe
				)
			}
		}
		item {
			SettingsGroup(R.string.settings_automatic_installation) {
				SwitchSetting(
					getValue = { autoInstallEnabled },
					setValue = {
						autoInstallEnabled = it
						viewModel.setAutoUpdateBackground(it)
					},
					text = stringResource(R.string.settings_auto_update),
					subtitle = stringResource(R.string.settings_auto_update_desc),
					icon = R.drawable.ic_install,
					enabled = checksEnabled && installMode > 0
				)
				SwitchSetting(
					getValue = { viewModel.getAutoInstallWifiOnly() },
					setValue = { viewModel.setAutoInstallWifiOnly(it) },
					text = stringResource(R.string.settings_auto_install_wifi_only),
					subtitle = stringResource(R.string.settings_auto_install_wifi_only_desc),
					icon = R.drawable.ic_system,
					enabled = checksEnabled && installMode > 0 && autoInstallEnabled
				)
			}
		}
		item { Spacer(Modifier.height(24.dp)) }
	}
}

@Composable
private fun InstallSettings(viewModel: SettingsViewModel) {
	val availability by viewModel.installModeAvailability.collectAsStateWithLifecycle()
	LazyColumn {
		item {
			SettingsGroup(R.string.settings_install_mode_section) {
				SegmentedButtonSetting(
					stringResource(R.string.install_mode),
					listOf(
						stringResource(R.string.install_mode_normal),
						stringResource(R.string.install_mode_root),
						stringResource(R.string.install_mode_shizuku)
					),
					{ viewModel.getInstallMode() },
					{ viewModel.setInstallMode(it) },
					R.drawable.ic_install,
					enabledItems = availability.enabledModes
				)
			}
		}
		item {
			SettingsGroup(R.string.settings_install_access) {
				MetricInlineRow(
					text = stringResource(R.string.settings_install_root_status),
					value = stringResource(
						if (availability.rootAvailable) R.string.settings_status_available
						else R.string.settings_status_unavailable
					),
					icon = R.drawable.ic_root
				)
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				MetricInlineRow(
					text = stringResource(R.string.settings_install_shizuku_status),
					value = stringResource(
						when {
							availability.shizukuPermissionGranted -> R.string.settings_status_available
							availability.shizukuRunning -> R.string.settings_status_permission_required
							else -> R.string.settings_status_unavailable
						}
					),
					icon = R.drawable.ic_install
				)
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				ButtonSetting(
					stringResource(R.string.settings_install_refresh_status),
					viewModel::refreshInstallModeAvailability,
					R.drawable.ic_refresh
				)
			}
		}
		item { Spacer(Modifier.height(24.dp)) }
	}
}

@Composable
private fun NetworkSettings(viewModel: SettingsViewModel) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_network_timeouts) {
			SliderSetting(
				getValue = { viewModel.getGlobalTimeoutSec().toFloat() },
				setValue = { viewModel.setGlobalTimeoutSec(it.toInt()) },
				text = stringResource(R.string.settings_global_timeout),
				valueRange = 10f..120f,
				icon = R.drawable.ic_system
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SliderSetting(
				getValue = { viewModel.getPlayTimeoutSec().toFloat() },
				setValue = { viewModel.setPlayTimeoutSec(it.toInt()) },
				text = stringResource(R.string.settings_play_timeout),
				valueRange = 10f..120f,
				icon = R.drawable.ic_play
			)
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun DataLogsSettings(
	viewModel: SettingsViewModel,
	downloadFolderLabel: String,
	hasCustomDownloadFolder: Boolean,
	onChooseDownloadFolder: () -> Unit,
	onResetDownloadFolder: () -> Unit,
	onExport: () -> Unit,
	onImport: () -> Unit
) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_downloads) {
			MetricInlineRow(
				text = stringResource(R.string.download_folder),
				value = downloadFolderLabel,
				icon = R.drawable.ic_download
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			ButtonSetting(stringResource(R.string.download_folder_choose), onChooseDownloadFolder, R.drawable.ic_download)
			if (hasCustomDownloadFolder) {
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				ButtonSetting(stringResource(R.string.download_folder_reset), onResetDownloadFolder, R.drawable.ic_refresh)
			}
		}
	}
	item {
		SettingsGroup(R.string.settings_data_logs_actions) {
			ButtonSetting(stringResource(R.string.copy_app_list), { viewModel.copyAppList() }, R.drawable.ic_root)
			ButtonSetting(stringResource(R.string.copy_app_logs), { viewModel.copyAppLogs() }, R.drawable.ic_root)
		}
	}
	item {
		SettingsGroup(R.string.settings_data_backup) {
			ButtonSetting(stringResource(R.string.settings_export), onExport, R.drawable.ic_safe)
			ButtonSetting(stringResource(R.string.settings_import), onImport, R.drawable.ic_safe)
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun MetricInlineRow(text: String, value: String, @DrawableRes icon: Int) = Row(
	modifier = Modifier
		.fillMaxWidth()
		.padding(horizontal = 16.dp, vertical = 12.dp),
	verticalAlignment = Alignment.CenterVertically
) {
	SettingsIcon(icon, text, Modifier.padding(end = 16.dp))
	Column(Modifier.weight(1f)) {
		Text(text, style = MaterialTheme.typography.bodyLarge, color = ApkTheme.colors.textPrimary)
		Text(value, style = MaterialTheme.typography.bodyMedium, color = ApkTheme.colors.textSecondary)
	}
}

@Composable
private fun CustomGitReposSection(
	repos: List<CustomGitRepo>,
	onAdd: () -> Unit,
	onEdit: (CustomGitRepo) -> Unit,
	onDelete: (CustomGitRepo) -> Unit
) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 8.dp, top = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			stringResource(R.string.settings_custom_repos),
			style = MaterialTheme.typography.titleMedium,
			color = ApkTheme.colors.textPrimary,
			modifier = Modifier.weight(1f)
		)
		IconButton(onClick = onAdd) {
			Icon(
				Icons.Filled.Add,
				contentDescription = stringResource(R.string.settings_custom_repos_add),
				tint = ApkTheme.colors.textSecondary
			)
		}
	}
	if (repos.isEmpty()) {
		Text(
			text = stringResource(R.string.settings_custom_repo_empty),
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			style = MaterialTheme.typography.bodyMedium,
			color = ApkTheme.colors.textSecondary
		)
	} else {
		repos.forEach { repo ->
			CustomRepoCard(
				repo = repo,
				onEdit = { onEdit(repo) },
				onDelete = { onDelete(repo) }
			)
		}
	}
}

@Composable
private fun CustomRepoCard(
	repo: CustomGitRepo,
	onEdit: () -> Unit,
	onDelete: () -> Unit
) {
	val editDescription = stringResource(R.string.settings_custom_repos_edit)
	val deleteDescription = stringResource(R.string.settings_repository_delete)
	AppListSurface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		Column(Modifier.fillMaxWidth().padding(16.dp)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				SettingsIcon(providerIcon(repo.platform), repo.platform.name)
				Spacer(Modifier.width(16.dp))
				Column(Modifier.weight(1f)) {
					Text("${repo.user}/${repo.repo}", style = MaterialTheme.typography.titleMedium, color = ApkTheme.colors.textPrimary)
					Text(repo.packageName, style = MaterialTheme.typography.bodyMedium, color = ApkTheme.colors.textSecondary)
					repo.extraRegex?.takeIf { it.isNotBlank() }?.let {
						Text(it, style = MaterialTheme.typography.bodySmall, color = ApkTheme.colors.textSecondary)
					}
				}
				if (!ApkTheme.accessibility.isLargeText) {
					IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = editDescription, tint = ApkTheme.colors.textSecondary) }
					IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = deleteDescription, tint = ApkTheme.colors.error) }
				}
			}
			if (ApkTheme.accessibility.isLargeText) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = editDescription, tint = ApkTheme.colors.textSecondary) }
					IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = deleteDescription, tint = ApkTheme.colors.error) }
				}
			}
		}
	}
}

@Composable
private fun CustomRepoDialog(
	repo: CustomGitRepo,
	viewModel: SettingsViewModel,
	onDismiss: () -> Unit,
	onSave: (CustomGitRepo) -> Unit
) {
	val initialUrl = remember(repo.id) { repo.webUrl() }
	val isExisting = remember(repo.id) {
		repo.user.isNotBlank() && repo.repo.isNotBlank() && repo.packageName.isNotBlank()
	}
	var url by remember(repo.id) { mutableStateOf(initialUrl) }
	var provider by remember(repo.id) { mutableStateOf(repo.platform) }
	var user by remember(repo.id) { mutableStateOf(repo.user) }
	var project by remember(repo.id) { mutableStateOf(repo.repo) }
	var packageName by remember(repo.id) { mutableStateOf(repo.packageName) }
	var regex by remember(repo.id) { mutableStateOf(repo.extraRegex.orEmpty()) }
	var showAdvanced by remember(repo.id) { mutableStateOf(false) }
	var retryCounter by remember(repo.id) { mutableIntStateOf(0) }
	var detectionState by remember(repo.id) {
		mutableStateOf<CustomRepoDetectionUiState>(
			if (isExisting) {
				CustomRepoDetectionUiState.Ready(
					DetectedGitRepo(
						provider = repo.platform,
						user = repo.user,
						repo = repo.repo,
						packageName = repo.packageName,
						appName = repo.repo,
						versionName = null,
						assetName = "",
						extraRegex = repo.extraRegex
					)
				)
			} else {
				CustomRepoDetectionUiState.Idle
			}
		)
	}

	LaunchedEffect(url, retryCounter) {
		val normalizedUrl = url.trim()
		if (normalizedUrl.isBlank()) {
			detectionState = CustomRepoDetectionUiState.Idle
			return@LaunchedEffect
		}

		val parsed = parseRepoUrl(normalizedUrl)
		if (parsed != null) {
			provider = parsed.provider
			user = parsed.user
			project = parsed.repo
		}
		if (normalizedUrl != initialUrl) {
			packageName = ""
			regex = ""
		}

		if (isExisting && retryCounter == 0 && normalizedUrl == initialUrl) return@LaunchedEffect

		detectionState = CustomRepoDetectionUiState.Loading
		delay(450)
		if (parsed == null) {
			detectionState = CustomRepoDetectionUiState.Error(GitRepoDetectionFailure.INVALID_URL)
			return@LaunchedEffect
		}

		when (val result = viewModel.detectCustomGitRepo(normalizedUrl)) {
			is GitRepoDetectionResult.Success -> {
				val detected = result.repo
				provider = detected.provider
				user = detected.user
				project = detected.repo
				packageName = detected.packageName
				regex = detected.extraRegex.orEmpty()
				detectionState = CustomRepoDetectionUiState.Ready(detected)
			}
			is GitRepoDetectionResult.Failure -> {
				detectionState = CustomRepoDetectionUiState.Error(result.reason)
			}
		}
	}

	val canSave = parseRepoUrl(url) != null && user.isNotBlank() && project.isNotBlank() &&
		packageName.isNotBlank() && detectionState !is CustomRepoDetectionUiState.Loading
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				enabled = canSave,
				onClick = {
				onSave(
					repo.copy(
						platform = provider,
						user = user,
						repo = project,
						packageName = packageName,
						extraRegex = regex.ifBlank { null }
					)
				)
			}) {
				Text(stringResource(R.string.settings_custom_repo_save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_custom_repo_cancel)) }
		},
		title = { Text(stringResource(if (repo.user.isBlank() && repo.repo.isBlank() && repo.packageName.isBlank()) R.string.settings_custom_repos_add else R.string.settings_custom_repos_edit)) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					modifier = Modifier.fillMaxWidth(),
					value = url,
					onValueChange = { url = it },
					label = { Text(stringResource(R.string.settings_custom_repo_url)) },
					placeholder = { Text("https://github.com/owner/repository") },
					singleLine = true
				)

				when (val state = detectionState) {
					CustomRepoDetectionUiState.Idle -> Unit
					CustomRepoDetectionUiState.Loading -> Row(
						modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						CircularProgressIndicator(
							modifier = Modifier.size(24.dp),
							strokeWidth = 2.dp,
							color = ApkTheme.colors.accent
						)
						Spacer(Modifier.width(12.dp))
						Text(
							text = stringResource(R.string.settings_custom_repo_detecting),
							style = MaterialTheme.typography.bodyMedium,
							color = ApkTheme.colors.textSecondary
						)
					}
					is CustomRepoDetectionUiState.Ready -> Row(
						modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						SettingsIcon(providerIcon(state.repo.provider), state.repo.provider.name)
						Spacer(Modifier.width(12.dp))
						Column(Modifier.weight(1f)) {
							Text(
								text = state.repo.appName,
								style = MaterialTheme.typography.titleMedium,
								color = ApkTheme.colors.textPrimary
							)
							Text(
								text = state.repo.packageName,
								style = MaterialTheme.typography.bodyMedium,
								color = ApkTheme.colors.textSecondary
							)
							state.repo.assetName.takeIf { it.isNotBlank() }?.let { asset ->
								Text(
									text = asset,
									style = MaterialTheme.typography.bodySmall,
									color = ApkTheme.colors.textSecondary,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis
								)
							}
						}
						Icon(Icons.Filled.Check, contentDescription = null, tint = ApkTheme.colors.accent)
					}
					is CustomRepoDetectionUiState.Error -> Column(
						modifier = Modifier.fillMaxWidth()
					) {
						Text(
							text = stringResource(detectionFailureString(state.reason)),
							color = ApkTheme.colors.error,
							style = MaterialTheme.typography.bodyMedium
						)
						if (state.reason != GitRepoDetectionFailure.INVALID_URL) {
							TextButton(onClick = { retryCounter++ }) {
								Text(stringResource(R.string.settings_custom_repo_retry))
							}
						}
					}
				}

				TextButton(onClick = { showAdvanced = !showAdvanced }) {
					Text(stringResource(R.string.settings_custom_repo_advanced))
					Icon(
						imageVector = if (showAdvanced) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
						contentDescription = null
					)
				}
				AnimatedVisibility(
					visible = showAdvanced,
					enter = expandVertically(),
					exit = shrinkVertically()
				) {
					Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
						OutlinedTextField(
							modifier = Modifier.fillMaxWidth(),
							value = packageName,
							onValueChange = { packageName = it },
							label = { Text(stringResource(R.string.settings_custom_repo_package)) },
							singleLine = true
						)
						OutlinedTextField(
							modifier = Modifier.fillMaxWidth(),
							value = regex,
							onValueChange = { regex = it },
							label = { Text(stringResource(R.string.settings_custom_repo_regex)) },
							singleLine = true
						)
					}
				}
			}
		}
	)
}

private sealed interface CustomRepoDetectionUiState {
	data object Idle : CustomRepoDetectionUiState
	data object Loading : CustomRepoDetectionUiState
	data class Ready(val repo: DetectedGitRepo) : CustomRepoDetectionUiState
	data class Error(val reason: GitRepoDetectionFailure) : CustomRepoDetectionUiState
}

private fun CustomGitRepo.webUrl(): String {
	if (user.isBlank() || repo.isBlank()) return ""
	val host = when (platform) {
		GitProvider.CODEBERG -> "codeberg.org"
		GitProvider.GITHUB -> "github.com"
		GitProvider.GITLAB -> "gitlab.com"
	}
	return "https://$host/$user/$repo"
}

@StringRes
private fun detectionFailureString(reason: GitRepoDetectionFailure): Int = when (reason) {
	GitRepoDetectionFailure.INVALID_URL -> R.string.settings_custom_repo_error_url
	GitRepoDetectionFailure.REPOSITORY_UNAVAILABLE -> R.string.settings_custom_repo_error_unavailable
	GitRepoDetectionFailure.NO_RELEASES -> R.string.settings_custom_repo_error_no_releases
	GitRepoDetectionFailure.NO_COMPATIBLE_APK -> R.string.settings_custom_repo_error_no_apk
	GitRepoDetectionFailure.DOWNLOAD_FAILED -> R.string.settings_custom_repo_error_download
	GitRepoDetectionFailure.INVALID_APK -> R.string.settings_custom_repo_error_invalid_apk
}

@DrawableRes
private fun providerIcon(provider: GitProvider) = when (provider) {
	GitProvider.CODEBERG -> R.drawable.ic_codeberg
	GitProvider.GITHUB -> R.drawable.ic_github
	GitProvider.GITLAB -> R.drawable.ic_gitlab
}

private fun formatDuration(durationMs: Long): String {
	val seconds = durationMs / 1000.0
	return String.format(Locale.ROOT, "%.1f s", seconds)
}

private fun formatTimestamp(timestamp: Long): String =
	DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun MetricRow(text: String, value: String, @DrawableRes icon: Int) = AppListSurface(
	modifier = Modifier
		.fillMaxWidth()
		.padding(horizontal = 16.dp, vertical = 4.dp)
) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SettingsIcon(icon, text, Modifier.padding(end = 16.dp))
		Column(Modifier.weight(1f)) {
			Text(text, style = MaterialTheme.typography.bodyLarge, color = ApkTheme.colors.textPrimary)
			Text(value, style = MaterialTheme.typography.bodyMedium, color = ApkTheme.colors.textSecondary)
		}
	}
}

@Composable
private fun NotificationStatusCard(viewModel: SettingsViewModel) {
	val notificationStatus = remember { mutableStateOf(viewModel.areNotificationsEnabled()) }
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
		notificationStatus.value = viewModel.areNotificationsEnabled()
	}
	val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		notificationStatus.value = viewModel.areNotificationsEnabled()
	}
	AppListSurface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		Column(Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				SettingsIcon(
					icon = if (notificationStatus.value) R.drawable.ic_alarm else R.drawable.ic_disabled,
					contentDescription = stringResource(R.string.settings_notifications),
					modifier = Modifier.padding(end = 16.dp)
				)
				Text(
					text = if (notificationStatus.value) stringResource(R.string.notifications_status_on) else stringResource(R.string.notifications_status_off),
					style = MaterialTheme.typography.bodyLarge,
					color = ApkTheme.colors.textPrimary
				)
			}
			Spacer(Modifier.height(12.dp))
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (!notificationStatus.value) {
					FilledTonalButton(
						modifier = Modifier.tvFocusOutline(ApkTheme.shapes.md),
						onClick = { viewModel.requestNotificationPermission(launcher) }
					) {
						Text(stringResource(R.string.notifications_enable_action))
					}
				}
				TextButton(
					onClick = { settingsLauncher.launch(viewModel.notificationSettingsIntent()) }
				) {
					Text(stringResource(R.string.notifications_open_settings))
				}
			}
		}
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
private fun FdroidReposSection(
	repos: List<FdroidRepo>,
	onAdd: () -> Unit,
	onEdit: (FdroidRepo) -> Unit,
	onDelete: (String) -> Unit,
	onToggle: (String, Boolean) -> Unit
) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 8.dp, top = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(stringResource(R.string.settings_fdroid_repos), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
		IconButton(onClick = onAdd) {
			Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_custom_repos_add))
		}
	}
	repos.forEach { repo ->
		FdroidRepoCard(
			repo = repo,
			onEdit = { onEdit(repo) },
			onDelete = { onDelete(repo.id) },
			onToggle = { onToggle(repo.id, it) }
		)
	}
}

@Composable
private fun FdroidRepoCard(
	repo: FdroidRepo,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	onToggle: (Boolean) -> Unit
) {
	val editDescription = stringResource(R.string.settings_custom_repos_edit)
	val deleteDescription = stringResource(R.string.settings_repository_delete)
	AppListSurface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp)
	) {
		Column(Modifier.fillMaxWidth().padding(12.dp)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				androidx.compose.material3.Switch(
					checked = repo.isEnabled,
					onCheckedChange = onToggle,
					modifier = Modifier.padding(end = 12.dp)
				)
				Column(Modifier.weight(1f)) {
					Text(repo.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
					if (repo.isDefault) {
						Surface(
							color = ApkTheme.colors.surfaceHighlight,
							shape = ApkTheme.shapes.xs,
							border = BorderStroke(1.dp, ApkTheme.colors.accent.copy(alpha = 0.28f)),
							modifier = Modifier.padding(top = 4.dp)
						) {
							Text(
								stringResource(R.string.settings_fdroid_repo_default),
								Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								style = MaterialTheme.typography.labelSmall,
								color = ApkTheme.colors.accent
							)
						}
					}
					Text(
						repo.url,
						style = MaterialTheme.typography.bodySmall,
						color = ApkTheme.colors.textSecondary,
						maxLines = if (ApkTheme.accessibility.isLargeText) 2 else 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				if (!ApkTheme.accessibility.isLargeText) {
					IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = editDescription, modifier = Modifier.size(20.dp), tint = ApkTheme.colors.textSecondary) }
					if (!repo.isDefault) {
						IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = deleteDescription, modifier = Modifier.size(20.dp), tint = ApkTheme.colors.error) }
					}
				}
			}
			if (ApkTheme.accessibility.isLargeText) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = editDescription, modifier = Modifier.size(20.dp), tint = ApkTheme.colors.textSecondary) }
					if (!repo.isDefault) {
						IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = deleteDescription, modifier = Modifier.size(20.dp), tint = ApkTheme.colors.error) }
					}
				}
			}
		}
	}
}

@Composable
private fun FdroidRepoDialog(
	repo: FdroidRepo,
	onDismiss: () -> Unit,
	onSave: (FdroidRepo) -> Unit
) {
	var name by remember(repo.id) { mutableStateOf(repo.name) }
	var url by remember(repo.id) { mutableStateOf(repo.url) }
	var showError by remember { mutableStateOf(false) }
	var showUrlError by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = {
				if (name.isBlank() || url.isBlank()) {
					showError = true
					return@TextButton
				}
				val normalizedUrl = normalizeFdroidRepoUrl(url)
				if (normalizedUrl == null) {
					showUrlError = true
					return@TextButton
				}
				onSave(repo.copy(name = name.trim(), url = normalizedUrl))
			}) {
				Text(stringResource(R.string.settings_custom_repo_save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_custom_repo_cancel)) }
		},
		title = { Text(stringResource(if (repo.name.isBlank() && repo.url.isBlank()) R.string.settings_custom_repos_add else R.string.settings_custom_repos_edit)) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it; showError = false },
					label = { Text(stringResource(R.string.settings_fdroid_repo_name)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = url,
					onValueChange = { url = it; showError = false; showUrlError = false },
					label = { Text(stringResource(R.string.settings_fdroid_repo_url)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				if (showError || showUrlError) {
					Text(
						text = stringResource(
							if (showUrlError) R.string.settings_fdroid_repo_error_invalid_url
							else R.string.settings_fdroid_repo_error_required
						),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
				}
			}
		}
	)
}

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
				androidx.compose.foundation.lazy.LazyColumn(
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
