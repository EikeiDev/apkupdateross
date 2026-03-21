package com.apkupdateross.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
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
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.LargeTitle
import com.apkupdateross.ui.component.LoadingImageApp
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SliderSetting
import com.apkupdateross.ui.component.SourceIcon
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.component.SwitchSetting
import com.apkupdateross.ui.theme.statusBarColor
import com.apkupdateross.viewmodel.SettingsViewModel
import com.apkupdateross.viewmodel.UpdateMetrics
import java.text.DateFormat
import java.util.Date
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar


@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) = Column {
	val uiState = viewModel.state.collectAsStateWithLifecycle().value
	val ruStoreCacheCount = viewModel.ruStore404Count.collectAsStateWithLifecycle().value
	val updateMetrics = viewModel.updateMetrics.collectAsStateWithLifecycle().value
	val customRepos = viewModel.customGitRepos.collectAsStateWithLifecycle().value
	val fdroidRepos = viewModel.fdroidRepos.collectAsStateWithLifecycle().value
	var dialogRepo by remember { mutableStateOf<CustomGitRepo?>(null) }
	var dialogFdroidRepo by remember { mutableStateOf<FdroidRepo?>(null) }
	val alarmPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
	if (uiState == SettingsUiState.Settings) {
		DisposableEffect(Unit) {
			viewModel.startMetricsAutoRefresh()
			onDispose { viewModel.stopMetricsAutoRefresh() }
		}
		SettingsTopBar(viewModel)
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
			notificationPermissionLauncher = alarmPermissionLauncher
		)
	} else {
		AboutTopBar(viewModel)
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
}

@Composable
fun About() = Box(
	Modifier.fillMaxSize()
) {
	ElevatedCard(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp, vertical = 32.dp)
			.align(Alignment.TopCenter),
		shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
	) {
		Column(
			Modifier.fillMaxWidth().padding(32.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			LoadingImageApp(BuildConfig.APPLICATION_ID, Modifier.size(100.dp))
			Spacer(Modifier.height(24.dp))
			Text(
				text = stringResource(R.string.app_name),
				style = MaterialTheme.typography.headlineLarge,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary
			)
			Spacer(Modifier.height(8.dp))
			Surface(
				color = MaterialTheme.colorScheme.secondaryContainer,
				shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
			) {
				Text(
					"${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
					Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
					color = MaterialTheme.colorScheme.onSecondaryContainer,
					style = MaterialTheme.typography.bodyMedium
				)
			}
			Spacer(Modifier.height(32.dp))
			Text(
				"Copyright © ${Calendar.getInstance().get(Calendar.YEAR)}",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				style = MaterialTheme.typography.bodyMedium
			)
			Text(
				"rumboalla, NotDev",
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				style = MaterialTheme.typography.bodyMedium
			)
		}
	}
}

