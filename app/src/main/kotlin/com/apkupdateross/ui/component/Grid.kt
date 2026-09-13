package com.apkupdateross.ui.component

import android.content.res.Configuration
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
    text: String = ""
) = Box(Modifier.fillMaxSize()) {
    if (text.isNotEmpty()) {
        AppStateMessage(
            title = text,
            icon = R.drawable.ic_empty,
            tone = AppTone.Neutral,
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
    columns = GridCells.Fixed(getNumColumns(compactMode, portraitColumns, landscapeColumns)),
    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 14.dp),
    horizontalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 14.dp),
    content = content,
    userScrollEnabled = scroll,
    modifier = Modifier.fillMaxSize()
)

@Composable
fun getNumColumns(compactMode: Boolean, portraitColumns: Int, landscapeColumns: Int): Int {
    return if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        if (compactMode) portraitColumns else 1
    } else {
        if (compactMode) landscapeColumns else 2
    }
}
