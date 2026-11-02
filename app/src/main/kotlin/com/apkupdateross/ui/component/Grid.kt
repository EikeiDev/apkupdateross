package com.apkupdateross.ui.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import com.apkupdateross.R
import com.apkupdateross.ui.theme.ApkTheme

@Composable
fun LoadingGrid() {
    ShimmeringGrid()
}

@Composable
private fun ShimmeringGrid() = InstalledGrid(false) {
    items(16) {
        Box(
            Modifier
                .height(132.dp)
                .clip(ApkTheme.shapes.md)
                .shimmering(true)
        )
    }
}

@Composable
fun EmptyGrid(
    text: String = "",
    @DrawableRes icon: Int? = null,
    tone: AppTone = AppTone.Neutral,
    loading: Boolean = false
) = Box(Modifier.fillMaxSize()) {
    if (text.isNotEmpty()) {
        AppStateMessage(
            title = text,
            icon = icon,
            tone = tone,
            loading = loading,
            modifier = Modifier.align(Alignment.Center)
        )
    }
    LazyColumn(Modifier.fillMaxSize()) {}
}

@Composable
fun InstalledGrid(
    scroll: Boolean = true,
    compactMode: Boolean = false,
    portraitColumns: Int = 1,
    landscapeColumns: Int = 2,
    content: LazyGridScope.() -> Unit
) = LazyVerticalGrid(
    columns = if (LocalTvMode.current) {
        GridCells.Adaptive(if (compactMode) 240.dp else 360.dp)
    } else {
        GridCells.Fixed(getNumColumns(compactMode, portraitColumns, landscapeColumns))
    },
    contentPadding = if (LocalTvMode.current) {
        PaddingValues(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 28.dp)
    } else {
        PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp)
    },
    verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 14.dp),
    horizontalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 14.dp),
    content = content,
    userScrollEnabled = scroll,
    modifier = Modifier.fillMaxSize()
)

@Composable
fun getNumColumns(compactMode: Boolean, portraitColumns: Int, landscapeColumns: Int): Int {
    val configuration = LocalConfiguration.current
    val requested = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        if (compactMode) portraitColumns else 1
    } else {
        if (compactMode) landscapeColumns else 2
    }
    return accessibleColumnCount(
        requested = requested,
        screenWidthDp = configuration.screenWidthDp,
        fontScale = LocalDensity.current.fontScale
    )
}

internal fun accessibleColumnCount(
    requested: Int,
    screenWidthDp: Int,
    fontScale: Float
): Int {
    if (fontScale < 1.3f) return requested.coerceAtLeast(1)

    val minimumColumnWidth = when {
        fontScale >= 1.8f -> 200
        fontScale >= 1.5f -> 168
        else -> 144
    }
    val availableWidth = (screenWidthDp - 32).coerceAtLeast(1)
    val accessibleMaximum = (availableWidth / minimumColumnWidth).coerceAtLeast(1)
    return requested.coerceIn(1, accessibleMaximum)
}
