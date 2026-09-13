package com.apkupdateross.ui.component

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.apkupdateross.R
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.Source
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.util.getAppName
import com.apkupdateross.util.to2f
import com.apkupdateross.util.toAnnotatedString

private val ActionButtonSize = 48.dp
private val ActionIconSize = 20.dp
private val ActionProgressSize = 20.dp

@Composable
fun CommonItem(
    packageName: String,
    name: String,
    version: String,
    oldVersion: String?,
    versionCode: Long,
    oldVersionCode: Long?,
    uri: Uri? = null,
    single: Boolean = false,
    source: Source? = null,
    compactMode: Boolean = false,
    releaseType: ReleaseType? = null,
    showVersionInfo: Boolean = true
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    val iconSlotWidth = when {
        releaseType == null && compactMode -> 52.dp
        releaseType == null -> 76.dp
        compactMode -> 72.dp
        else -> 82.dp
    }
    val iconSize = if (compactMode) 44.dp else 64.dp

    Column(
        modifier = Modifier
            .width(iconSlotWidth)
            .padding(end = if (compactMode) 8.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp)
    ) {
        Box(Modifier.size(iconSize)) {
            if (uri == null) {
                LoadingImageApp(packageName, Modifier.fillMaxSize())
            } else {
                LoadingImage(uri, Modifier.fillMaxSize())
            }
        }
        releaseType?.let { ReleaseTypeChip(it, compactMode) }
    }
    Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
        LargeTitle(name.ifEmpty { LocalContext.current.getAppName(packageName) }.ifEmpty { packageName })
        MediumText(packageName)

        if (showVersionInfo) {
            VersionInfo(
                version = version,
                oldVersion = oldVersion,
                versionCode = versionCode,
                oldVersionCode = oldVersionCode,
                single = single,
                source = source
            )
        }
    }
}

@Composable
private fun VersionInfo(
    version: String,
    oldVersion: String?,
    versionCode: Long,
    oldVersionCode: Long?,
    single: Boolean,
    source: Source? = null,
    modifier: Modifier = Modifier
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    val previousVersion = oldVersion?.takeIf { it.isNotBlank() }
    if (previousVersion != null && !single) {
        VersionCompareRow(previousVersion, version.ifBlank { "?" })
    } else if (version.isNotBlank()) {
        VersionLine(version)
    }

    if (oldVersionCode != null && !single && versionCode > 0L) {
        VersionCompareRow(oldVersionCode.toString(), versionCode.toString())
    } else if (versionCode > 0L) {
        VersionLine(versionCode.toString())
    }
}

