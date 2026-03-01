package com.apkupdateross.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column(Modifier.weight(1f).padding(end = 56.dp).align(Alignment.CenterVertically)) {
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
    modifier = Modifier.padding(top = 0.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
    onClick = { if (app.isInstalling) onCancel() else onInstall(app.packageName) }
) {
    if (app.isInstalling) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_install), stringResource(R.string.install_cd))
    }
}

@Composable
fun InstalledItem(app: AppInstalled, onIgnore: (String) -> Unit = {}) = Card(
    modifier = Modifier.alpha(if (app.ignored) 0.5f else 1f),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(80.dp).padding(end = 12.dp)) {
            LoadingImageApp(app.packageName, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            LargeTitle(app.name.ifEmpty { LocalContext.current.getAppName(app.packageName) }.ifEmpty { app.packageName })
            MediumText(app.packageName)
            MediumText(app.version)
            MediumText(if (app.versionCode == 0L) "?" else app.versionCode.toString())
        }
        androidx.compose.material3.FilledTonalIconButton(
            onClick = { onIgnore(app.packageName) },
            modifier = Modifier.padding(start = 12.dp)
        ) {
            androidx.compose.material3.Icon(
                if (app.ignored) androidx.compose.ui.res.painterResource(R.drawable.ic_visible) else androidx.compose.ui.res.painterResource(R.drawable.ic_visible_off),
                stringResource(if (app.ignored) R.string.unignore_cd else R.string.ignore_cd)
            )
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
    onCancel: () -> Unit = {}
) = Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            CommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode)
            Box(Modifier.padding(end = 48.dp)) {
                WhatsNew(app.whatsNew, app.source)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceIcon(app.source, Modifier.size(34.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (app.isInstalling && app.total > 0L && app.progress > 0L) {
                        val percent = (app.progress * 100 / app.total).coerceIn(0, 100)
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        ) {
                            SmallText(
                                "$percent%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                InstallButton(app, onInstall, onCancel)
            }
        }
        IgnoreVersionButton(
            app,
            onIgnoreVersion,
            Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 20.dp)
        )
    }
}

@Composable
fun SearchItem(app: AppUpdate, onInstall: (String) -> Unit = {}, onCancel: () -> Unit = {}) = Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
    Column(Modifier.padding(12.dp)) {
        CommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode, app.iconUri, true)
        WhatsNew(app.whatsNew, app.source)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceIcon(app.source, Modifier.size(34.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (app.isInstalling && app.total > 0L && app.progress > 0L) {
                    val percent = (app.progress * 100 / app.total).coerceIn(0, 100)
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ) {
                        SmallText(
                            "$percent%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            InstallButton(app, onInstall, onCancel)
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
