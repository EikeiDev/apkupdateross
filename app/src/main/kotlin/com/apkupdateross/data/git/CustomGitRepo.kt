package com.apkupdateross.data.git

import java.net.URI
import java.util.UUID

enum class GitProvider {
    CODEBERG,
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
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val provider = when (uri.host?.lowercase()) {
        "codeberg.org", "www.codeberg.org" -> GitProvider.CODEBERG
        "github.com", "www.github.com" -> GitProvider.GITHUB
        "gitlab.com", "www.gitlab.com" -> GitProvider.GITLAB
        else -> return null
    }
    val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
    if (segments.size < 2) return null
    val projectSegments = if (provider == GitProvider.GITLAB) {
        segments.takeWhile { it != "-" }
    } else {
        segments.take(2)
    }
    if (projectSegments.size < 2) return null
    val user = projectSegments.dropLast(1).joinToString("/")
    val repo = projectSegments.last().removeSuffix(".git")
    val validSegment = Regex("[A-Za-z0-9_.-]+")
    if (user.split('/').any { !validSegment.matches(it) } || !validSegment.matches(repo)) return null
    return ParsedGitUrl(provider, user, repo)
}
