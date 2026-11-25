package com.apkupdateross.ui.component

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.apkupdateross.ui.theme.ApkTheme

val LocalTvMode = staticCompositionLocalOf { false }

@Composable
fun TvModeProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = remember(configuration.uiMode, context) {
        isTelevisionUiMode(configuration.uiMode) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    CompositionLocalProvider(LocalTvMode provides isTv, content = content)
}

internal fun isTelevisionUiMode(uiMode: Int): Boolean =
    uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
fun Modifier.tvFocusOutline(
    shape: Shape = RoundedCornerShape(8.dp),
    enabled: Boolean = true,
    scale: Float = 1.025f
): Modifier = composed {
    if (!LocalTvMode.current || !enabled) return@composed this

    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        label = "tvFocusScale"
    )
    val focusBorder = if (focused) {
        Modifier.border(3.dp, ApkTheme.colors.accent, shape)
    } else {
        Modifier
    }

    this
        .onFocusChanged { focused = it.isFocused }
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .then(focusBorder)
}

@Composable
fun TvAwareIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialIconButton(
        onClick = onClick,
        modifier = modifier.tvFocusOutline(ApkTheme.shapes.md, enabled),
        enabled = enabled,
        content = content
    )
}

@Composable
fun TvAwareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    MaterialButton(
        onClick = onClick,
        modifier = modifier.tvFocusOutline(ApkTheme.shapes.sm, enabled),
        enabled = enabled,
        content = content
    )
}

@Composable
fun TvAwareTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    MaterialTextButton(
        onClick = onClick,
        modifier = modifier.tvFocusOutline(ApkTheme.shapes.sm, enabled),
        enabled = enabled,
        content = content
    )
}

@Composable
fun tvContentPadding(): PaddingValues = if (LocalTvMode.current) {
    PaddingValues(horizontal = 28.dp, vertical = 12.dp)
} else {
    PaddingValues(0.dp)
}
