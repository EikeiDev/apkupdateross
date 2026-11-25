package com.apkupdateross.util

import java.net.URI
import java.util.Locale

class ApkMirrorSession {
    data class Snapshot(
        val cookies: String,
        val userAgent: String?
    )

    @Volatile
    private var cookies: String = ""

    @Volatile
    private var userAgent: String? = null

    @Synchronized
    fun update(cookieHeader: String, verifiedUserAgent: String) {
        cookies = mergeCookieHeaders(cookies, cookieHeader)
        userAgent = verifiedUserAgent.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun clearClearance() {
        cookies = cookies
            .split(';')
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("cf_clearance=", ignoreCase = true) }
            .joinToString("; ")
    }

    fun snapshot(): Snapshot = Snapshot(cookies, userAgent)

    fun userAgentOr(fallback: String): String = userAgent ?: fallback

    fun cookieHeaderFor(url: String): String? = cookies
        .takeIf { it.isNotBlank() && isApkMirrorUrl(url) }
}

internal fun mergeCookieHeaders(vararg headers: String?): String {
    val cookies = linkedMapOf<String, String>()
    headers.forEach { header ->
        header.orEmpty().split(';').forEach { rawCookie ->
            val cookie = rawCookie.trim()
            val separator = cookie.indexOf('=')
            if (separator > 0) {
                cookies[cookie.substring(0, separator).lowercase(Locale.ROOT)] = cookie
            }
        }
    }
    return cookies.values.joinToString("; ")
}

internal fun isApkMirrorUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    uri.scheme.equals("https", ignoreCase = true) &&
        (host == "apkmirror.com" || host.endsWith(".apkmirror.com"))
}.getOrDefault(false)

internal fun isLikelyApkMirrorDownloadUrl(url: String): Boolean {
    if (!isApkMirrorUrl(url)) return false
    val normalized = url.lowercase(Locale.ROOT).substringBefore('#')
    val path = normalized.substringBefore('?')
    return path.endsWith(".apk") ||
        path.endsWith(".apkm") ||
        path.endsWith(".apks") ||
        path.endsWith(".xapk") ||
        normalized.contains("/wp-content/themes/apkmirror/download.php")
}

internal fun extractCookieValue(cookieHeader: String?, name: String): String? {
    if (cookieHeader.isNullOrBlank()) return null
    val target = name.lowercase(Locale.ROOT)
    var found: String? = null
    cookieHeader.split(';').forEach { raw ->
        val trimmed = raw.trim()
        val sep = trimmed.indexOf('=')
        if (sep > 0 && trimmed.substring(0, sep).trim().lowercase(Locale.ROOT) == target) {
            val value = trimmed.substring(sep + 1).trim()
            found = value.takeIf { it.isNotEmpty() }
        }
    }
    return found
}

internal fun hasFreshClearanceCookie(cookieHeader: String?, previousClearance: String?): Boolean {
    val current = extractCookieValue(cookieHeader, "cf_clearance") ?: return false
    if (current.length < 8) return false
    return previousClearance == null || current != previousClearance
}

internal fun isCloudflareChallengePage(url: String?, title: String?): Boolean {
    val lowerUrl = url.orEmpty().lowercase(Locale.ROOT)
    if (lowerUrl.contains("__cf_chl") || lowerUrl.contains("/cdn-cgi/challenge-platform")) {
        return true
    }
    val lowerTitle = title.orEmpty().trim().lowercase(Locale.ROOT)
    if (lowerTitle.isEmpty()) return true
    return lowerTitle.contains("just a moment") ||
        lowerTitle.contains("attention required") ||
        lowerTitle.contains("checking your browser") ||
        lowerTitle.contains("one moment") ||
        lowerTitle.contains("один момент") ||
        lowerTitle.contains("подождите") ||
        lowerTitle.contains("cloudflare")
}