@Composable
fun Settings(
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
	notificationPermissionLauncher: ActivityResultLauncher<String>
) = LazyColumn {
	item {
		LargeTitle(stringResource(R.string.settings_ui), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		ElevatedCard(
			shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
		) {
			Column {
				SegmentedButtonSetting(
					stringResource(R.string.theme),
					listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_dark), stringResource(R.string.theme_light)),
					{ viewModel.getTheme() },
					{ viewModel.setTheme(it) },
					R.drawable.ic_theme
				)
				SwitchSetting(
					{ viewModel.getPlayTextAnimations() },
					{ viewModel.setPlayTextAnimations(it) },
					stringResource(R.string.play_text_animations),
					R.drawable.ic_animation
				)
				SwitchSetting(
					{ viewModel.getUseCompactView() },
					{ viewModel.setUseCompactView(it) },
					stringResource(R.string.settings_compact_view),
					R.drawable.ic_visible
				)
			}
		}
	}

	item {
		LargeTitle(stringResource(R.string.settings_sources), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		ElevatedCard(
			shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
		) {
			Column {
				var githubExpanded by remember { mutableStateOf(false) }
				var githubToken by rememberSaveable { mutableStateOf(viewModel.getGithubToken()) }
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
							visualTransformation = PasswordVisualTransformation()
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
							visualTransformation = PasswordVisualTransformation()
						)
						CustomGitReposSection(
							repos = customRepos.filter { it.platform == GitProvider.GITLAB },
							onAdd = { onAddRepo(GitProvider.GITLAB) },
							onEdit = onEditRepo,
							onDelete = onDeleteRepo
						)
					}
				}

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
				SwitchSetting({ viewModel.getUsePlay() }, { viewModel.setUsePlay(it) }, stringResource(R.string.source_play), R.drawable.ic_play)
			}
		}
	}

	item {
		LargeTitle(stringResource(R.string.settings_options), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		ElevatedCard(
			shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
		) {
			Column {
				SegmentedButtonSetting(
					stringResource(R.string.install_mode),
					listOf(stringResource(R.string.install_mode_normal), stringResource(R.string.install_mode_root), stringResource(R.string.install_mode_shizuku)),
					{ viewModel.getInstallMode() },
					{ viewModel.setInstallMode(it) },
					R.drawable.ic_install,
					enabledItems = viewModel.installModeAvailable.collectAsStateWithLifecycle().value
				)
				
				Divider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

				SwitchSetting({ viewModel.getIgnoreAlpha() }, { viewModel.setIgnoreAlpha(it) }, stringResource(R.string.ignore_alpha), R.drawable.ic_alpha)
				SwitchSetting({ viewModel.getIgnoreBeta() }, { viewModel.setIgnoreBeta(it) }, stringResource(R.string.ignore_beta), R.drawable.ic_beta)
				SwitchSetting({ viewModel.getIgnorePreRelease() }, { viewModel.setIgnorePreRelease(it) }, stringResource(R.string.ignore_preRelease), R.drawable.ic_pre_release)
			}
		}
	}

	item {
		LargeTitle(stringResource(R.string.settings_alarm), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		ElevatedCard(
			shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
		) {
			Column {
				SwitchSetting(
					getValue = { viewModel.getEnableAlarm() },
					setValue = { viewModel.setEnableAlarm(it, notificationPermissionLauncher) },
					text = stringResource(R.string.settings_alarm),
					icon = R.drawable.ic_alarm
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
	}
	item {
		LargeTitle(stringResource(R.string.settings_utils), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		ElevatedCard(
			shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
		) {
			Column {
				ButtonSetting(stringResource(R.string.copy_app_list), { viewModel.copyAppList() }, R.drawable.ic_root)
				ButtonSetting(stringResource(R.string.copy_app_logs), { viewModel.copyAppLogs() }, R.drawable.ic_root)
			}
		}
	}
	item {
		LargeTitle(stringResource(R.string.settings_notifications), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		NotificationStatusCard(viewModel)
	}
	item {
		LargeTitle(stringResource(R.string.settings_metrics), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
		ElevatedCard(
			shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
		) {
			Column(Modifier.padding(vertical = 8.dp)) {
				MetricRow(
					text = stringResource(R.string.metric_last_check_duration),
					value = updateMetrics.durationMs?.let { formatDuration(it) } ?: stringResource(R.string.metric_no_data),
					icon = R.drawable.ic_hour
				)
				MetricRow(
					text = stringResource(R.string.metric_last_check_time),
					value = updateMetrics.timestamp?.let { formatTimestamp(it) } ?: stringResource(R.string.metric_no_data),
					icon = R.drawable.ic_info
				)
				MetricRow(
					text = stringResource(R.string.metric_last_check_sources),
					value = updateMetrics.sources?.toString() ?: stringResource(R.string.metric_no_data),
					icon = R.drawable.ic_safe
				)
			}
		}
		Spacer(Modifier.height(24.dp))
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
		Text(stringResource(R.string.settings_custom_repos), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
		IconButton(onClick = onAdd) {
			Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_custom_repos_add))
		}
	}
	if (repos.isEmpty()) {
		Text(
			text = stringResource(R.string.settings_custom_repo_empty),
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
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
	ElevatedCard(
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
				Text("${repo.user}/${repo.repo}", style = MaterialTheme.typography.titleMedium)
				Text(repo.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				repo.extraRegex?.takeIf { it.isNotBlank() }?.let {
					Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
			IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null) }
			IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
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
		title = { Text(stringResource(R.string.settings_custom_repos_add)) },
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
	return String.format("%.1f s", seconds)
}

private fun formatTimestamp(timestamp: Long): String =
	DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun MetricRow(text: String, value: String, @DrawableRes icon: Int) = ElevatedCard(
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
			Text(text, style = MaterialTheme.typography.bodyLarge)
			Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}

@Composable
private fun NotificationStatusCard(viewModel: SettingsViewModel) {
	val notificationStatus = remember { mutableStateOf(viewModel.areNotificationsEnabled()) }
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
		notificationStatus.value = viewModel.areNotificationsEnabled()
	}
	ElevatedCard(
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
					style = MaterialTheme.typography.bodyLarge
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
fun SettingsTopBar(viewModel: SettingsViewModel) = TopAppBar(
	title = { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleLarge) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	actions = {
		IconButton(onClick = { viewModel.setAbout() }) {
			Icon(painterResource(R.drawable.ic_info), stringResource(R.string.about))
		}
	}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutTopBar(viewModel: SettingsViewModel) = TopAppBar(
	title = { Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	actions = {
		IconButton(onClick = { viewModel.setSettings() }) {
			Icon(Icons.Default.Settings, stringResource(R.string.tab_settings))
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
		Text("F-Droid Repositories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
		IconButton(onClick = onAdd) {
			Icon(Icons.Filled.Add, contentDescription = "Add Repository")
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
	ElevatedCard(
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
							color = MaterialTheme.colorScheme.secondaryContainer,
							shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
						) {
							Text(
								"Default",
								Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSecondaryContainer
							)
						}
					}
				}
				Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
			if (!repo.isDefault) {
				IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp)) }
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
				Text("Save")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Cancel") }
		},
		title = { Text(if (repo.name.isEmpty()) "Add Repository" else "Edit Repository") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it; showError = false },
					label = { Text("Name") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = url,
					onValueChange = { url = it; showError = false },
					label = { Text("URL") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				if (showError) {
					Text(
						text = "Name and URL are required",
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall
					)
				}
			}
		}
	)
}

