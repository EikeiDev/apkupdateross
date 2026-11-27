package com.apkupdateross.util

import com.apkupdateross.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

class FourPdaSession(
    private val prefs: Prefs
) {
    private val _loggedInUserId = MutableStateFlow(readValidUserId())
    val loggedInUserId: StateFlow<String> = _loggedInUserId.asStateFlow()

    private fun readValidUserId(): String {
        val cookies = prefs.fourPdaCookies.get()
        if (!hasValidFourPdaSession(cookies)) return ""
        return prefs.fourPdaUserId.get().ifBlank { extractFourPdaMemberId(cookies).orEmpty() }
    }

    fun isLoggedIn(): Boolean = hasValidFourPdaSession(prefs.fourPdaCookies.get())

    fun saveSession(cookies: String, memberId: String? = null) {
        val cleanCookies = cookies.trim()
        val resolvedId = memberId?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
            ?: extractFourPdaMemberId(cleanCookies).orEmpty()
        prefs.fourPdaCookies.put(cleanCookies)
        prefs.fourPdaUserId.put(resolvedId)
        _loggedInUserId.value = if (hasValidFourPdaSession(cleanCookies)) resolvedId else ""
    }

    fun clear() {
        prefs.fourPdaCookies.put("")
        prefs.fourPdaUserId.put("")
        _loggedInUserId.value = ""
    }

    fun cookieHeaderFor(url: String): String? {
        if (!isFourPdaUrl(url)) return null
        val stored = prefs.fourPdaCookies.get().trim()
        return stored.takeIf { hasValidFourPdaSession(it) }
    }
}

internal fun isFourPdaUrl(url: String): Boolean {
    val parsed = url.trim().toHttpUrlOrNull() ?: return false
    if (!parsed.isHttps) return false
    val host = parsed.host.lowercase(Locale.ROOT)
    return host == "4pda.to" || host.endsWith(".4pda.to") ||
        host == "4pda.ru" || host.endsWith(".4pda.ru")
}

internal fun extractFourPdaMemberId(cookieHeader: String?): String? {
    val value = extractCookieValue(cookieHeader, "member_id")?.trim() ?: return null
    return value.takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
}

internal fun hasValidFourPdaSession(cookieHeader: String?): Boolean {
    val memberId = extractFourPdaMemberId(cookieHeader) ?: return false
    val passHash = extractCookieValue(cookieHeader, "pass_hash")?.trim().orEmpty()
    return memberId.isNotEmpty() &&
        passHash.length >= 8 &&
        !passHash.equals("deleted", ignoreCase = true)
}