@Composable
private fun VersionCompareRow(
    oldValue: String,
    newValue: String
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    VersionValueChip(
        text = oldValue,
        highlighted = false,
        modifier = Modifier.weight(1f)
    )
    Text(
        text = "->",
        style = MaterialTheme.typography.bodyMedium,
        color = ApkTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = 4.dp)
    )
    VersionValueChip(
        text = newValue,
        highlighted = true,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun VersionLine(text: String, modifier: Modifier = Modifier) = ScrollableText(modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = ApkTheme.colors.textSecondary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun VersionValueChip(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (highlighted) {
        ApkTheme.colors.accent.copy(alpha = 0.76f)
    } else {
        ApkTheme.colors.divider
    }
    val contentColor = if (highlighted) {
        ApkTheme.colors.accent
    } else {
        ApkTheme.colors.textSecondary
    }
    val containerColor = if (highlighted) {
        ApkTheme.colors.surfaceHighlight
    } else {
        ApkTheme.colors.surfaceSecondary
    }

    Surface(
        modifier = modifier,
        shape = ApkTheme.shapes.xs,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        ScrollableText(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun ReleaseTypeChip(type: ReleaseType, compactMode: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val content = when (type) {
        ReleaseType.Stable -> ApkTheme.colors.success
        ReleaseType.Beta -> if (dark) Color(0xFF7CC7FF) else Color(0xFF0B65B9)
        ReleaseType.Alpha -> ApkTheme.colors.warning
        ReleaseType.PreRelease -> ApkTheme.colors.error
    }

    Surface(
        shape = ApkTheme.shapes.xs,
        color = content.copy(alpha = if (dark) 0.2f else 0.13f),
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.35f))
    ) {
        Text(
            text = stringResource(type.labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (compactMode) 5.dp else 7.dp,
                vertical = if (compactMode) 2.dp else 3.dp
            )
        )
    }
}

@Composable
private fun RoundActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) = Surface(
    modifier = modifier.size(ActionButtonSize),
    shape = ApkTheme.shapes.md,
    color = if (enabled) ApkTheme.colors.surfaceSecondary else ApkTheme.colors.surfaceSecondary.copy(alpha = 0.45f),
    contentColor = if (enabled) ApkTheme.colors.textPrimary else ApkTheme.colors.disabled,
    border = BorderStroke(1.dp, ApkTheme.colors.divider)
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun InstallButton(
    app: AppUpdate,
    onInstall: (String) -> Unit,
    onCancel: (AppUpdate) -> Unit = {}
) = Surface(
    modifier = Modifier.size(ActionButtonSize),
    shape = ApkTheme.shapes.md,
    color = if (!app.isDownloading && (app.link !is Link.Empty || app.isInstalling)) {
        ApkTheme.colors.accent
    } else {
        ApkTheme.colors.surfaceSecondary
    },
    contentColor = if (!app.isDownloading && (app.link !is Link.Empty || app.isInstalling)) {
        ApkTheme.colors.onAccent
    } else {
        ApkTheme.colors.disabled
    }
) {
    val enabled = !app.isDownloading && (app.link !is Link.Empty || app.isInstalling)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = enabled) {
                if (app.isInstalling) onCancel(app) else onInstall(app.packageName)
            },
        contentAlignment = Alignment.Center
    ) {
        if (app.isInstalling) {
            CircularProgressIndicator(
                Modifier.size(ActionProgressSize),
                color = ApkTheme.colors.onAccent,
                strokeWidth = 2.dp
            )
        } else {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_install),
                contentDescription = stringResource(R.string.install_cd),
                modifier = Modifier.size(ActionIconSize)
            )
        }
    }
}

