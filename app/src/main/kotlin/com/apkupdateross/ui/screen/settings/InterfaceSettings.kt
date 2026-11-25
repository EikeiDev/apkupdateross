package com.apkupdateross.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdateross.R
import com.apkupdateross.data.ui.SwipeIgnoreDirection
import com.apkupdateross.ui.component.ButtonSetting
import com.apkupdateross.ui.component.SegmentedButtonSetting
import com.apkupdateross.ui.component.SliderSetting
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

@Composable
internal fun InterfaceSettings(viewModel: SettingsViewModel) {
	val context = LocalContext.current
	LazyColumn {
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
			} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
				SwitchSetting(
					{ viewModel.getUseDynamicColor() },
					{ viewModel.setUseDynamicColor(it) },
					stringResource(R.string.settings_dynamic_color),
					R.drawable.ic_theme
				)
			}
			Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
			SegmentedButtonSetting(
				stringResource(R.string.settings_language),
				listOf(
					stringResource(R.string.language_system),
					stringResource(R.string.language_english),
					stringResource(R.string.language_russian)
				),
				{ viewModel.getAppLanguageIndex() },
				{ viewModel.setAppLanguage(context, it) },
				R.drawable.ic_info
			)
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
