package com.apkupdateross.data.git

import java.util.UUID

enum class GitProvider {
    GITHUB,
    GITLAB
}

data class CustomGitRepo(
    val platform: GitProvider = GitProvider.GITHUB,
    val user: String = "",
    val repo: String = "",
    val packageName: String = "",
    val extraRegex: String? = null,
    val id: String = UUID.randomUUID().toString()
) {
    fun trimmed(): CustomGitRepo = copy(
        user = user.trim(),
        repo = repo.trim(),
        packageName = packageName.trim(),
        extraRegex = extraRegex?.trim()?.ifEmpty { null }
    )
}

data class ParsedGitUrl(
    val provider: GitProvider,
    val user: String,
    val repo: String
)

fun parseRepoUrl(url: String): ParsedGitUrl? {
    val trimmed = url.trim()
    val match = Regex("https?://(www\\.)?(github|gitlab)\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)").find(trimmed)
    return match?.let {
        val provider = if (it.groupValues[2].equals("github", true)) GitProvider.GITHUB else GitProvider.GITLAB
        ParsedGitUrl(provider, it.groupValues[3], it.groupValues[4])
    }
}
