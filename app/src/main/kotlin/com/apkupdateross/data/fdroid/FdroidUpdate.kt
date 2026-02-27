package com.apkupdateross.data.fdroid

import androidx.core.net.toUri
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.Source

data class FdroidUpdate(
    val apk: FdroidPackage,
    val app: FdroidApp
)

fun FdroidUpdate.toAppUpdate(current: AppInstalled?, source: Source, url: String) = AppUpdate(
    app.localized["en-US"]?.name ?: app.name,
    app.packageName,
    apk.versionName,
    current?.version ?: "?",
    apk.versionCode,
    current?.versionCode ?: 0L,
    source,
    if(app.icon.isEmpty())
        "https://f-droid.org/assets/ic_repo_app_default.png".toUri()
    else
        "${url}icons-640/${app.icon}".toUri(),
    Link.Url("$url${apk.apkName}"),
    if (current != null) app.localized["en-US"]?.whatsNew.orEmpty() else app.localized["en-US"]?.summary.orEmpty()
)