@Composable
fun InstalledItem(app: AppInstalled, compactMode: Boolean = false, onIgnore: (String) -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    AppListSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (app.ignored) 0.5f else 1f),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(if (compactMode) 8.dp else 12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(
                        packageName = app.packageName,
                        name = app.name,
                        version = app.version,
                        oldVersion = null,
                        versionCode = app.versionCode,
                        oldVersionCode = null,
                        single = true,
                        compactMode = compactMode
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = stringResource(R.string.expand)
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ApkTheme.colors.divider)
                    
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Open App Button
                            RoundActionButton(
                                onClick = {
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new),
                                    contentDescription = stringResource(R.string.open),
                                    modifier = Modifier.size(ActionIconSize)
                                )
                            }

                            // Ignore Button
                            RoundActionButton(
                                onClick = { onIgnore(app.packageName) }
                            ) {
                                Icon(
                                    if (app.ignored) androidx.compose.ui.res.painterResource(R.drawable.ic_visible) else androidx.compose.ui.res.painterResource(R.drawable.ic_visible_off),
                                    stringResource(if (app.ignored) R.string.unignore_cd else R.string.ignore_cd),
                                    modifier = Modifier.size(ActionIconSize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IgnoreVersionButton(
    app: AppUpdate,
    onIgnoreVersion: (Int) -> Unit,
    modifier: Modifier = Modifier
) = RoundActionButton(
    modifier = modifier,
    onClick = { onIgnoreVersion(app.id) },
    enabled = !app.isInstalling
) {
    androidx.compose.material3.Icon(
		androidx.compose.ui.res.painterResource(R.drawable.ic_visible_off),
		stringResource(R.string.ignore_cd),
		modifier = Modifier.size(ActionIconSize)
	)
}

@Composable
fun UpdateItem(
    grouped: GroupedAppUpdate,
    compactMode: Boolean = false,
    onInstall: (AppUpdate) -> Unit = {},
    onIgnoreVersion: (Int) -> Unit,
    onCancel: (AppUpdate) -> Unit = {},
    onDownload: (AppUpdate) -> Unit = {},
    onOpenPage: (AppUpdate) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var activeUpdate by remember(grouped.id) { mutableStateOf(grouped.primary) }
    val app = grouped.updates.find { it.isInstalling || it.isDownloading } ?: (grouped.updates.find { it.id == activeUpdate.id } ?: activeUpdate)

    AppListSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(if (compactMode) 8.dp else 12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(
                        app.packageName,
                        app.name,
                        app.version,
                        app.oldVersion,
                        app.versionCode,
                        app.oldVersionCode,
                        source = app.source,
                        compactMode = compactMode,
                        releaseType = app.releaseType,
                        showVersionInfo = false
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = stringResource(R.string.expand)
                    )
                }
                if (!app.isPaid) InstallButton(app, { onInstall(app) }, { onCancel(app) })
            }

            VersionInfo(
                version = app.version,
                oldVersion = app.oldVersion,
                versionCode = app.versionCode,
                oldVersionCode = app.oldVersionCode,
                single = false,
                source = app.source,
                modifier = Modifier.padding(top = if (compactMode) 6.dp else 8.dp)
            )
            
            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ApkTheme.colors.divider)
                    
                    WhatsNew(app.whatsNew, app.source)

                    if (app.link is Link.Empty) {
                        DownloadUnavailableNotice()
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val canDownload = app.link !is Link.Empty
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IgnoreVersionButton(app, onIgnoreVersion)
                            
                            RoundActionButton(
                                onClick = { onOpenPage(app) },
                                enabled = app.sourceUrl.isNotBlank() || app.releaseUrl.isNotBlank()
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new),
                                    contentDescription = stringResource(R.string.open),
                                    modifier = Modifier.size(ActionIconSize)
                                )
                            }
                            RoundActionButton(
                                onClick = { if (app.isDownloading) onCancel(app) else onDownload(app) },
                                enabled = canDownload && !app.isInstalling && !app.isPaid
                            ) {
                                if (app.isDownloading) {
                                    CircularProgressIndicator(Modifier.size(ActionProgressSize), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_download),
                                        contentDescription = stringResource(R.string.download),
                                        modifier = Modifier.size(ActionIconSize)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        if (grouped.updates.size > 1) {
                            SourceSelector(grouped.updates, activeUpdate, { activeUpdate = it })
                        } else {
                            SourceIcon(app.source, Modifier.size(32.dp))
                        }
                    }
                }
            }

            // Progress bar (always visible if installing)
            val currentProgress = grouped.updates.firstOrNull { it.isInstalling || it.isDownloading } ?: activeUpdate
            if (currentProgress.isInstalling || currentProgress.isDownloading) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (currentProgress.total > 0L) {
                            val fraction = (currentProgress.progress.toFloat() / currentProgress.total.toFloat()).coerceIn(0f, 1f)
                            val percent = (fraction * 100).roundToInt()
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = ApkTheme.colors.accent,
                                trackColor = ApkTheme.colors.surfaceSecondary
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                SmallText("$percent%")
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = ApkTheme.colors.accent,
                                trackColor = ApkTheme.colors.surfaceSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchItem(
    grouped: GroupedAppUpdate,
    compactMode: Boolean = false,
    onInstall: (AppUpdate) -> Unit = {},
    onCancel: (AppUpdate) -> Unit = {},
    onDownload: (AppUpdate) -> Unit = {},
    onOpenPage: (AppUpdate) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var activeUpdate by remember(grouped.id) { mutableStateOf(grouped.primary) }
    val app = grouped.updates.find { it.isInstalling || it.isDownloading } ?: (grouped.updates.find { it.id == activeUpdate.id } ?: activeUpdate)

    AppListSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(if (compactMode) 8.dp else 12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(
                        app.packageName,
                        app.name,
                        app.version,
                        app.oldVersion,
                        app.versionCode,
                        app.oldVersionCode,
                        app.iconUri,
                        true,
                        source = app.source,
                        compactMode = compactMode
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = stringResource(R.string.expand)
                    )
                }
                if (!app.isPaid) InstallButton(app, { onInstall(app) }, { onCancel(app) })
            }
            
            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ApkTheme.colors.divider)
                    
                    WhatsNew(app.whatsNew, app.source)

                    if (app.link is Link.Empty) {
                        DownloadUnavailableNotice()
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val canDownload = app.link !is Link.Empty
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundActionButton(
                                onClick = { onOpenPage(app) },
                                enabled = app.sourceUrl.isNotBlank() || app.releaseUrl.isNotBlank()
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new),
                                    contentDescription = stringResource(R.string.open),
                                    modifier = Modifier.size(ActionIconSize)
                                )
                            }
                            RoundActionButton(
                                onClick = { if (app.isDownloading) onCancel(app) else onDownload(app) },
                                enabled = canDownload && !app.isInstalling && !app.isPaid
                            ) {
                                if (app.isDownloading) {
                                    CircularProgressIndicator(Modifier.size(ActionProgressSize), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_download),
                                        contentDescription = stringResource(R.string.download),
                                        modifier = Modifier.size(ActionIconSize)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        if (grouped.updates.size > 1) {
                            SourceSelector(grouped.updates, activeUpdate, { activeUpdate = it })
                        } else {
                            SourceIcon(app.source, Modifier.size(32.dp))
                        }
                    }
                }
            }

            // Progress bar (always visible if installing)
            val currentProgress = grouped.updates.firstOrNull { it.isInstalling || it.isDownloading } ?: activeUpdate
            if (currentProgress.isInstalling || currentProgress.isDownloading) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (currentProgress.total > 0L) {
                            val fraction = (currentProgress.progress.toFloat() / currentProgress.total.toFloat()).coerceIn(0f, 1f)
                            val percent = (fraction * 100).roundToInt()
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = ApkTheme.colors.accent,
                                trackColor = ApkTheme.colors.surfaceSecondary
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                SmallText("$percent%")
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = ApkTheme.colors.accent,
                                trackColor = ApkTheme.colors.surfaceSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceSelector(
    updates: List<AppUpdate>,
    selected: AppUpdate,
    onSelect: (AppUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        updates.forEach { update ->
            val isSelected = update.id == selected.id
            androidx.compose.material3.Surface(
                onClick = { onSelect(update) },
                shape = ApkTheme.shapes.sm,
                color = if (isSelected) ApkTheme.colors.surfaceHighlight else ApkTheme.colors.surfaceSecondary,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ApkTheme.colors.accent.copy(alpha = 0.5f) else ApkTheme.colors.divider
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SourceIcon(update.source, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun GridItem(
    packageName: String,
    name: String,
    version: String,
    uri: Uri? = null,
    source: Source? = null,
    onIgnore: (() -> Unit)? = null,
    onOpenPage: (() -> Unit)? = null,
    isIgnored: Boolean = false,
    onClick: () -> Unit,
    updates: List<AppUpdate> = emptyList(),
    onUpdateIgnore: ((Int) -> Unit)? = null,
    onUpdateOpenPage: ((AppUpdate) -> Unit)? = null,
    onUpdateClick: ((AppUpdate) -> Unit)? = null
) {
    var activeUpdate by remember(updates) { mutableStateOf(updates.firstOrNull()) }
    val selectedUpdate = activeUpdate
    val currentVersion = selectedUpdate?.version ?: version
    val currentSource = selectedUpdate?.source ?: source
    val currentOnIgnore = if (selectedUpdate != null && onUpdateIgnore != null) ({ onUpdateIgnore(selectedUpdate.id) }) else onIgnore
    val currentOnOpenPage = if (selectedUpdate != null && onUpdateOpenPage != null) ({ onUpdateOpenPage(selectedUpdate) }) else onOpenPage
    val currentOnClick = if (selectedUpdate != null && onUpdateClick != null) ({ onUpdateClick(selectedUpdate) }) else onClick

    AppListSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isIgnored) 0.5f else 1f),
        onClick = { currentOnClick() }
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(80.dp)) {
                    if (uri == null) {
                        LoadingImageApp(packageName, Modifier.fillMaxSize())
                    } else {
                        LoadingImage(uri, Modifier.fillMaxSize())
                    }
                }
                Text(
                    text = name.ifEmpty { LocalContext.current.getAppName(packageName) }.ifEmpty { packageName },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                
                androidx.compose.material3.Surface(
                    shape = ApkTheme.shapes.xs,
                    color = ApkTheme.colors.surfaceHighlight,
                    contentColor = ApkTheme.colors.accent,
                    border = BorderStroke(1.dp, ApkTheme.colors.accent.copy(alpha = 0.28f)),
                    modifier = Modifier.alpha(0.8f)
                ) {
                    var showDropdown by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.let { if (currentOnOpenPage != null) it.clickable { currentOnOpenPage() } else it }
                        ) {
                            if (currentSource != null) {
                                Box(modifier = Modifier.padding(end = 4.dp)) {
                                    SourceIcon(currentSource, Modifier.size(14.dp))
                                }
                            }
                            Text(
                                text = currentVersion,
                                style = MaterialTheme.typography.labelSmall,
                                color = ApkTheme.colors.accent,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        if (updates.size > 1) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .clickable { showDropdown = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.search_filter_button),
                                    modifier = Modifier.size(16.dp),
                                    tint = ApkTheme.colors.accent
                                )
                            }

                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false }
                            ) {
                                updates.forEach { update ->
                                    DropdownMenuItem(
                                        text = { Text(update.version, style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = { SourceIcon(update.source, Modifier.size(24.dp)) },
                                        onClick = {
                                            activeUpdate = update
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (currentOnIgnore != null) {
                IconButton(
                    onClick = currentOnIgnore,
                    modifier = Modifier.size(32.dp).align(Alignment.TopEnd).padding(2.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            if (isIgnored) R.drawable.ic_visible else R.drawable.ic_visible_off
                        ),
                        contentDescription = stringResource(if (isIgnored) R.string.unignore_cd else R.string.ignore_cd),
                        modifier = Modifier.size(20.dp),
                        tint = ApkTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadUnavailableNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = ApkTheme.shapes.xs,
        color = ApkTheme.colors.warning.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, ApkTheme.colors.warning.copy(alpha = 0.28f))
    ) {
        Text(
            text = stringResource(R.string.download_unavailable_for_source),
            style = MaterialTheme.typography.bodySmall,
            color = ApkTheme.colors.warning,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun WhatsNew(whatsNew: String, source: Source) {
    if (whatsNew.isNotEmpty()) {
        val text = if (source == ApkMirrorSource || source == ApkPureSource || source == PlaySource) {
            HtmlCompat.fromHtml(whatsNew.trim(), HtmlCompat.FROM_HTML_MODE_COMPACT).toAnnotatedString()
        } else {
            AnnotatedString(whatsNew)
        }
        ExpandingAnnotatedText(text, Modifier.padding(8.dp).fillMaxWidth())
    }
}

@Composable
fun DefaultErrorScreen() = AppStateMessage(
    title = stringResource(R.string.something_went_wrong),
    icon = R.drawable.ic_disabled,
    tone = AppTone.Error
)
