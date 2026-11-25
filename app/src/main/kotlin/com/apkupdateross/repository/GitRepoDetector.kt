package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.git.parseRepoUrl
import com.apkupdateross.data.github.GitHubRelease
import com.apkupdateross.data.github.GitHubReleaseAsset
import com.apkupdateross.data.gitlab.GitLabRelease
import com.apkupdateross.data.ui.isApkDownloadUrl
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.CodebergService
import com.apkupdateross.service.GitHubService
import com.apkupdateross.service.GitLabService
import com.apkupdateross.util.AbiMatcher
import com.apkupdateross.util.ApkArchiveInspector
import com.apkupdateross.util.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

data class DetectedGitRepo(
    val provider: GitProvider,
    val user: String,
    val repo: String,
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val assetName: String,
    val extraRegex: String?
)

enum class GitRepoDetectionFailure {
    INVALID_URL,
    REPOSITORY_UNAVAILABLE,
    NO_RELEASES,
    NO_COMPATIBLE_APK,
    DOWNLOAD_FAILED,
    INVALID_APK
}

sealed interface GitRepoDetectionResult {
    data class Success(val repo: DetectedGitRepo) : GitRepoDetectionResult
    data class Failure(val reason: GitRepoDetectionFailure) : GitRepoDetectionResult
}

