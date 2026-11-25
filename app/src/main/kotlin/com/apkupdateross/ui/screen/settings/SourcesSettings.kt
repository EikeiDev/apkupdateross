package com.apkupdateross.ui.screen.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkupdateross.R
import com.apkupdateross.data.fdroid.FdroidRepo
import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.git.parseRepoUrl
import com.apkupdateross.repository.DetectedGitRepo
import com.apkupdateross.repository.GitRepoDetectionFailure
import com.apkupdateross.repository.GitRepoDetectionResult
import com.apkupdateross.ui.component.AppListSurface
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.component.SwitchSetting
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
import com.apkupdateross.ui.component.tvFocusOutline
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun SourcesSettings(
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
internal fun CustomRepoDialog(
	repo: CustomGitRepo,
	viewModel: SettingsViewModel,
	initialUrlOverride: String? = null,
	onDismiss: () -> Unit,
	onSave: (CustomGitRepo) -> Unit
) {
	val initialUrl = remember(repo.id, initialUrlOverride) {
		initialUrlOverride?.takeIf { it.isNotBlank() } ?: repo.webUrl()
	}
	val isExisting = remember(repo.id) {
		repo.user.isNotBlank() && repo.repo.isNotBlank() && repo.packageName.isNotBlank()
	}
	var url by remember(repo.id, initialUrl) { mutableStateOf(initialUrl) }
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
		if (normalizedUrl != initialUrl || initialUrlOverride != null) {
			if (!isExisting) {
				packageName = ""
				regex = ""
			}
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
				}
			) {
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
				Switch(
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
internal fun FdroidRepoDialog(
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
