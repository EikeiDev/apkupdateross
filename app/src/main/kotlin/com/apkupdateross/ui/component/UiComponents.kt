package com.apkupdateross.ui.component

import android.net.Uri
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
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
import com.apkupdateross.data.ui.Source
import com.apkupdateross.util.getAppName
import com.apkupdateross.util.to2f
import com.apkupdateross.util.toAnnotatedString


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
    compactMode: Boolean = false
) = Row(Modifier.fillMaxWidth()) {
    Box(Modifier.size(if (compactMode) 48.dp else 80.dp).padding(end = if (compactMode) 8.dp else 12.dp)) {
        if (uri == null) {
            LoadingImageApp(packageName, Modifier.fillMaxSize())
        } else {
            LoadingImage(uri, Modifier.fillMaxSize())
        }
    }
    Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
        LargeTitle(name.ifEmpty { LocalContext.current.getAppName(packageName) }.ifEmpty { packageName })
        MediumText(packageName)
        
        if (oldVersion != null && !single) {
            ScrollableText {
                MediumText("$oldVersion -> ${version.ifBlank { "?" }}")
            }
        } else if (version.isNotBlank()) {
            MediumText(version)
        }
        
        if (oldVersionCode != null && !single) {
            val code = if (versionCode == 0L) "?" else versionCode.toString()
            MediumText("$oldVersionCode -> $code")
        } else if (versionCode > 0L) {
            MediumText(versionCode.toString())
        }
    }
}

@Composable
fun InstallButton(
    app: AppUpdate,
    onInstall: (String) -> Unit,
    onCancel: (AppUpdate) -> Unit = {}
) = androidx.compose.material3.FilledTonalIconButton(
    modifier = Modifier,
    onClick = { if (app.isInstalling) onCancel(app) else onInstall(app.packageName) },
    enabled = !app.isDownloading
) {
    if (app.isInstalling) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_install),
            contentDescription = stringResource(R.string.install_cd),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun InstalledItem(app: AppInstalled, compactMode: Boolean = false, onIgnore: (String) -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (app.ignored) 0.5f else 1f)
            .clickable { expanded = !expanded }
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
                        contentDescription = "Expand"
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Open App Button
                            FilledTonalIconButton(
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Ignore Button
                            FilledTonalIconButton(
                                onClick = { onIgnore(app.packageName) }
                            ) {
                                Icon(
                                    if (app.ignored) androidx.compose.ui.res.painterResource(R.drawable.ic_visible) else androidx.compose.ui.res.painterResource(R.drawable.ic_visible_off),
                                    stringResource(if (app.ignored) R.string.unignore_cd else R.string.ignore_cd),
                                    modifier = Modifier.size(20.dp)
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
) = androidx.compose.material3.FilledTonalIconButton(
    modifier = modifier,
    onClick = { onIgnoreVersion(app.id) },
    enabled = !app.isInstalling
) {
    androidx.compose.material3.Icon(
		androidx.compose.ui.res.painterResource(R.drawable.ic_visible_off),
		stringResource(R.string.ignore_cd),
		modifier = Modifier.size(24.dp)
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

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(if (compactMode) 8.dp else 12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode, compactMode = compactMode)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = "Expand"
                    )
                }
                if (!app.isPaid) InstallButton(app, { onInstall(app) }, { onCancel(app) })
            }
            
            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    WhatsNew(app.whatsNew, app.source)
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val canDownload = app.link !is com.apkupdateross.data.ui.Link.Empty
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IgnoreVersionButton(app, onIgnoreVersion)
                            
                            FilledTonalIconButton(
                                onClick = { onOpenPage(app) },
                                enabled = app.sourceUrl.isNotBlank() || app.releaseUrl.isNotBlank()
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new),
                                    contentDescription = stringResource(R.string.open),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { if (app.isDownloading) onCancel(app) else onDownload(app) },
                                enabled = canDownload && !app.isInstalling && !app.isPaid
                            ) {
                                if (app.isDownloading) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_download),
                                        contentDescription = stringResource(R.string.download),
                                        modifier = Modifier.size(20.dp)
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
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                SmallText("$percent%")
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp)
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

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(if (compactMode) 8.dp else 12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode, app.iconUri, true, compactMode = compactMode)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = "Expand"
                    )
                }
                if (!app.isPaid) InstallButton(app, { onInstall(app) }, { onCancel(app) })
            }
            
            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    WhatsNew(app.whatsNew, app.source)
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val canDownload = app.link !is com.apkupdateross.data.ui.Link.Empty
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { onOpenPage(app) },
                                enabled = app.sourceUrl.isNotBlank() || app.releaseUrl.isNotBlank()
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new),
                                    contentDescription = stringResource(R.string.open),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { if (app.isDownloading) onCancel(app) else onDownload(app) },
                                enabled = canDownload && !app.isInstalling && !app.isPaid
                            ) {
                                if (app.isDownloading) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_download),
                                        contentDescription = stringResource(R.string.download),
                                        modifier = Modifier.size(20.dp)
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
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                SmallText("$percent%")
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp)
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
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
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
    isIgnored: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isIgnored) 0.5f else 1f)
            .clickable { onClick() }
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
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (source != null) {
                        SourceIcon(source, Modifier.size(18.dp).align(Alignment.CenterStart).offset(x = (-6).dp))
                    }
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.alpha(0.8f).align(Alignment.Center)
                    ) {
                        Text(
                            text = version,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (onIgnore != null) {
                IconButton(
                    onClick = onIgnore,
                    modifier = Modifier.size(32.dp).align(Alignment.TopEnd).padding(2.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            if (isIgnored) R.drawable.ic_visible else R.drawable.ic_visible_off
                        ),
                        contentDescription = "Ignore",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
fun DefaultErrorScreen() = Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    MediumTitle(stringResource(R.string.something_went_wrong))
}
