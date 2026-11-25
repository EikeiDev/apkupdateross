package com.apkupdateross.data.apkpure

import android.net.Uri
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link


data class AppUpdateResponse(
    val package_name: String,
    val version_code: Long,
    val version_name: String,
    val sign: List<String>,
    val whatsnew: String,
    val description_short: String,
    val label: String,
    val asset: AppUpdateResponseAsset,
    val icon: AppUpdateResponseIcon
)

fun AppUpdateResponse.toAppUpdate(
    app: AppInstalled?
) = AppUpdate(
    name = label,
    packageName = package_name,
    version = version_name,
    oldVersion = app?.version.orEmpty(),
    versionCode = version_code,
    oldVersionCode = app?.versionCode ?: 0L,
    source = ApkPureSource,
    iconUri = if (app == null) Uri.parse(icon.thumbnail.url) else Uri.EMPTY,
    link = if (asset.url.contains("/XAPK")) Link.Xapk(asset.url.replace("http://", "https://")) else Link.Url(asset.url.replace("http://", "https://")),
    sourceUrl = buildApkPureUrl(label, package_name),
    releaseUrl = buildApkPureUrl(label, package_name),
    whatsNew = if (app == null) description_short else whatsnew
)

private fun buildApkPureUrl(label: String, packageName: String): String {
    val slug = label.lowercase()
        .replace("[^a-z0-9]+".toRegex(), "-")
        .trim('-')
        .ifBlank { packageName }
    return "https://apkpure.com/$slug/$packageName"
}
