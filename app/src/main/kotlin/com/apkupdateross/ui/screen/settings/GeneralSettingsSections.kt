package com.apkupdateross.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdateross.R
import com.apkupdateross.ui.component.AppListSurface
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.LargeTitle
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.component.SliderSetting
import com.apkupdateross.ui.component.SwitchSetting
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
import com.apkupdateross.ui.component.tvFocusOutline
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.util.formatSize
import com.apkupdateross.viewmodel.SettingsViewModel
import com.apkupdateross.viewmodel.UpdateMetrics
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun UpdatesSettings(
	viewModel: SettingsViewModel,
	updateMetrics: UpdateMetrics,
	onManageIgnoredUpdates: () -> Unit,
	onOpenSourcePriority: () -> Unit
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
			SwitchSetting(
				{ viewModel.getAutoCheckOnStart() },
				{ viewModel.setAutoCheckOnStart(it) },
				stringResource(R.string.settings_auto_check_on_start),
				R.drawable.ic_refresh
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			ButtonSetting(stringResource(R.string.settings_source_priority), onOpenSourcePriority, R.drawable.ic_appstore)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
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
internal fun NotificationsSettings(
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

@Composable
internal fun InstallSettings(
	viewModel: SettingsViewModel,
	onInstallLocalPackage: () -> Unit
) {
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
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				ButtonSetting(
					stringResource(R.string.install_local_package),
					onInstallLocalPackage,
					R.drawable.ic_install
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
internal fun NetworkSettings(viewModel: SettingsViewModel) {
	var proxyEnabled by remember { mutableStateOf(viewModel.getProxyEnabled()) }
	var proxyHost by remember { mutableStateOf(viewModel.getProxyHost()) }
	var proxyPort by remember { mutableStateOf(viewModel.getProxyPort().toString()) }
	var proxyUser by remember { mutableStateOf(viewModel.getProxyUsername()) }
	var proxyPassword by remember { mutableStateOf(viewModel.getProxyPassword()) }
	val isTestingProxy by viewModel.isTestingProxy.collectAsStateWithLifecycle()

	val fieldColors = OutlinedTextFieldDefaults.colors(
		focusedBorderColor = ApkTheme.colors.accent.copy(alpha = 0.72f),
		unfocusedBorderColor = ApkTheme.colors.divider,
		focusedContainerColor = ApkTheme.colors.surfaceSecondary,
		unfocusedContainerColor = ApkTheme.colors.surfaceSecondary,
		focusedTextColor = ApkTheme.colors.textPrimary,
		unfocusedTextColor = ApkTheme.colors.textPrimary,
		focusedLabelColor = ApkTheme.colors.accent,
		unfocusedLabelColor = ApkTheme.colors.textSecondary,
		cursorColor = ApkTheme.colors.accent
	)

	LazyColumn {
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
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				SwitchSetting(
					getValue = { viewModel.getBackgroundCheckWifiOnly() },
					setValue = { viewModel.setBackgroundCheckWifiOnly(it) },
					text = stringResource(R.string.settings_background_wifi_only),
					icon = R.drawable.ic_system
				)
			}
		}
		item {
			SettingsGroup(R.string.settings_proxy) {
				SwitchSetting(
					getValue = { proxyEnabled },
					setValue = {
						proxyEnabled = it
						viewModel.setProxyEnabled(it)
					},
					text = stringResource(R.string.settings_proxy_enable),
					icon = R.drawable.ic_system
				)
				if (proxyEnabled) {
					Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
					SegmentedButtonSetting(
						text = stringResource(R.string.settings_proxy_type),
						options = listOf("HTTP", "SOCKS5"),
						getValue = { viewModel.getProxyType() },
						setValue = { viewModel.setProxyType(it) },
						icon = R.drawable.ic_system
					)
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 10.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp)
					) {
						OutlinedTextField(
							value = proxyHost,
							onValueChange = {
								proxyHost = it
								viewModel.setProxyHost(it)
							},
							label = { Text(stringResource(R.string.settings_proxy_host)) },
							singleLine = true,
							modifier = Modifier.fillMaxWidth(),
							shape = ApkTheme.shapes.sm,
							colors = fieldColors
						)
						OutlinedTextField(
							value = proxyPort,
							onValueChange = { value ->
								val digits = value.filter { it.isDigit() }.take(5)
								proxyPort = digits
								viewModel.setProxyPort(digits)
							},
							label = { Text(stringResource(R.string.settings_proxy_port)) },
							singleLine = true,
							modifier = Modifier.fillMaxWidth(),
							shape = ApkTheme.shapes.sm,
							colors = fieldColors
						)
						OutlinedTextField(
							value = proxyUser,
							onValueChange = {
								proxyUser = it
								viewModel.setProxyUsername(it)
							},
							label = { Text(stringResource(R.string.settings_proxy_username)) },
							singleLine = true,
							modifier = Modifier.fillMaxWidth(),
							shape = ApkTheme.shapes.sm,
							colors = fieldColors
						)
						OutlinedTextField(
							value = proxyPassword,
							onValueChange = {
								proxyPassword = it
								viewModel.setProxyPassword(it)
							},
							label = { Text(stringResource(R.string.settings_proxy_password)) },
							singleLine = true,
							visualTransformation = PasswordVisualTransformation(),
							modifier = Modifier.fillMaxWidth(),
							shape = ApkTheme.shapes.sm,
							colors = fieldColors
						)
					}
					Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
					ButtonSetting(
						text = stringResource(if (isTestingProxy) R.string.proxy_testing else R.string.settings_proxy_test),
						onClick = { viewModel.testProxyConnection() },
						icon = R.drawable.ic_refresh
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp)) }
	}
}

@Composable
internal fun DataLogsSettings(
	viewModel: SettingsViewModel,
	downloadFolderLabel: String,
	hasCustomDownloadFolder: Boolean,
	installHistoryCount: Int,
	downloadCacheSizeBytes: Long,
	storeNotFoundCacheCount: Int,
	onOpenInstallHistory: () -> Unit,
	onChooseDownloadFolder: () -> Unit,
	onResetDownloadFolder: () -> Unit,
	onExport: () -> Unit,
	onImport: () -> Unit
) {
	val context = LocalContext.current
	LazyColumn {
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
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			ButtonSetting(
				stringResource(R.string.clear_download_cache, downloadCacheSizeBytes.formatSize(context)),
				{ viewModel.clearDownloadCache() },
				R.drawable.ic_delete
			)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			ButtonSetting(
				stringResource(R.string.clear_store_cache, storeNotFoundCacheCount),
				{ viewModel.clearStoreNotFoundCache() },
				R.drawable.ic_delete
			)
		}
	}
	item {
		SettingsGroup(R.string.settings_data_logs_actions) {
			val historyLabel = if (installHistoryCount > 0) {
				"${stringResource(R.string.install_history_title)} ($installHistoryCount)"
			} else {
				stringResource(R.string.install_history_title)
			}
			ButtonSetting(historyLabel, onOpenInstallHistory, R.drawable.ic_hour)
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
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
}

private fun formatDuration(durationMs: Long): String {
	val seconds = durationMs / 1000.0
	return String.format(Locale.ROOT, "%.1f s", seconds)
}

private fun formatTimestamp(timestamp: Long): String =
	DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
