package com.apkupdateross.data.rustore

import android.net.Uri
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.RuStoreSource
import com.google.gson.annotations.SerializedName

// Generic API response wrapper
data class RuStoreApiResponse<T>(
    val code: String = "",
    val body: T? = null
)

// App details from /applicationData/overallInfo/{packageName}
data class RuStoreAppDetails(
    val appId: Long = 0,
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val iconUrl: String = "",
    val shortDescription: String = "",
    val fullDescription: String = "",
    val fileSize: Long = 0,
    val minSdkVersion: Int = 0,
    val downloads: Long = 0,
    val appVerUpdatedAt: String = "",
    val whatsNew: String = ""
)

// Search response body from /applicationData/apps
data class RuStoreSearchBody(
    val content: List<RuStoreSearchApp> = emptyList(),
    val totalPages: Int = 0
)

// Search result item
data class RuStoreSearchApp(
    val packageName: String = "",
    val appName: String = "",
    val averageUserRating: Double = 0.0,
    val iconUrl: String = ""
)

// Download link request body for /applicationData/v2/download-link
data class RuStoreDownloadRequest(
    val appId: Long,
    val firstInstall: Boolean = true,
    val supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi"),
    val sdkVersion: Int = 21,
    val withoutSplits: Boolean = true,
    val signatureFingerprint: String? = null
)

// Download link response body
data class RuStoreDownloadBody(
    val downloadUrls: List<RuStoreDownloadUrl> = emptyList()
)

data class RuStoreDownloadUrl(
    val url: String = "",
    val type: String = ""
)

// Extension to convert RuStoreAppDetails to AppUpdate
fun RuStoreAppDetails.toAppUpdate(
    installedApp: AppInstalled?,
    downloadUrl: String = ""
) = AppUpdate(
    name = appName,
    packageName = packageName,
    version = versionName,
    oldVersion = installedApp?.version ?: "",
    versionCode = versionCode,
    oldVersionCode = installedApp?.versionCode ?: 0,
    source = RuStoreSource,
    iconUri = if (iconUrl.isNotEmpty()) Uri.parse(iconUrl) else Uri.EMPTY,
    link = if (downloadUrl.isNotEmpty()) Link.Url(downloadUrl, fileSize) else Link.Empty,
    whatsNew = whatsNew
)
