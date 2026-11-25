package com.apkupdateross.data.fdroid

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

data class FdroidRepo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false
)

fun normalizeFdroidRepoUrl(rawUrl: String): String? {
    val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return null
    if (!parsed.isHttps || parsed.query != null || parsed.fragment != null) return null
    return parsed.toString().trimEnd('/') + "/"
}
