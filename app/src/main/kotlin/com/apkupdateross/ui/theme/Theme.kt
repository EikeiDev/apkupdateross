package com.apkupdateross.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkupdateross.util.isDark

private val ApkDarkColors = darkColorScheme(
	primary = Color(0xFF74D7B2),
	onPrimary = Color(0xFF003828),
	primaryContainer = Color(0xFF124D3B),
	onPrimaryContainer = Color(0xFFB4F1D9),
	secondary = Color(0xFFFFC857),
	onSecondary = Color(0xFF3F2E00),
	secondaryContainer = Color(0xFF5B4300),
	onSecondaryContainer = Color(0xFFFFE3A3),
	tertiary = Color(0xFF91CEF4),
	onTertiary = Color(0xFF00344B),
	tertiaryContainer = Color(0xFF114B65),
	onTertiaryContainer = Color(0xFFC4E8FF),
	background = Color(0xFF101312),
	onBackground = Color(0xFFE1E5E0),
	surface = Color(0xFF151917),
	onSurface = Color(0xFFE1E5E0),
	surfaceVariant = Color(0xFF222A26),
	onSurfaceVariant = Color(0xFFC0CAC2),
	outline = Color(0xFF88948B),
	outlineVariant = Color(0xFF3F4943),
	error = Color(0xFFFFB4AB),
	errorContainer = Color(0xFF93000A),
	onErrorContainer = Color(0xFFFFDAD6)
)

private val ApkLightColors = lightColorScheme(
	primary = Color(0xFF006C52),
	onPrimary = Color.White,
	primaryContainer = Color(0xFF96F1D1),
	onPrimaryContainer = Color(0xFF002117),
	secondary = Color(0xFF745B00),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFFFE08A),
	onSecondaryContainer = Color(0xFF241A00),
	tertiary = Color(0xFF176B87),
	onTertiary = Color.White,
	tertiaryContainer = Color(0xFFC0E9FF),
	onTertiaryContainer = Color(0xFF001F2B),
	background = Color(0xFFF7FAF6),
	onBackground = Color(0xFF181D1A),
	surface = Color(0xFFFFFFFF),
	onSurface = Color(0xFF181D1A),
	surfaceVariant = Color(0xFFE0E8E2),
	onSurfaceVariant = Color(0xFF414941),
	outline = Color(0xFF717A72),
	outlineVariant = Color(0xFFC0C8C1),
	error = Color(0xFFBA1A1A),
	errorContainer = Color(0xFFFFDAD6),
	onErrorContainer = Color(0xFF410002)
)

