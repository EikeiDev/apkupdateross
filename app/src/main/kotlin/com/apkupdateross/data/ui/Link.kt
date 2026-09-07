package com.apkupdateross.data.ui

import com.aurora.gplayapi.data.models.File
import java.util.Locale


sealed class Link {
    data object Empty: Link()
    data class Url(
        val link: String,
        val size: Long = 0L,
        val expectedPackageName: String? = null,
        val sha256: String? = null
    ): Link()
    data class BrowserDownload(
        val pageUrl: String,
        val expectedPackageName: String? = null,
        val sha256: String? = null,
        val suggestedFileName: String? = null,
        val isXapk: Boolean = false
    ): Link()
    data class Xapk(val link: String): Link()
    data class Play(val getInstallFiles: () -> List<File>): Link()
}

fun Link.Url.hasValidationMetadata(): Boolean =
    !expectedPackageName.isNullOrBlank() || !sha256.isNullOrBlank()

fun Link.Url.validationPackageName(fallbackPackageName: String): String? =
    if (hasValidationMetadata()) {
        expectedPackageName?.takeIf { it.isNotBlank() } ?: fallbackPackageName
    } else {
        null
    }

fun Link.Url.validationSize(): Long =
    if (hasValidationMetadata()) size else 0L

fun Link.Url.totalSize(contentLength: Long, fallbackSize: Long = 0L): Long = when {
    size > 0L -> size
    contentLength > 0L -> contentLength
    fallbackSize > 0L -> fallbackSize
    else -> 0L
}

fun String.isSplitPackageDownloadUrl(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.contains(".xapk") || lower.contains(".apks") || lower.contains(".apkm")
}
