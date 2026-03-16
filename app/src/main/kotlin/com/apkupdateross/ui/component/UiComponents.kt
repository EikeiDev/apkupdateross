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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.apkupdateross.R
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
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
    source: Source? = null
) = Row(Modifier.fillMaxWidth()) {
    Box(Modifier.size(80.dp).padding(end = 12.dp)) {
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
                MediumText("$oldVersion -> $version")
            }
        } else {
            MediumText(version)
        }
        val code = if (versionCode == 0L) "?" else versionCode.toString()
        if (oldVersionCode != null && !single) {
            MediumText("$oldVersionCode -> $code")
        } else {
            MediumText(code)
        }
    }
}

@Composable
fun InstallButton(
    app: AppUpdate,
    onInstall: (String) -> Unit,
    onCancel: () -> Unit = {}
) = androidx.compose.material3.FilledTonalIconButton(
    modifier = Modifier,
    onClick = { if (app.isInstalling) onCancel() else onInstall(app.packageName) }
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
fun InstalledItem(app: AppInstalled, onIgnore: (String) -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (app.ignored) 0.5f else 1f)
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
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
                        single = true
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
    app: AppUpdate,
    onInstall: (String) -> Unit = {},
    onIgnoreVersion: (Int) -> Unit,
    onCancel: () -> Unit = {},
    onDownload: (AppUpdate) -> Unit = {},
    onOpenPage: (AppUpdate) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = "Expand"
                    )
                }
                InstallButton(app, onInstall, onCancel)
            }
            
            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    WhatsNew(app.whatsNew, app.source)
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IgnoreVersionButton(app, onIgnoreVersion, Modifier.padding(end = 12.dp))
                        
                        val canDownload = app.link !is com.apkupdateross.data.ui.Link.Play && app.link !is com.apkupdateross.data.ui.Link.Empty
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                onClick = { onDownload(app) },
                                enabled = canDownload
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_download),
                                    contentDescription = stringResource(R.string.download),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        SourceIcon(app.source, Modifier.size(32.dp))
                    }
                }
            }

            // Progress bar (always visible if installing)
            if (app.isInstalling && app.total > 0L && app.progress > 0L) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    val fraction = (app.progress.toFloat() / app.total.toFloat()).coerceIn(0f, 1f)
                    val percent = (fraction * 100).toInt()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SmallText("$percent%")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchItem(
    app: AppUpdate,
    onInstall: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    onDownload: (AppUpdate) -> Unit = {},
    onOpenPage: (AppUpdate) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Always visible top row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    CommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode, app.iconUri, true)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = "Expand"
                    )
                }
                InstallButton(app, onInstall, onCancel)
            }
            
            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    WhatsNew(app.whatsNew, app.source)
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val canDownload = app.link !is com.apkupdateross.data.ui.Link.Play && app.link !is com.apkupdateross.data.ui.Link.Empty
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                onClick = { onDownload(app) },
                                enabled = canDownload
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_download),
                                    contentDescription = stringResource(R.string.download),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        SourceIcon(app.source, Modifier.size(32.dp))
                    }
                }
            }

            // Progress bar (always visible if installing)
            if (app.isInstalling && app.total > 0L && app.progress > 0L) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    val fraction = (app.progress.toFloat() / app.total.toFloat()).coerceIn(0f, 1f)
                    val percent = (fraction * 100).toInt()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SmallText("$percent%")
                        }
                    }
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