class GitRepoDetector(
    private val gitHubService: GitHubService,
    private val gitLabService: GitLabService,
    private val codebergService: CodebergService,
    private val prefs: Prefs,
    private val downloader: Downloader,
    private val archiveInspector: ApkArchiveInspector
) {
    private data class ApkCandidate(
        val url: String,
        val displayName: String,
        val rawFileName: String,
        val size: Long,
        val releaseTag: String,
        val siblingApkCount: Int
    )

    suspend fun detect(url: String): GitRepoDetectionResult = withContext(Dispatchers.IO) {
        val parsed = parseRepoUrl(url)
            ?: return@withContext GitRepoDetectionResult.Failure(GitRepoDetectionFailure.INVALID_URL)

        val candidate = try {
            when (parsed.provider) {
                GitProvider.GITHUB -> {
                    val releases = gitHubService.getReleases(parsed.user, parsed.repo, gitHubAuthHeader())
                    if (releases.isEmpty()) return@withContext noReleases()
                    selectGitHubCandidate(releases)
                }
                GitProvider.CODEBERG -> {
                    val releases = codebergService.getReleases(parsed.user, parsed.repo, codebergAuthHeader())
                    if (releases.isEmpty()) return@withContext noReleases()
                    selectGitHubCandidate(releases)
                }
                GitProvider.GITLAB -> {
                    val releases = gitLabService.getReleases(parsed.user, parsed.repo, gitLabAuthHeader())
                    if (releases.isEmpty()) return@withContext noReleases()
                    selectGitLabCandidate(releases)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return@withContext GitRepoDetectionResult.Failure(GitRepoDetectionFailure.REPOSITORY_UNAVAILABLE)
        } ?: return@withContext GitRepoDetectionResult.Failure(GitRepoDetectionFailure.NO_COMPATIBLE_APK)

        val downloadId = ("git-repo:${parsed.provider}:${parsed.user}/${parsed.repo}").hashCode() xor
            System.nanoTime().toInt()
        try {
            val downloaded = downloader.downloadFileWithSize(
                id = downloadId,
                url = candidate.url,
                requestHeaders = downloadHeaders(parsed.provider, candidate.url)
            ) ?: return@withContext GitRepoDetectionResult.Failure(GitRepoDetectionFailure.DOWNLOAD_FAILED)

            val metadata = archiveInspector.inspect(downloaded.file)
                ?: return@withContext GitRepoDetectionResult.Failure(GitRepoDetectionFailure.INVALID_APK)

            GitRepoDetectionResult.Success(
                DetectedGitRepo(
                    provider = parsed.provider,
                    user = parsed.user,
                    repo = parsed.repo,
                    packageName = metadata.packageName,
                    appName = metadata.appName,
                    versionName = metadata.versionName,
                    assetName = candidate.displayName,
                    extraRegex = suggestedAssetRegex(candidate)
                )
            )
        } finally {
            downloader.cleanup(downloadId)
        }
    }

    private fun noReleases() = GitRepoDetectionResult.Failure(GitRepoDetectionFailure.NO_RELEASES)

    private fun selectGitHubCandidate(releases: List<GitHubRelease>): ApkCandidate? {
        releases.filterNot { it.draft }.forEach { release ->
            val apks = release.assets.filter { it.browser_download_url.isApkDownloadUrl() }
            val preferredApks = preferProductionAssets(apks) {
                it.name.ifBlank { assetFileName(it.browser_download_url) }
            }
            val selected = AbiMatcher.selectCompatible(
                items = preferredApks,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                nameSelector = { it.name.ifBlank { it.browser_download_url } },
                sizeSelector = GitHubReleaseAsset::size
            ) ?: return@forEach
            val rawName = rawFileName(selected.browser_download_url)
            return ApkCandidate(
                url = selected.browser_download_url,
                displayName = selected.name.ifBlank { Uri.decode(rawName) },
                rawFileName = rawName,
                size = selected.size,
                releaseTag = release.tag_name,
                siblingApkCount = apks.size
            )
        }
        return null
    }

    private fun selectGitLabCandidate(releases: List<GitLabRelease>): ApkCandidate? {
        releases.forEach { release ->
            val candidates = buildList {
                release.assets.links.forEach { link ->
                    val url = link.direct_asset_url?.takeIf { it.isNotBlank() } ?: link.url
                    if (url.isApkDownloadUrl()) add(Triple(url, link.name, 0L))
                }
                release.assets.sources.forEach { source ->
                    if (source.url.isApkDownloadUrl()) add(Triple(source.url, source.format, 0L))
                }
            }
            val preferredCandidates = preferProductionAssets(candidates) { (url, name) ->
                name.ifBlank { assetFileName(url) }
            }
            val selected = AbiMatcher.selectCompatible(
                items = preferredCandidates,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                nameSelector = { (url, name) -> name.ifBlank { url } },
                sizeSelector = { it.third }
            ) ?: return@forEach
            val rawName = rawFileName(selected.first)
            return ApkCandidate(
                url = selected.first,
                displayName = selected.second.ifBlank { Uri.decode(rawName) },
                rawFileName = rawName,
                size = selected.third,
                releaseTag = release.tag_name,
                siblingApkCount = candidates.size
            )
        }
        return null
    }

    private fun suggestedAssetRegex(candidate: ApkCandidate): String? {
        if (candidate.siblingApkCount <= 1) return null

        val marker = "__APKUPDATER_VERSION__"
        var template = candidate.rawFileName
        val versionTokens = listOf(
            candidate.releaseTag,
            candidate.releaseTag.removePrefix("v").removePrefix("V"),
            candidate.releaseTag.replace('.', '-'),
            candidate.releaseTag.replace('.', '_')
        ).filter { it.length >= 2 }.distinct().sortedByDescending(String::length)

        versionTokens.forEach { token ->
            template = template.replace(Regex(Regex.escape(token), RegexOption.IGNORE_CASE), marker)
        }
        if (!template.contains(marker)) {
            template = template.replaceFirst(
                Regex("\\d+(?:[._-]\\d+){1,4}(?:[._-]?(?:alpha|beta|rc)\\d*)?", RegexOption.IGNORE_CASE),
                marker
            )
        }

        val filePattern = template.split(marker)
            .joinToString("[^/?]+") { Regex.escape(it) }
        return ".*/$filePattern(?:\\?.*)?"
    }

    private fun rawFileName(url: String): String = url.substringAfterLast('/').substringBefore('?')

    private fun assetFileName(url: String): String = Uri.decode(rawFileName(url))

    private fun <T> preferProductionAssets(items: List<T>, name: (T) -> String): List<T> {
        val production = items.filterNot { item ->
            DISCOURAGED_ASSET_PATTERN.containsMatchIn(name(item))
        }
        return production.ifEmpty { items }
    }

    private fun gitHubAuthHeader(): String? = prefs.githubToken.get().trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "token $it" }

    private fun codebergAuthHeader(): String? = prefs.codebergToken.get().trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "token $it" }

    private fun gitLabAuthHeader(): String? = prefs.gitlabToken.get().trim().takeIf { it.isNotEmpty() }

    private fun downloadHeaders(provider: GitProvider, url: String): Map<String, String> {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        return when (provider) {
            GitProvider.GITHUB -> gitHubAuthHeader()
                ?.takeIf { host == "github.com" || host.endsWith(".github.com") }
                ?.let { mapOf("Authorization" to it) }
                .orEmpty()
            GitProvider.CODEBERG -> codebergAuthHeader()
                ?.takeIf { host == "codeberg.org" || host.endsWith(".codeberg.org") }
                ?.let { mapOf("Authorization" to it) }
                .orEmpty()
            GitProvider.GITLAB -> gitLabAuthHeader()
                ?.takeIf { host == "gitlab.com" || host.endsWith(".gitlab.com") }
                ?.let { mapOf("Authorization" to "Bearer $it") }
                .orEmpty()
        }
    }

    private companion object {
        val DISCOURAGED_ASSET_PATTERN = Regex(
            "(^|[._-])(debug|unsigned|unaligned|test|benchmark)([._-]|$)",
            RegexOption.IGNORE_CASE
        )
    }
}