private val ApkShapes = Shapes(
	extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
	small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
	medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
	large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
	extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

private val DefaultTypography = Typography()
private val ApkTypography = Typography(
	displaySmall = DefaultTypography.displaySmall.copy(
		fontSize = 30.sp,
		lineHeight = 36.sp,
		fontWeight = FontWeight.Bold
	),
	headlineSmall = DefaultTypography.headlineSmall.copy(
		fontSize = 24.sp,
		lineHeight = 30.sp,
		fontWeight = FontWeight.SemiBold
	),
	titleLarge = DefaultTypography.titleLarge.copy(
		fontSize = 20.sp,
		lineHeight = 26.sp,
		fontWeight = FontWeight.SemiBold
	),
	titleMedium = DefaultTypography.titleMedium.copy(
		fontSize = 17.sp,
		lineHeight = 22.sp,
		fontWeight = FontWeight.SemiBold
	),
	bodyLarge = DefaultTypography.bodyLarge.copy(
		fontSize = 16.sp,
		lineHeight = 22.sp
	),
	bodyMedium = DefaultTypography.bodyMedium.copy(
		fontSize = 14.sp,
		lineHeight = 20.sp
	),
	labelLarge = DefaultTypography.labelLarge.copy(
		fontSize = 13.sp,
		lineHeight = 18.sp,
		fontWeight = FontWeight.SemiBold
	),
	labelSmall = DefaultTypography.labelSmall.copy(
		fontSize = 11.sp,
		lineHeight = 14.sp,
		fontWeight = FontWeight.Medium
	)
)

const val THEME_MODE_SYSTEM = 0
const val THEME_MODE_DARK = 1
const val THEME_MODE_LIGHT = 2
const val THEME_MODE_CUSTOM = 3

const val DEFAULT_CUSTOM_ACCENT = "#74D7B2"
const val DEFAULT_CUSTOM_BACKGROUND = "#101312"
const val DEFAULT_CUSTOM_SURFACE = "#151917"
const val DEFAULT_CUSTOM_NAVIGATION = "#15231D"

data class CustomThemeColors(
	val accentHex: String = DEFAULT_CUSTOM_ACCENT,
	val backgroundHex: String = DEFAULT_CUSTOM_BACKGROUND,
	val surfaceHex: String = DEFAULT_CUSTOM_SURFACE,
	val navigationHex: String = DEFAULT_CUSTOM_NAVIGATION
)

data class AppThemeState(
	val mode: Int,
	val darkTheme: Boolean,
	val customColors: CustomThemeColors = CustomThemeColors()
)

data class AppExtraColors(
	val navigationBar: Color? = null,
	val onNavigationBar: Color? = null,
	val navigationIndicator: Color? = null,
	val onNavigationIndicator: Color? = null,
	val selectedNavigationText: Color? = null
)

val LocalAppExtraColors = staticCompositionLocalOf { AppExtraColors() }

@Composable
fun AppTheme(
	theme: AppThemeState,
	dynamicColor: Boolean = false,
	content: @Composable () -> Unit
) {
	val colorScheme = when {
		theme.mode == THEME_MODE_CUSTOM -> customColorScheme(theme.customColors)
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val context = LocalContext.current
			if (theme.darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}
		theme.darkTheme -> ApkDarkColors
		else -> ApkLightColors
	}
	val extraColors = if (theme.mode == THEME_MODE_CUSTOM) {
		val navigationBar = colorFromHex(
			theme.customColors.navigationHex,
			colorScheme.surfaceColorAtElevation(6.dp)
		)
		val onNavigationBar = contentColorFor(navigationBar)
		val navigationIndicator = navigationIndicatorFor(
			colorFromHex(theme.customColors.accentHex, Color(0xFF74D7B2)),
			navigationBar
		)
		AppExtraColors(
			navigationBar = navigationBar,
			onNavigationBar = onNavigationBar,
			navigationIndicator = navigationIndicator,
			onNavigationIndicator = contentColorFor(navigationIndicator),
			selectedNavigationText = preferredReadableColor(colorScheme.primary, navigationBar, onNavigationBar)
		)
	} else {
		AppExtraColors()
	}

	CompositionLocalProvider(
		LocalAppExtraColors provides extraColors,
		LocalAppColors provides colorScheme.toAppColors(theme.darkTheme)
	) {
		MaterialTheme(
			colorScheme = colorScheme,
			typography = ApkTypography,
			shapes = ApkShapes,
			content = content
		)
	}
}

fun ColorScheme.statusBarColor() = surfaceColorAtElevation(2.dp)

fun isDarkTheme(theme: Int, customColors: CustomThemeColors = CustomThemeColors()): Boolean {
	if (theme == THEME_MODE_DARK) return true
	if (theme == THEME_MODE_LIGHT) return false
	if (theme == THEME_MODE_CUSTOM) {
		return colorFromHex(customColors.backgroundHex, Color(0xFF101312)).luminance() < 0.5f
	}
	return isDark()
}

fun normalizeHexColor(value: String): String? {
	val raw = value.trim().removePrefix("#")
	if (raw.length != 6 || raw.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
		return null
	}
	return "#${raw.uppercase()}"
}

fun colorFromHex(value: String, fallback: Color): Color {
	val normalized = normalizeHexColor(value) ?: return fallback
	val raw = normalized.removePrefix("#").toLong(16)
	return Color(0xFF000000 or raw)
}

private fun customColorScheme(colors: CustomThemeColors): ColorScheme {
	val accent = colorFromHex(colors.accentHex, Color(0xFF74D7B2))
	val background = colorFromHex(colors.backgroundHex, Color(0xFF101312))
	val surface = colorFromHex(colors.surfaceHex, Color(0xFF151917))
	val dark = background.luminance() < 0.5f
	val base = if (dark) ApkDarkColors else ApkLightColors
	val onBackground = contentColorFor(background)
	val onSurface = contentColorFor(surface)
	val primary = readableAccentOn(readableAccentOn(accent, background), surface)
	val primaryContainer = if (dark) lerp(accent, Color.Black, 0.55f) else lerp(accent, Color.White, 0.72f)
	val surfaceVariant = lerp(surface, onSurface, if (dark) 0.12f else 0.08f)

	return base.copy(
		primary = primary,
		onPrimary = contentColorFor(primary),
		primaryContainer = primaryContainer,
		onPrimaryContainer = contentColorFor(primaryContainer),
		background = background,
		onBackground = onBackground,
		surface = surface,
		onSurface = onSurface,
		surfaceVariant = surfaceVariant,
		onSurfaceVariant = lerp(onSurface, surface, 0.18f),
		outline = lerp(onSurface, surface, 0.45f),
		outlineVariant = lerp(onSurface, surface, 0.72f),
		surfaceTint = accent,
		surfaceDim = surface,
		surfaceBright = surfaceVariant,
		surfaceContainerLowest = surface,
		surfaceContainerLow = surface,
		surfaceContainer = surface,
		surfaceContainerHigh = surfaceVariant,
		surfaceContainerHighest = surfaceVariant
	)
}

private fun contentColorFor(background: Color): Color =
	if (background.luminance() > 0.45f) Color(0xFF111418) else Color.White

private fun preferredReadableColor(
	preferred: Color,
	background: Color,
	fallback: Color,
	minContrast: Float = 3f
): Color =
	if (contrastRatio(preferred, background) >= minContrast) preferred else fallback

private fun readableAccentOn(accent: Color, background: Color, minContrast: Float = 3f): Color {
	if (contrastRatio(accent, background) >= minContrast) return accent
	val target = if (background.luminance() > 0.5f) Color.Black else Color.White
	var amount = 0.12f
	while (amount <= 0.84f) {
		val candidate = lerp(accent, target, amount)
		if (contrastRatio(candidate, background) >= minContrast) return candidate
		amount += 0.12f
	}
	return target
}

private fun navigationIndicatorFor(accent: Color, navigationBar: Color): Color {
	val target = if (navigationBar.luminance() > 0.5f) Color.Black else Color.White
	val amount = if (contrastRatio(accent, navigationBar) >= 1.8f) 0.12f else 0.36f
	val candidate = lerp(accent, target, amount)
	return if (contrastRatio(candidate, navigationBar) >= 1.35f) candidate else target
}

private fun contrastRatio(first: Color, second: Color): Float {
	val firstLum = first.luminance()
	val secondLum = second.luminance()
	val lighter = maxOf(firstLum, secondLum)
	val darker = minOf(firstLum, secondLum)
	return (lighter + 0.05f) / (darker + 0.05f)
}
