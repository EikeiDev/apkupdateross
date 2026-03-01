package com.apkupdateross.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
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
import com.apkupdateross.data.ui.SettingsUiState
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.LargeTitle
import com.apkupdateross.ui.component.LoadingImageApp
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SliderSetting
import com.apkupdateross.ui.component.SourceIcon
import com.apkupdateross.ui.component.SwitchSetting
import com.apkupdateross.ui.theme.statusBarColor
import com.apkupdateross.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar


@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) = Column {
	if (viewModel.state.collectAsStateWithLifecycle().value == SettingsUiState.Settings) {
		SettingsTopBar(viewModel)
		Settings(viewModel)
	} else {
		AboutTopBar(viewModel)
		About()
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
fun Settings(viewModel: SettingsViewModel) = LazyColumn {
	item {
		LargeTitle(stringResource(R.string.settings_ui), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			{ viewModel.getPlayTextAnimations() },
			{ viewModel.setPlayTextAnimations(it) },
			stringResource(R.string.play_text_animations),
			R.drawable.ic_animation
		)
		SegmentedButtonSetting(
			stringResource(R.string.theme),
			listOf(
				stringResource(R.string.theme_system),
				stringResource(R.string.theme_dark),
				stringResource(R.string.theme_light)
			),
			{ viewModel.getTheme() },
			{ viewModel.setTheme(it) },
			R.drawable.ic_theme
		)
	}

	item {
		LargeTitle(stringResource(R.string.settings_sources), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			{ viewModel.getUseGitHub() },
			{ viewModel.setUseGitHub(it) },
			stringResource(R.string.source_github),
			R.drawable.ic_github
		)
		SwitchSetting(
			{ viewModel.getUseGitLab() },
			{ viewModel.setUseGitLab(it) },
			stringResource(R.string.source_gitlab),
			R.drawable.ic_gitlab
		)
		SwitchSetting(
			{ viewModel.getUseApkMirror() },
			{ viewModel.setUseApkMirror(it) },
			stringResource(R.string.source_apkmirror),
			R.drawable.ic_apkmirror
		)
		SwitchSetting(
			{ viewModel.getUseFdroid() },
			{ viewModel.setUseFdroid(it) },
			stringResource(R.string.source_fdroid),
			R.drawable.ic_fdroid
		)
		SwitchSetting(
			{ viewModel.getUseIzzy() },
			{ viewModel.setUseIzzy(it) },
			stringResource(R.string.source_izzy),
			R.drawable.ic_izzy
		)
		SwitchSetting(
			{ viewModel.getUseAptoide() },
			{ viewModel.setUseAptoide(it) },
			stringResource(R.string.source_aptoide),
			R.drawable.ic_aptoide
		)
		SwitchSetting(
			{ viewModel.getUseApkPure() },
			{ viewModel.setUseApkPure(it) },
			stringResource(R.string.source_apkpure),
			R.drawable.ic_apkpure
		)
		SwitchSetting(
			{ viewModel.getUsePlay() },
			{ viewModel.setUsePlay(it) },
			stringResource(R.string.source_play) + " (Alpha)",
			R.drawable.ic_play
		)
		SwitchSetting(
			{ viewModel.getUseRuStore() },
			{ viewModel.setUseRuStore(it) },
			stringResource(R.string.source_rustore),
			R.drawable.ic_rustore
		)
	}

	item {
		LargeTitle(stringResource(R.string.settings_options), Modifier.padding(start = 16.dp, top = 16.dp))
		SegmentedButtonSetting(
			stringResource(R.string.install_mode),
			listOf(
				stringResource(R.string.install_mode_normal),
				stringResource(R.string.install_mode_root),
				stringResource(R.string.install_mode_shizuku)
			),
			{ viewModel.getInstallMode() },
			{ viewModel.setInstallMode(it) },
			R.drawable.ic_root,
			viewModel.installModeAvailable.collectAsStateWithLifecycle().value
		)
		SwitchSetting(
			{ viewModel.getIgnoreAlpha() },
			{ viewModel.setIgnoreAlpha(it) },
			stringResource(R.string.ignore_alpha),
			R.drawable.ic_alpha
		)
		SwitchSetting(
			{ viewModel.getIgnoreBeta() },
			{ viewModel.setIgnoreBeta(it) },
			stringResource(R.string.ignore_beta),
			R.drawable.ic_beta
		)
		SwitchSetting(
			{ viewModel.getIgnorePreRelease() },
			{ viewModel.setIgnorePreRelease(it) },
			stringResource(R.string.ignore_preRelease),
			R.drawable.ic_pre_release
		)
		SwitchSetting(
			{ viewModel.getUseSafeStores() },
			{ viewModel.setUseSafeStores(it) },
			stringResource(R.string.use_safe_stores),
			R.drawable.ic_safe
		)
	}

	item {
		val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
		LargeTitle(stringResource(R.string.settings_alarm), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			getValue = { viewModel.getEnableAlarm() },
			setValue = { viewModel.setEnableAlarm(it, launcher) },
			text = stringResource(R.string.settings_alarm),
			icon = R.drawable.ic_alarm
		)
		SliderSetting(
			{ viewModel.getAlarmHour().toFloat() },
			{ viewModel.setAlarmHour(it.toInt()) },
			stringResource(R.string.settings_hour),
			0f..23f,
			22,
			R.drawable.ic_hour
		)
		SegmentedButtonSetting(
			stringResource(R.string.frequency),
			listOf(
				stringResource(R.string.settings_alarm_daily),
				stringResource(R.string.settings_alarm_3day),
				stringResource(R.string.settings_alarm_weekly)
			),
			{ viewModel.getAlarmFrequency() },
			{ viewModel.setAlarmFrequency(it) },
			R.drawable.ic_frequency
		)
	}
	item {
		LargeTitle(stringResource(R.string.settings_utils), Modifier.padding(start = 16.dp, top = 16.dp))
		ButtonSetting(
			stringResource(R.string.copy_app_list),
			{ viewModel.copyAppList() },
			R.drawable.ic_root,
			R.drawable.ic_copy
		)
		ButtonSetting(
			stringResource(R.string.copy_app_logs),
			{ viewModel.copyAppLogs() },
			R.drawable.ic_root,
			R.drawable.ic_copy
		)
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
