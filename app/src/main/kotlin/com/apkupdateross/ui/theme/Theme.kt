package com.apkupdateross.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkupdateross.util.isDark


@Composable
fun AppTheme(
	darkTheme: Boolean,
	dynamicColor: Boolean = true,
	content: @Composable () -> Unit
) {
	val colorScheme = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val context = LocalContext.current
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}
		darkTheme -> darkColorScheme()
		else -> lightColorScheme()
	}

	MaterialTheme(colorScheme = colorScheme, content = content)
}

fun ColorScheme.statusBarColor() = surfaceColorAtElevation(3.dp)

fun isDarkTheme(theme: Int): Boolean {
	if (theme == 1) return true
	if (theme == 2) return false
	return isDark()
}
