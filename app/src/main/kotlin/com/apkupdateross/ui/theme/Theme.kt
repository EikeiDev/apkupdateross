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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
	extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
	small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
	medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
	large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
	extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

private val DefaultTypography = Typography()
private val ApkTypography = Typography(
	displaySmall = DefaultTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
	headlineSmall = DefaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
	titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
	titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
	labelLarge = DefaultTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)


@Composable
fun AppTheme(
	darkTheme: Boolean,
	dynamicColor: Boolean = false,
	content: @Composable () -> Unit
) {
	val colorScheme = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val context = LocalContext.current
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}
		darkTheme -> ApkDarkColors
		else -> ApkLightColors
	}

	MaterialTheme(
		colorScheme = colorScheme,
		typography = ApkTypography,
		shapes = ApkShapes,
		content = content
	)
}

fun ColorScheme.statusBarColor() = surfaceColorAtElevation(2.dp)

fun isDarkTheme(theme: Int): Boolean {
	if (theme == 1) return true
	if (theme == 2) return false
	return isDark()
}
