package com.apkupdateross.data.ui

import com.aurora.gplayapi.model.AppFile
import java.net.URI
import java.util.Locale


sealed class Link {
    data object Empty: Link()
    data class Url(
        val link: String,
        val size: Long = 0L,
        val expectedPackageName: String? = null,
        val sha256: String? = null,
        val resolveUrl: (suspend () -> String)? = null,
        val requestHeaders: Map<String, String> = emptyMap()
    ): Link()
    data class BrowserDownload(
        val pageUrl: String,
        val expectedPackageName: String? = null,
        val sha256: String? = null,
        val suggestedFileName: String? = null,
        val isXapk: Boolean = false
    ): Link()
    data class Xapk(val link: String): Link()
    data class Play(val getInstallFiles: suspend () -> List<AppFile>): Link()
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
    if (!sha256.isNullOrBlank()) size else 0L

suspend fun Link.Url.resolvedUrl(): String =
    resolveUrl?.invoke()?.takeIf { it.isNotBlank() } ?: link

fun Link.Url.totalSize(contentLength: Long, fallbackSize: Long = 0L): Long = when {
    size > 0L -> size
    contentLength > 0L -> contentLength
    fallbackSize > 0L -> fallbackSize
    else -> 0L
}

fun String.isSplitPackageDownloadUrl(): Boolean {
    val normalized = trim()
    val path = runCatching { URI(normalized).path }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: normalized.substringBefore('?').substringBefore('#')
    val lowerPath = path.trimEnd('/').lowercase(Locale.ROOT)
    return SPLIT_PACKAGE_EXTENSIONS.any(lowerPath::endsWith)
}

fun String.isApkDownloadUrl(): Boolean {
    val normalized = trim()
    val path = runCatching { URI(normalized).path }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: normalized.substringBefore('?').substringBefore('#')
    return path.trimEnd('/').endsWith(".apk", ignoreCase = true)
}

private val SPLIT_PACKAGE_EXTENSIONS = listOf(".xapk", ".apks", ".apkm")
