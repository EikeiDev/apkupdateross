package com.apkupdateross.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

@Immutable
data class AppColors(
    val background: Color,
    val textOnBackground: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val divider: Color,
    val disabled: Color
)

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object AppShapeTokens {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)
}

object ApkTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val spacing: AppSpacing
        get() = AppSpacing

    val shapes: AppShapeTokens
        get() = AppShapeTokens
}

internal val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        background = Color(0xFF101514),
        textOnBackground = Color(0xFFF5F8F5),
        surface = Color(0xFF17201E),
        surfaceSecondary = Color(0xFF1D2825),
        surfaceElevated = Color(0xFF202A27),
        surfaceHighlight = Color(0xFF14382F),
        textPrimary = Color(0xFFF5F8F5),
        textSecondary = Color(0xFFC8D0CB),
        textTertiary = Color(0xFF93A09A),
        accent = Color(0xFF74D7B2),
        onAccent = Color(0xFF062019),
        success = Color(0xFF74D7B2),
        warning = Color(0xFFFFC857),
        error = Color(0xFFFF6B57),
        divider = Color(0xFF31403B),
        disabled = Color(0xFF77817D)
    )
}

internal fun ColorScheme.toAppColors(darkTheme: Boolean): AppColors {
    val surfaceLayer = if (darkTheme) {
        lerp(surface, onSurface, 0.05f)
    } else {
        lerp(surface, primary, 0.025f)
    }
    val secondaryLayer = if (darkTheme) {
        lerp(surface, primary, 0.09f)
    } else {
        lerp(surface, primary, 0.045f)
    }
    val elevatedLayer = if (darkTheme) {
        lerp(surface, onSurface, 0.08f)
    } else {
        lerp(surface, primary, 0.035f)
    }
    val highlightLayer = if (darkTheme) {
        lerp(primary, surface, 0.72f)
    } else {
        lerp(primary, surface, 0.86f)
    }

    return AppColors(
        background = background,
        textOnBackground = onBackground,
        surface = surfaceLayer,
        surfaceSecondary = secondaryLayer,
        surfaceElevated = elevatedLayer,
        surfaceHighlight = highlightLayer,
        textPrimary = onSurface,
        textSecondary = onSurfaceVariant,
        textTertiary = onSurfaceVariant.copy(alpha = 0.72f),
        accent = primary,
        onAccent = onPrimary,
        success = if (darkTheme) Color(0xFF74D7B2) else Color(0xFF167A58),
        warning = if (darkTheme) Color(0xFFFFC857) else Color(0xFF8A5D00),
        error = if (darkTheme) Color(0xFFFF6B57) else Color(0xFFB3261E),
        divider = outlineVariant.copy(alpha = if (darkTheme) 0.34f else 0.58f),
        disabled = onSurface.copy(alpha = 0.38f)
    )
}
