package com.apkupdateross.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
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
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.git.parseRepoUrl
import com.apkupdateross.data.ui.SettingsUiState
import com.apkupdateross.data.ui.SwipeIgnoreDirection
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.AppListSurface
import com.apkupdateross.ui.component.AppScreen
import com.apkupdateross.ui.component.AppTopBar
import com.apkupdateross.ui.component.LargeTitle
import com.apkupdateross.ui.component.LoadingImageApp
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SliderSetting
import com.apkupdateross.ui.component.SourceIcon
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.component.SwitchSetting
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
import java.util.Calendar
import java.util.Locale


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

	val alarmPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
			onBack = if (selectedSection == SettingsSection.Home) null else ({ selectedSection = SettingsSection.Home })
		)
		Settings(
			viewModel,
			ruStoreCacheCount,
			updateMetrics,
			customRepos,
			fdroidRepos,
			onAddRepo = { dialogRepo = viewModel.createEmptyCustomRepo(it) },
			onEditRepo = { dialogRepo = it },
			onDeleteRepo = { viewModel.removeCustomRepo(it.id) },
			onAddFdroidRepo = { dialogFdroidRepo = FdroidRepo(name = "", url = "") },
			onEditFdroidRepo = { dialogFdroidRepo = it },
			onDeleteFdroidRepo = { viewModel.removeFdroidRepo(it) },
			onToggleFdroidRepo = { id, enabled -> viewModel.toggleFdroidRepo(id, enabled) },
			notificationPermissionLauncher = alarmPermissionLauncher,
			onManageIgnoredUpdates = { dialogIgnoredUpdates = true },
			selectedSection = selectedSection,
			onSelectSection = { selectedSection = it },
			onAbout = { viewModel.setAbout() }
		)
	} else {
		AboutTopBar(onBack = { viewModel.setSettings() })
		About()
	}
	dialogRepo?.let { repo ->
		CustomRepoDialog(
			repo = repo,
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
	Modifier.fillMaxSize()
		.verticalScroll(rememberScrollState())
		.padding(24.dp),
	horizontalAlignment = Alignment.CenterHorizontally
) {
	Spacer(Modifier.height(48.dp))

	Surface(
		modifier = Modifier.size(112.dp),
		shape = ApkTheme.shapes.lg,
		color = ApkTheme.colors.surfaceSecondary,
		border = androidx.compose.foundation.BorderStroke(
			1.dp,
			ApkTheme.colors.divider
		)
	) {
		Box(contentAlignment = Alignment.Center) {
			LoadingImageApp(BuildConfig.APPLICATION_ID, Modifier.size(82.dp))
		}
	}

	Spacer(Modifier.height(32.dp))

	Text(
		text = stringResource(R.string.app_name),
		style = MaterialTheme.typography.displaySmall,
		fontWeight = FontWeight.ExtraBold,
		color = ApkTheme.colors.textOnBackground
	)

	Spacer(Modifier.height(12.dp))

	AssistChip(
		onClick = {},
		label = { Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
		leadingIcon = {
			Icon(
				Icons.Default.Info,
				null,
				Modifier.size(18.dp),
				tint = ApkTheme.colors.accent
			)
		},
		shape = ApkTheme.shapes.xs,
		border = null,
		colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
			containerColor = ApkTheme.colors.surfaceHighlight,
			labelColor = ApkTheme.colors.accent
		)
	)

	Spacer(Modifier.height(48.dp))

	// Links Section
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		SocialButton(
			icon = R.drawable.ic_github,
			label = stringResource(R.string.github),
			url = stringResource(R.string.github_url)
		)
		SocialButton(
			imageVector = Icons.Default.Favorite,
			label = stringResource(R.string.donate),
			url = stringResource(R.string.donate_url)
		)
	}

	Spacer(Modifier.height(64.dp))
	Spacer(Modifier.weight(1f))

	// Footer
	Text(
		text = "Copyright (c) ${Calendar.getInstance().get(Calendar.YEAR)}",
		style = MaterialTheme.typography.labelMedium,
		color = ApkTheme.colors.textOnBackground.copy(alpha = 0.58f)
	)
	Text(
		text = "rumboalla, NotDev",
		style = MaterialTheme.typography.labelLarge,
		fontWeight = FontWeight.Bold,
		color = ApkTheme.colors.textOnBackground.copy(alpha = 0.78f)
	)
	Spacer(Modifier.height(16.dp))
}

