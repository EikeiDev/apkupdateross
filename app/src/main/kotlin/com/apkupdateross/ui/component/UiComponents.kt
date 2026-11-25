package com.apkupdateross.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.apkupdateross.R
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GroupedAppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.Source
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.util.formatSize
import com.apkupdateross.util.getAppName
import com.apkupdateross.util.toAnnotatedString
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val PrimaryActionSize = 48.dp
private val ActionIconSize = 18.dp
private val ActionProgressSize = 18.dp

@Composable
fun InstallButton(
    app: AppUpdate,
    onInstall: (String) -> Unit,
    onCancel: (AppUpdate) -> Unit = {},
    fullWidth: Boolean = false
) {
    val enabled = !app.isDownloading && (app.link !is Link.Empty || app.isInstalling)
    val containerColor = if (enabled) ApkTheme.colors.accent else ApkTheme.colors.surfaceSecondary
    val contentColor = if (enabled) ApkTheme.colors.onAccent else ApkTheme.colors.disabled
    val shape = ApkTheme.shapes.md

    Surface(
        modifier = (if (fullWidth) Modifier.fillMaxWidth().heightIn(min = 44.dp) else Modifier.size(PrimaryActionSize))
            .tvFocusOutline(shape, enabled),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) {
                    if (app.isInstalling) onCancel(app) else onInstall(app.packageName)
                }
                .padding(horizontal = if (fullWidth) 14.dp else 0.dp, vertical = if (fullWidth) 10.dp else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.isInstalling) {
                CircularProgressIndicator(
                    Modifier.size(ActionProgressSize),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_install),
                    contentDescription = stringResource(R.string.install_cd),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (fullWidth) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (app.isInstalling) R.string.settings_custom_repo_cancel else R.string.install_cd
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstalledItem(
    app: AppInstalled,
    compactMode: Boolean = false,
    onIgnore: (String) -> Unit = {},
    onCheckUpdate: ((String) -> Unit)? = null,
    onBackupApk: ((AppInstalled) -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val isLargeText = ApkTheme.accessibility.isLargeText

    AppListSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (app.ignored) 0.58f else 1f),
        onClick = { expanded = !expanded }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactMode) 10.dp else 14.dp)
        ) {
            val isNarrow = maxWidth < 310.dp
            val iconSize = if (compactMode || isNarrow) 44.dp else 50.dp
            val displayName = app.name.ifEmpty { context.getAppName(app.packageName) }.ifEmpty { app.packageName }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        LoadingImageApp(app.packageName, Modifier.fillMaxSize())
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ApkTheme.colors.textPrimary,
                            maxLines = if (isLargeText) 3 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (app.version.isNotBlank()) {
                                MetaPill(text = app.version)
                            }
                            if (app.installerSource.isNotBlank()) {
                                MetaPill(
                                    text = app.installerSource,
                                    accent = false,
                                    tinted = true
                                )
                            }
                            if (app.ignored) {
                                AppStatusBadge(
                                    text = stringResource(R.string.card_ignored_badge),
                                    tone = AppTone.Warning
                                )
                            }
                        }
                    }

                    Icon(
                        painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = stringResource(R.string.expand),
                        tint = ApkTheme.colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = ApkTheme.colors.divider)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = ApkTheme.colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (app.versionCode > 0L) {
                                Text(
                                    text = stringResource(R.string.card_version_code_single, app.versionCode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApkTheme.colors.textTertiary
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onCheckUpdate != null) {
                                ActionPillButton(
                                    label = stringResource(R.string.card_action_check),
                                    iconRes = R.drawable.ic_refresh,
                                    highlighted = true,
                                    onClick = { onCheckUpdate(app.packageName) }
                                )
                            }

                            if (onBackupApk != null && app.sourceDir.isNotBlank()) {
                                ActionPillButton(
                                    label = stringResource(R.string.card_action_backup),
                                    iconRes = R.drawable.ic_download,
                                    onClick = { onBackupApk(app) }
                                )
                            }

                            ActionPillButton(
                                label = stringResource(R.string.open),
                                iconRes = R.drawable.ic_open_in_new,
                                onClick = {
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    }
                                }
                            )

                            ActionPillButton(
                                label = stringResource(R.string.card_action_info),
                                iconRes = R.drawable.ic_info,
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${app.packageName}")
                                    )
                                    runCatching { context.startActivity(intent) }
                                }
                            )

                            ActionPillButton(
                                label = stringResource(if (app.ignored) R.string.card_action_show else R.string.card_action_hide),
                                iconRes = if (app.ignored) R.drawable.ic_visible else R.drawable.ic_visible_off,
                                onClick = { onIgnore(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateItem(
    grouped: GroupedAppUpdate,
    compactMode: Boolean = false,
    onInstall: (AppUpdate) -> Unit = {},
    onIgnoreVersion: (Int) -> Unit,
    onCancel: (AppUpdate) -> Unit = {},
    onDownload: (AppUpdate) -> Unit = {},
    onOpenPage: (AppUpdate) -> Unit = {},
    pinnedSourceName: String? = null,
    onTogglePinSource: ((AppUpdate) -> Unit)? = null
) = AppUpdateCard(
    grouped = grouped,
    compactMode = compactMode,
    isSearchItem = false,
    onInstall = onInstall,
    onIgnoreVersion = onIgnoreVersion,
    onCancel = onCancel,
    onDownload = onDownload,
    onOpenPage = onOpenPage,
    pinnedSourceName = pinnedSourceName,
    onTogglePinSource = onTogglePinSource
)

@Composable
fun SearchItem(
    grouped: GroupedAppUpdate,
    compactMode: Boolean = false,
    onInstall: (AppUpdate) -> Unit = {},
    onCancel: (AppUpdate) -> Unit = {},
    onDownload: (AppUpdate) -> Unit = {},
    onOpenPage: (AppUpdate) -> Unit = {}
) = AppUpdateCard(
    grouped = grouped,
    compactMode = compactMode,
    isSearchItem = true,
    onInstall = onInstall,
    onIgnoreVersion = null,
    onCancel = onCancel,
    onDownload = onDownload,
    onOpenPage = onOpenPage
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppUpdateCard(
    grouped: GroupedAppUpdate,
    compactMode: Boolean,
    isSearchItem: Boolean,
    onInstall: (AppUpdate) -> Unit,
    onIgnoreVersion: ((Int) -> Unit)?,
    onCancel: (AppUpdate) -> Unit,
    onDownload: (AppUpdate) -> Unit,
    onOpenPage: (AppUpdate) -> Unit,
    pinnedSourceName: String? = null,
    onTogglePinSource: ((AppUpdate) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val updateIdsKey = remember(grouped.updates) { grouped.updates.map { it.id } }
    var selectedUpdateId by remember(grouped.packageName, updateIdsKey) { mutableStateOf(grouped.primary.id) }
    val app = grouped.updates.find { it.isInstalling || it.isDownloading }
        ?: grouped.updates.find { it.id == selectedUpdateId }
        ?: grouped.primary

    val context = LocalContext.current
    val isLargeText = ApkTheme.accessibility.isLargeText
    val isExtraLargeText = ApkTheme.accessibility.isExtraLargeText
    val displayName = app.name.ifEmpty { context.getAppName(app.packageName) }.ifEmpty { app.packageName }
    val extraSourcesCount = (grouped.updates.size - 1).coerceAtLeast(0)
    val isPinned = pinnedSourceName == app.source.name

    AppListSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactMode) 10.dp else 14.dp)
        ) {
            val isNarrow = maxWidth < 310.dp
            val stackPrimaryAction = isExtraLargeText || (isLargeText && isNarrow) || maxWidth < 250.dp
            val stackVersion = isLargeText || isNarrow
            val iconSize = if (compactMode || isNarrow) 44.dp else 52.dp

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: Icon + Title + Source/Type badges + Primary Install button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        if (isSearchItem) {
                            LoadingImage(app.iconUri, Modifier.fillMaxSize())
                        } else {
                            LoadingImageApp(app.packageName, Modifier.fillMaxSize())
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ApkTheme.colors.textPrimary,
                            maxLines = if (isLargeText) 3 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SourceBadgePill(
                                source = app.source,
                                extraCount = extraSourcesCount,
                                isPinned = isPinned
                            )
                            if (!isSearchItem) {
                                ReleaseTypeChip(app.releaseType, compactMode = compactMode)
                            } else if (app.version.isNotBlank()) {
                                MetaPill(text = app.version, accent = true)
                            }
                        }
                    }

                    if (!app.isPaid && !stackPrimaryAction) {
                        InstallButton(app, { onInstall(app) }, { onCancel(app) })
                    }
                }

                // Version transition strip (Updates) or subtle metadata bar (Search)
                if (!isSearchItem) {
                    VersionTransitionStrip(
                        oldVersion = app.oldVersion,
                        newVersion = app.version,
                        oldVersionCode = app.oldVersionCode,
                        newVersionCode = app.versionCode,
                        stacked = stackVersion,
                        expanded = expanded
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = ApkTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                            contentDescription = stringResource(R.string.expand),
                            tint = ApkTheme.colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Full-width Install CTA when font is extra large or card is very narrow
                if (!app.isPaid && stackPrimaryAction) {
                    InstallButton(
                        app = app,
                        onInstall = { onInstall(app) },
                        onCancel = { onCancel(app) },
                        fullWidth = true
                    )
                }

                // Expanded details: full packageName, versionCode, source switcher, release notes, actions
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = ApkTheme.colors.divider)

                        // Technical details (always full, never truncated harshly)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = ApkTheme.colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSearchItem && app.versionCode > 0L) {
                                Text(
                                    text = stringResource(R.string.card_version_code_single, app.versionCode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApkTheme.colors.textTertiary
                                )
                            }
                        }

                        // Multi-source selector showing icon + name + version
                        if (grouped.updates.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.card_sources_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApkTheme.colors.textTertiary
                                )
                                SourceSelector(
                                    updates = grouped.updates,
                                    selected = app,
                                    onSelect = { selectedUpdateId = it.id }
                                )
                            }
                        }

                        // Release notes in a recessed container
                        if (app.whatsNew.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ApkTheme.shapes.sm,
                                color = ApkTheme.colors.surfaceSecondary,
                                border = BorderStroke(1.dp, ApkTheme.colors.divider.copy(alpha = 0.7f))
                            ) {
                                WhatsNew(app.whatsNew, app.source)
                            }
                        }

                        if (app.link is Link.Empty) {
                            DownloadUnavailableNotice()
                        }

                        // Adaptive FlowRow of clear action pills
                        val canDownload = app.link !is Link.Empty
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onTogglePinSource != null) {
                                ActionPillButton(
                                    label = stringResource(if (isPinned) R.string.card_action_pinned else R.string.card_action_pin),
                                    iconVector = Icons.Filled.Star,
                                    highlighted = isPinned,
                                    onClick = { onTogglePinSource(app) }
                                )
                            }

                            ActionPillButton(
                                label = stringResource(R.string.download),
                                iconRes = R.drawable.ic_download,
                                enabled = canDownload && !app.isInstalling && !app.isPaid,
                                loading = app.isDownloading,
                                onClick = { if (app.isDownloading) onCancel(app) else onDownload(app) }
                            )

                            ActionPillButton(
                                label = stringResource(R.string.card_action_source),
                                iconRes = R.drawable.ic_open_in_new,
                                enabled = app.sourceUrl.isNotBlank() || app.releaseUrl.isNotBlank(),
                                onClick = { onOpenPage(app) }
                            )

                            if (onIgnoreVersion != null) {
                                ActionPillButton(
                                    label = stringResource(R.string.card_action_hide),
                                    iconRes = R.drawable.ic_visible_off,
                                    enabled = !app.isInstalling,
                                    onClick = { onIgnoreVersion(app.id) }
                                )
                            }
                        }
                    }
                }

                // Active download/install progress bar
                val currentProgress = grouped.updates.firstOrNull { it.isInstalling || it.isDownloading } ?: app
                if (currentProgress.isInstalling || currentProgress.isDownloading) {
                    InstallProgressBar(currentProgress)
                }
            }
        }
    }
}

