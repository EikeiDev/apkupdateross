package com.apkupdateross.data.rustore

import android.net.Uri
import android.os.Build
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

// Batch request for checking multiple apps at once
data class RuStoreBatchRequest(
    val content: List<RuStoreBatchEntry>
)

data class RuStoreBatchEntry(
    val packageName: String,
    val versionCode: Long
)

// Batch response body
data class RuStoreBatchBody(
    val content: List<RuStoreBatchApp> = emptyList()
)

// Batch response - NOT wrapped in RuStoreApiResponse
data class RuStoreBatchResponse(
    val body: RuStoreBatchBody = RuStoreBatchBody()
)

data class RuStoreBatchApp(
    val appId: Long = 0L,
    val packageName: String = "",
    val appName: String = "",
    val updatedAt: String = "",
    val versionCode: Long = 0L
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
    val companyName: String = "",
    val appVerUpdatedAt: String = "",
    val whatsNew: String = "",
    val publicCompanyId: String? = null,
    val labelIds: List<Int>? = null
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
    val sdkVersion: Int = Build.VERSION.SDK_INT,
    val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    val withoutSplits: Boolean = true
)

// Download link response body
data class RuStoreDownloadBody(
    val downloadUrls: List<RuStoreDownloadUrl> = emptyList()
)

data class RuStoreDownloadUrl(
    val url: String = "",
    val type: String = ""
)

// Extension to convert RuStore .zip URLs to .apk URLs
fun String.ruStoreApkUrl(): String =
    if (endsWith(".zip", ignoreCase = true)) dropLast(4) + ".apk" else this

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
    link = if (downloadUrl.isNotEmpty()) Link.Url(downloadUrl) else Link.Empty,
    whatsNew = whatsNew,
    total = fileSize,
    sourceUrl = "https://www.rustore.ru/catalog/app/$packageName",
    releaseUrl = "https://www.rustore.ru/catalog/app/$packageName"
)