@Composable
private fun SocialButton(
	@DrawableRes icon: Int? = null,
	imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
	label: String,
	url: String
) {
	val context = LocalContext.current
	androidx.compose.material3.FilledTonalButton(
		onClick = {
			runCatching {
				val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
				context.startActivity(intent)
			}
		},
		modifier = Modifier
			.fillMaxWidth(0.7f)
			.height(56.dp),
		shape = ApkTheme.shapes.md,
		colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
			containerColor = ApkTheme.colors.surfaceSecondary,
			contentColor = ApkTheme.colors.textPrimary
		)
	) {
		if (icon != null) {
			Icon(
				painter = painterResource(icon),
				contentDescription = null,
				modifier = Modifier.size(24.dp),
				tint = ApkTheme.colors.accent
			)
		} else if (imageVector != null) {
			Icon(
				imageVector = imageVector,
				contentDescription = null,
				modifier = Modifier.size(24.dp),
				tint = ApkTheme.colors.accent
			)
		}
		Spacer(Modifier.width(12.dp))
		Text(
			text = label,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold
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
	onAddRepo: (GitProvider) -> Unit,
	onAddFdroidRepo: () -> Unit,
	onEditFdroidRepo: (FdroidRepo) -> Unit,
	onDeleteFdroidRepo: (String) -> Unit,
	onToggleFdroidRepo: (String, Boolean) -> Unit,
	notificationPermissionLauncher: ActivityResultLauncher<String>,
	onManageIgnoredUpdates: () -> Unit,
	selectedSection: SettingsSection,
	onSelectSection: (SettingsSection) -> Unit,
	onAbout: () -> Unit
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
		SettingsSection.Home -> SettingsHome(onSelectSection, onAbout)
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
			installMode = installMode,
			updateMetrics = updateMetrics,
			notificationPermissionLauncher = notificationPermissionLauncher,
			onManageIgnoredUpdates = onManageIgnoredUpdates
		)
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
	onAbout: () -> Unit
) = LazyColumn {
	item { Spacer(Modifier.height(12.dp)) }
	listOf(
		SettingsSection.Sources,
		SettingsSection.Updates,
		SettingsSection.Install,
		SettingsSection.Interface,
		SettingsSection.Network,
		SettingsSection.DataLogs
	).forEach { section ->
		item {
			SettingsCategoryItem(
				title = stringResource(section.titleRes),
				subtitle = stringResource(section.subtitleRes),
				icon = section.iconRes,
				onClick = { onSelectSection(section) }
			)
		}
	}
	item {
		SettingsCategoryItem(
			title = stringResource(R.string.about),
			subtitle = stringResource(R.string.settings_category_about_subtitle),
			icon = R.drawable.ic_info,
			onClick = onAbout
		)
	}
	item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun SettingsCategoryItem(
	title: String,
	subtitle: String,
	@DrawableRes icon: Int,
	onClick: () -> Unit
) = AppListSurface(
	modifier = Modifier
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
				color = ApkTheme.colors.textSecondary,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
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
							text = "1.3.0 -> 1.3.1",
							style = MaterialTheme.typography.bodyMedium,
							color = readableColorFor(surfaceColor).copy(alpha = 0.72f)
						)
					}
				}
			}
			Surface(
				modifier = Modifier.fillMaxWidth().height(28.dp),
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
				label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
private fun SourcesSettings(
	viewModel: SettingsViewModel,
	ruStore404Count: Int,
	customRepos: List<CustomGitRepo>,
	fdroidRepos: List<FdroidRepo>,
	onEditRepo: (CustomGitRepo) -> Unit,
	onDeleteRepo: (CustomGitRepo) -> Unit,
	onAddRepo: (GitProvider) -> Unit,
	onAddFdroidRepo: () -> Unit,
	onEditFdroidRepo: (FdroidRepo) -> Unit,
	onDeleteFdroidRepo: (String) -> Unit,
	onToggleFdroidRepo: (String, Boolean) -> Unit
) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_sources_code_hosting) {
			var githubExpanded by remember { mutableStateOf(false) }
			var githubToken by rememberSaveable { mutableStateOf(viewModel.getGithubToken()) }
			var githubTokenVisible by rememberSaveable { mutableStateOf(false) }
			SwitchSetting(
				{ viewModel.getUseGitHub() },
				{ viewModel.setUseGitHub(it) },
				stringResource(R.string.source_github),
				R.drawable.ic_github,
				onClick = { githubExpanded = !githubExpanded },
				isExpanded = githubExpanded
			)
			AnimatedVisibility(
				visible = githubExpanded,
				enter = expandVertically(),
				exit = shrinkVertically()
			) {
				Column {
					OutlinedTextField(
						value = githubToken,
						onValueChange = {
							githubToken = it
							viewModel.setGithubToken(it)
						},
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 8.dp),
						label = { Text(stringResource(R.string.github_token_label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
						singleLine = true,
						visualTransformation = if (githubTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
						trailingIcon = {
							IconButton(onClick = { githubTokenVisible = !githubTokenVisible }) {
								Icon(
									painter = painterResource(if (githubTokenVisible) R.drawable.ic_visible else R.drawable.ic_visible_off),
									contentDescription = stringResource(if (githubTokenVisible) R.string.hide_password else R.string.show_password),
									modifier = Modifier.size(24.dp)
								)
							}
						}
					)
					CustomGitReposSection(
						repos = customRepos.filter { it.platform == GitProvider.GITHUB },
						onAdd = { onAddRepo(GitProvider.GITHUB) },
						onEdit = onEditRepo,
						onDelete = onDeleteRepo
					)
				}
			}

			var gitlabExpanded by remember { mutableStateOf(false) }
			var gitlabToken by rememberSaveable { mutableStateOf(viewModel.getGitlabToken()) }
			var gitlabTokenVisible by rememberSaveable { mutableStateOf(false) }
			SwitchSetting(
				{ viewModel.getUseGitLab() },
				{ viewModel.setUseGitLab(it) },
				stringResource(R.string.source_gitlab),
				R.drawable.ic_gitlab,
				onClick = { gitlabExpanded = !gitlabExpanded },
				isExpanded = gitlabExpanded
			)
			AnimatedVisibility(
				visible = gitlabExpanded,
				enter = expandVertically(),
				exit = shrinkVertically()
			) {
				Column {
					OutlinedTextField(
						value = gitlabToken,
						onValueChange = {
							gitlabToken = it
							viewModel.setGitlabToken(it)
						},
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 8.dp),
						label = { Text(stringResource(R.string.gitlab_token_label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
						singleLine = true,
						visualTransformation = if (gitlabTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
						trailingIcon = {
							IconButton(onClick = { gitlabTokenVisible = !gitlabTokenVisible }) {
								Icon(
									painter = painterResource(if (gitlabTokenVisible) R.drawable.ic_visible else R.drawable.ic_visible_off),
									contentDescription = stringResource(if (gitlabTokenVisible) R.string.hide_password else R.string.show_password),
									modifier = Modifier.size(24.dp)
								)
							}
						}
					)
					CustomGitReposSection(
						repos = customRepos.filter { it.platform == GitProvider.GITLAB },
						onAdd = { onAddRepo(GitProvider.GITLAB) },
						onEdit = onEditRepo,
						onDelete = onDeleteRepo
					)
				}
			}
		}
	}

	item {
		SettingsGroup(R.string.settings_sources_app_stores) {
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

			SwitchSetting({ viewModel.getUseApkMirror() }, { viewModel.setUseApkMirror(it) }, stringResource(R.string.source_apkmirror), R.drawable.ic_apkmirror)
			SwitchSetting({ viewModel.getUseAptoide() }, { viewModel.setUseAptoide(it) }, stringResource(R.string.source_aptoide), R.drawable.ic_aptoide)
			SwitchSetting({ viewModel.getUseApkPure() }, { viewModel.setUseApkPure(it) }, stringResource(R.string.source_apkpure), R.drawable.ic_apkpure)
			SwitchSetting({ viewModel.getUseApkCombo() }, { viewModel.setUseApkCombo(it) }, stringResource(R.string.source_apkcombo), R.drawable.ic_apkcombo)
			SwitchSetting({ viewModel.getUseUptodown() }, { viewModel.setUseUptodown(it) }, stringResource(R.string.source_uptodown), R.drawable.ic_uptodown)
			SwitchSetting({ viewModel.getUsePlay() }, { viewModel.setUsePlay(it) }, stringResource(R.string.source_play), R.drawable.ic_play)

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
				SegmentedButtonSetting(
					stringResource(R.string.settings_huawei_region),
					listOf(
						stringResource(R.string.huawei_region_auto),
						"RU",
						"UA",
						"DE",
						"GB"
					),
					{ viewModel.getHuaweiRegion() },
					{ viewModel.setHuaweiRegion(it) },
					R.drawable.ic_huawei
				)
			}
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun UpdatesSettings(
	viewModel: SettingsViewModel,
	installMode: Int,
	updateMetrics: UpdateMetrics,
	notificationPermissionLauncher: ActivityResultLauncher<String>,
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
		SettingsGroup(R.string.settings_updates_schedule) {
			SwitchSetting(
				getValue = { viewModel.getEnableAlarm() },
				setValue = { viewModel.setEnableAlarm(it, notificationPermissionLauncher) },
				text = stringResource(R.string.settings_alarm),
				icon = R.drawable.ic_alarm
			)
			SwitchSetting(
				getValue = { viewModel.getAutoUpdateBackground() },
				setValue = { viewModel.setAutoUpdateBackground(it) },
				text = stringResource(R.string.settings_auto_update),
				subtitle = stringResource(R.string.settings_auto_update_desc),
				icon = R.drawable.ic_system,
				enabled = installMode > 0
			)
			SegmentedButtonSetting(
				stringResource(R.string.frequency),
				listOf(stringResource(R.string.settings_alarm_daily), stringResource(R.string.settings_alarm_3day), stringResource(R.string.settings_alarm_weekly)),
				{ viewModel.getAlarmFrequency() },
				{ viewModel.setAlarmFrequency(it) },
				R.drawable.ic_frequency
			)
			SliderSetting(
				{ viewModel.getAlarmHour().toFloat() },
				{ viewModel.setAlarmHour(it.toInt()) },
				stringResource(R.string.settings_hour),
				0f..23f,
				R.drawable.ic_hour
			)
		}
	}
	item {
		SettingsGroup(R.string.settings_updates_ignored) {
			ButtonSetting(stringResource(R.string.manage_ignored_updates), onManageIgnoredUpdates, R.drawable.ic_disabled)
		}
	}
	item {
		LargeTitle(stringResource(R.string.settings_notifications), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		NotificationStatusCard(viewModel)
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
private fun InstallSettings(viewModel: SettingsViewModel) = LazyColumn {
	item {
		SettingsGroup(R.string.settings_install_mode_section) {
			SegmentedButtonSetting(
				stringResource(R.string.install_mode),
				listOf(stringResource(R.string.install_mode_normal), stringResource(R.string.install_mode_root), stringResource(R.string.install_mode_shizuku)),
				{ viewModel.getInstallMode() },
				{ viewModel.setInstallMode(it) },
				R.drawable.ic_install,
				enabledItems = viewModel.installModeAvailable.collectAsStateWithLifecycle().value
			)
		}
	}
	item { Spacer(Modifier.height(24.dp)) }
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
	AppListSurface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.padding(16.dp),
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
			IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null, tint = ApkTheme.colors.textSecondary) }
			IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null, tint = ApkTheme.colors.error) }
		}
	}
}

@Composable
private fun CustomRepoDialog(
	repo: CustomGitRepo,
	onDismiss: () -> Unit,
	onSave: (CustomGitRepo) -> Unit
) {
	var user by remember(repo.id) { mutableStateOf(repo.user) }
	var project by remember(repo.id) { mutableStateOf(repo.repo) }
	var packageName by remember(repo.id) { mutableStateOf(repo.packageName) }
	var regex by remember(repo.id) { mutableStateOf(repo.extraRegex.orEmpty()) }
	var showError by remember { mutableStateOf(false) }
	fun handleUrlInput(value: String): Boolean {
		return parseRepoUrl(value)?.let { parsed ->
			user = parsed.user
			project = parsed.repo
			true
		} ?: false
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = {
				if (user.isBlank() || project.isBlank() || packageName.isBlank()) {
					showError = true
					return@TextButton
				}
				onSave(
					repo.copy(
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
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				OutlinedTextField(
					value = user,
					onValueChange = {
						if (!handleUrlInput(it)) {
							user = it
						}
						showError = false
					},
					label = { Text(stringResource(R.string.settings_custom_repo_user)) },
					singleLine = true
				)
				OutlinedTextField(
					value = project,
					onValueChange = {
						if (!handleUrlInput(it)) {
							project = it
						}
						showError = false
					},
					label = { Text(stringResource(R.string.settings_custom_repo_repo)) },
					singleLine = true
				)
				OutlinedTextField(
					value = packageName,
					onValueChange = { packageName = it; showError = false },
					label = { Text(stringResource(R.string.settings_custom_repo_package)) },
					singleLine = true
				)
				OutlinedTextField(
					value = regex,
					onValueChange = { regex = it },
					label = { Text(stringResource(R.string.settings_custom_repo_regex)) },
					singleLine = true
				)
				if (showError) {
					Text(
						text = stringResource(R.string.settings_custom_repo_error_required),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
				}
			}
		}
	)
}

@DrawableRes
private fun providerIcon(provider: GitProvider) = when (provider) {
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
			if (!notificationStatus.value) {
				Spacer(Modifier.height(12.dp))
				FilledTonalButton(onClick = {
					viewModel.requestNotificationPermission(launcher)
				}) {
					Text(stringResource(R.string.notifications_enable_action))
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
	title: String,
	onBack: (() -> Unit)? = null
) = AppTopBar(
	title = title,
	navigationIcon = {
		if (onBack != null) {
			IconButton(onClick = onBack) {
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
fun AboutTopBar(onBack: () -> Unit) = AppTopBar(
	title = stringResource(R.string.about),
	navigationIcon = {
		IconButton(onClick = onBack) {
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
	AppListSurface(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp)
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			androidx.compose.material3.Switch(
				checked = repo.isEnabled,
				onCheckedChange = onToggle,
				modifier = Modifier.padding(end = 12.dp)
			)
			Column(Modifier.weight(1f)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(repo.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
					if (repo.isDefault) {
						Spacer(Modifier.width(8.dp))
						Surface(
							color = ApkTheme.colors.surfaceHighlight,
							shape = ApkTheme.shapes.xs,
							border = BorderStroke(1.dp, ApkTheme.colors.accent.copy(alpha = 0.28f))
						) {
							Text(
								stringResource(R.string.settings_fdroid_repo_default),
								Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								style = MaterialTheme.typography.labelSmall,
								color = ApkTheme.colors.accent
							)
						}
					}
				}
				Text(repo.url, style = MaterialTheme.typography.bodySmall, color = ApkTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = ApkTheme.colors.textSecondary) }
			if (!repo.isDefault) {
				IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = ApkTheme.colors.error) }
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

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = {
				if (name.isBlank() || url.isBlank()) {
					showError = true
					return@TextButton
				}
				onSave(repo.copy(name = name, url = url))
			}) {
				Text(stringResource(R.string.settings_custom_repo_save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_custom_repo_cancel)) }
		},
		title = { Text(stringResource(if (repo.name.isBlank() && repo.url.isBlank()) R.string.settings_custom_repos_add else R.string.settings_custom_repos_edit)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it; showError = false },
					label = { Text(stringResource(R.string.settings_fdroid_repo_name)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = url,
					onValueChange = { url = it; showError = false },
					label = { Text(stringResource(R.string.settings_fdroid_repo_url)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				if (showError) {
					Text(
						text = stringResource(R.string.settings_fdroid_repo_error_required),
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
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
								Text(
									text = info.packageName,
									style = MaterialTheme.typography.bodyMedium,
									color = ApkTheme.colors.textSecondary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							IconButton(
								onClick = { viewModel.removeIgnoredUpdateInfo(info.packageName) }
							) {
								Icon(
									imageVector = Icons.Default.Close,
									contentDescription = null,
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