@Composable
private fun VersionTransitionStrip(
    oldVersion: String,
    newVersion: String,
    oldVersionCode: Long,
    newVersionCode: Long,
    stacked: Boolean,
    expanded: Boolean
) {
    val previous = oldVersion.takeIf { it.isNotBlank() && it != "?" }
    val target = newVersion.ifBlank { "?" }
    val sameVersionName = previous != null && previous == target
    val showVersionCode = newVersionCode > 0L
    val versionLabel = stringResource(R.string.card_label_version)
    val codeLabel = stringResource(R.string.card_label_code)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ApkTheme.shapes.sm,
        color = ApkTheme.colors.surfaceSecondary.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, ApkTheme.colors.divider.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FramedTransitionRow(
                    label = versionLabel,
                    oldValue = previous,
                    newValue = target,
                    stacked = stacked,
                    highlightNew = true,
                    compact = false
                )

                if (showVersionCode) {
                    FramedTransitionRow(
                        label = codeLabel,
                        oldValue = oldVersionCode.takeIf { it > 0L }?.toString(),
                        newValue = newVersionCode.toString(),
                        stacked = stacked,
                        highlightNew = sameVersionName,
                        compact = true
                    )
                }
            }

            Icon(
                painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = stringResource(R.string.expand),
                tint = ApkTheme.colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FramedTransitionRow(
    label: String,
    oldValue: String?,
    newValue: String,
    stacked: Boolean,
    highlightNew: Boolean,
    compact: Boolean
) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ApkTheme.colors.textTertiary
            )
            if (oldValue != null) {
                VersionFrameChip(
                    text = oldValue,
                    highlighted = false,
                    compact = compact
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (oldValue != null) {
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApkTheme.colors.textTertiary
                    )
                }
                VersionFrameChip(
                    text = newValue,
                    highlighted = highlightNew,
                    compact = compact
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = ApkTheme.colors.textTertiary
            )
            if (oldValue != null) {
                VersionFrameChip(
                    text = oldValue,
                    highlighted = false,
                    compact = compact,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApkTheme.colors.textTertiary
                )
            }
            VersionFrameChip(
                text = newValue,
                highlighted = highlightNew,
                compact = compact,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun VersionFrameChip(
    text: String,
    highlighted: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (highlighted) {
        ApkTheme.colors.surfaceHighlight
    } else {
        ApkTheme.colors.surface
    }
    val borderColor = if (highlighted) {
        ApkTheme.colors.accent.copy(alpha = 0.55f)
    } else {
        ApkTheme.colors.divider
    }
    val textColor = if (highlighted) ApkTheme.colors.accent else ApkTheme.colors.textSecondary
    val textStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val scrollState = rememberScrollState()
    val playAnimations = koinInject<Prefs>().playTextAnimations.get()
    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(text, playAnimations, scrollState.maxValue, isDragged) {
        if (!playAnimations || isDragged || scrollState.maxValue <= 0) return@LaunchedEffect
        val max = scrollState.maxValue
        val durationMs = (max * 14).coerceIn(1200, 8000)
        while (true) {
            delay(1400)
            scrollState.animateScrollTo(max, tween(durationMillis = durationMs, easing = LinearEasing))
            delay(1200)
            scrollState.animateScrollTo(0, tween(durationMillis = durationMs, easing = LinearEasing))
        }
    }

    Surface(
        modifier = modifier,
        shape = ApkTheme.shapes.xs,
        color = containerColor,
        contentColor = textColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(
                    horizontal = if (compact) 7.dp else 8.dp,
                    vertical = if (compact) 2.dp else 3.dp
                )
        ) {
            Text(
                text = text,
                style = textStyle,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun SourceBadgePill(
    source: Source,
    extraCount: Int = 0,
    isPinned: Boolean = false
) {
    Surface(
        shape = ApkTheme.shapes.xs,
        color = if (isPinned) ApkTheme.colors.surfaceHighlight else ApkTheme.colors.surfaceSecondary,
        border = BorderStroke(
            1.dp,
            if (isPinned) ApkTheme.colors.accent.copy(alpha = 0.45f) else ApkTheme.colors.divider
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            SourceIcon(source, Modifier.size(13.dp))
            Text(
                text = if (extraCount > 0) "${source.name} (+$extraCount)" else source.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPinned) ApkTheme.colors.accent else ApkTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPinned) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = ApkTheme.colors.accent,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun MetaPill(
    text: String,
    accent: Boolean = false,
    tinted: Boolean = false
) {
    val bg = when {
        accent -> ApkTheme.colors.surfaceHighlight
        tinted -> ApkTheme.colors.surfaceSecondary
        else -> ApkTheme.colors.surfaceSecondary.copy(alpha = 0.7f)
    }
    val fg = if (accent) ApkTheme.colors.accent else ApkTheme.colors.textSecondary
    val border = if (accent) ApkTheme.colors.accent.copy(alpha = 0.35f) else ApkTheme.colors.divider

    Surface(
        shape = ApkTheme.shapes.xs,
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
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
        color = content.copy(alpha = if (dark) 0.18f else 0.12f),
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.32f))
    ) {
        Text(
            text = stringResource(type.labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (compactMode) 6.dp else 7.dp,
                vertical = 3.dp
            )
        )
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int? = null,
    iconVector: ImageVector? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    loading: Boolean = false
) {
    val bg = when {
        !enabled -> ApkTheme.colors.surfaceSecondary.copy(alpha = 0.45f)
        highlighted -> ApkTheme.colors.surfaceHighlight
        else -> ApkTheme.colors.surfaceSecondary
    }
    val fg = when {
        !enabled -> ApkTheme.colors.disabled
        highlighted -> ApkTheme.colors.accent
        else -> ApkTheme.colors.textPrimary
    }
    val borderColor = when {
        !enabled -> ApkTheme.colors.divider.copy(alpha = 0.5f)
        highlighted -> ApkTheme.colors.accent.copy(alpha = 0.45f)
        else -> ApkTheme.colors.divider
    }

    Surface(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .tvFocusOutline(ApkTheme.shapes.sm, enabled),
        shape = ApkTheme.shapes.sm,
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = fg,
                    strokeWidth = 2.dp
                )
                iconVector != null -> Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fg
                )
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fg
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InstallProgressBar(currentProgress: AppUpdate) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    ) {
        if (currentProgress.total > 0L) {
            val fraction = (currentProgress.progress.toFloat() / currentProgress.total.toFloat()).coerceIn(0f, 1f)
            val percent = (fraction * 100).roundToInt()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = ApkTheme.colors.accent,
                trackColor = ApkTheme.colors.surfaceSecondary,
                strokeCap = StrokeCap.Round
            )
            val sizeText = "${currentProgress.progress.coerceAtLeast(0L).formatSize(context)} / ${currentProgress.total.formatSize(context)} · $percent%"
            SmallText(sizeText, color = ApkTheme.colors.accent)
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = ApkTheme.colors.accent,
                trackColor = ApkTheme.colors.surfaceSecondary,
                strokeCap = StrokeCap.Round
            )
            if (currentProgress.progress > 0L) {
                SmallText(currentProgress.progress.formatSize(context), color = ApkTheme.colors.textSecondary)
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
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        updates.forEach { update ->
            val isSelected = update.id == selected.id
            Surface(
                onClick = { onSelect(update) },
                shape = ApkTheme.shapes.sm,
                color = if (isSelected) ApkTheme.colors.surfaceHighlight else ApkTheme.colors.surfaceSecondary,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ApkTheme.colors.accent.copy(alpha = 0.6f) else ApkTheme.colors.divider
                ),
                modifier = Modifier.tvFocusOutline(ApkTheme.shapes.sm)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceIcon(update.source, Modifier.size(20.dp))
                    Column {
                        Text(
                            text = update.source.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) ApkTheme.colors.accent else ApkTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (update.version.isNotBlank()) {
                            Text(
                                text = update.version,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) ApkTheme.colors.accent.copy(alpha = 0.85f) else ApkTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
    val updateIdsKey = remember(updates) { updates.map { it.id } }
    var selectedUpdateId by remember(packageName, updateIdsKey) { mutableStateOf(updates.firstOrNull()?.id) }
    val selectedUpdate = updates.find { it.id == selectedUpdateId } ?: updates.firstOrNull()
    val currentVersion = selectedUpdate?.version ?: version
    val currentSource = selectedUpdate?.source ?: source
    val currentOnIgnore = if (selectedUpdate != null && onUpdateIgnore != null) ({ onUpdateIgnore(selectedUpdate.id) }) else onIgnore
    val currentOnOpenPage = if (selectedUpdate != null && onUpdateOpenPage != null) ({ onUpdateOpenPage(selectedUpdate) }) else onOpenPage
    val currentOnClick = if (selectedUpdate != null && onUpdateClick != null) ({ onUpdateClick(selectedUpdate) }) else onClick
    val isLargeText = ApkTheme.accessibility.isLargeText

    AppListSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isIgnored) 0.55f else 1f),
        onClick = { currentOnClick() }
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(62.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        if (uri == null) {
                            LoadingImageApp(packageName, Modifier.fillMaxSize())
                        } else {
                            LoadingImage(uri, Modifier.fillMaxSize())
                        }
                    }
                    if (currentSource != null) {
                        Surface(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd),
                            shape = CircleShape,
                            color = ApkTheme.colors.surfaceElevated,
                            border = BorderStroke(1.dp, ApkTheme.colors.divider)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SourceIcon(currentSource, Modifier.size(13.dp))
                            }
                        }
                    }
                }

                Text(
                    text = name.ifEmpty { LocalContext.current.getAppName(packageName) }.ifEmpty { packageName },
                    style = MaterialTheme.typography.labelLarge,
                    color = ApkTheme.colors.textPrimary,
                    maxLines = if (isLargeText) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = ApkTheme.shapes.xs,
                    color = ApkTheme.colors.surfaceHighlight,
                    contentColor = ApkTheme.colors.accent,
                    border = BorderStroke(1.dp, ApkTheme.colors.accent.copy(alpha = 0.28f))
                ) {
                    var showDropdown by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.let {
                                if (currentOnOpenPage != null) {
                                    it.tvFocusOutline(ApkTheme.shapes.xs).clickable { currentOnOpenPage() }
                                } else it
                            }
                        ) {
                            Text(
                                text = currentVersion,
                                style = MaterialTheme.typography.labelSmall,
                                color = ApkTheme.colors.accent,
                                textAlign = TextAlign.Center,
                                maxLines = if (isLargeText) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (updates.size > 1) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .tvFocusOutline(ApkTheme.shapes.xs)
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
                                        text = {
                                            Column {
                                                Text(update.source.name, style = MaterialTheme.typography.labelMedium)
                                                Text(
                                                    update.version,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = ApkTheme.colors.textSecondary
                                                )
                                            }
                                        },
                                        leadingIcon = { SourceIcon(update.source, Modifier.size(22.dp)) },
                                        onClick = {
                                            selectedUpdateId = update.id
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
                    modifier = Modifier.size(30.dp).align(Alignment.TopEnd).padding(2.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isIgnored) R.drawable.ic_visible else R.drawable.ic_visible_off
                        ),
                        contentDescription = stringResource(if (isIgnored) R.string.unignore_cd else R.string.ignore_cd),
                        modifier = Modifier.size(17.dp),
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
        modifier = Modifier.fillMaxWidth(),
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
    val trimmed = whatsNew.trim()
    if (trimmed.isEmpty()) return

    val isHtmlSource = source == ApkMirrorSource || source == ApkPureSource || source == PlaySource
    if (isHtmlSource && HTML_TAG_REGEX.containsMatchIn(trimmed)) {
        val text = HtmlCompat.fromHtml(trimmed, HtmlCompat.FROM_HTML_MODE_COMPACT).toAnnotatedString()
        ExpandingAnnotatedText(text, Modifier.padding(10.dp).fillMaxWidth())
    } else {
        ReleaseNotesContent(
            markdown = trimmed,
            modifier = Modifier.padding(10.dp),
            maxCollapsedLines = 3
        )
    }
}

private val HTML_TAG_REGEX = Regex("""<\s*(br|p|div|ul|ol|li|b|strong|i|em|a|h[1-6])\b[^>]*>""", RegexOption.IGNORE_CASE)

@Composable
fun SourceStatusChipsRow(
    loadingSources: Set<Source>,
    failedSources: Set<Source>,
    modifier: Modifier = Modifier,
    onRetrySource: ((Source) -> Unit)? = null
) {
    AnimatedVisibility(
        visible = loadingSources.isNotEmpty() || failedSources.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            items(loadingSources.toList(), key = { "loading_${it.name}" }) { source ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(source.name, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = ApkTheme.colors.surfaceSecondary,
                        labelColor = ApkTheme.colors.textSecondary,
                        disabledContainerColor = ApkTheme.colors.surfaceSecondary,
                        disabledLabelColor = ApkTheme.colors.textSecondary
                    )
                )
            }
            items(failedSources.toList(), key = { "failed_${it.name}" }) { source ->
                val canRetry = onRetrySource != null
                AssistChip(
                    onClick = { onRetrySource?.invoke(source) },
                    enabled = canRetry,
                    modifier = if (canRetry) Modifier.tvFocusOutline(ApkTheme.shapes.sm) else Modifier,
                    label = { Text(source.name, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = ApkTheme.colors.error
                        )
                    },
                    trailingIcon = if (canRetry) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.retry_source_cd, source.name),
                                modifier = Modifier.size(12.dp),
                                tint = ApkTheme.colors.error
                            )
                        }
                    } else null,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = ApkTheme.colors.error.copy(alpha = 0.18f),
                        labelColor = ApkTheme.colors.error,
                        disabledContainerColor = ApkTheme.colors.error.copy(alpha = 0.18f),
                        disabledLabelColor = ApkTheme.colors.error
                    )
                )
            }
        }
    }
}

@Composable
fun DefaultErrorScreen() = AppStateMessage(
    title = stringResource(R.string.something_went_wrong),
    icon = R.drawable.ic_disabled,
    tone = AppTone.Error
)
