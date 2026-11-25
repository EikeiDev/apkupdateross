package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.github.GitHubRelease
import com.apkupdateross.data.github.GitHubReleaseAsset
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.CodebergSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.data.ui.isApkDownloadUrl
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.CodebergService
import com.apkupdateross.util.AbiMatcher
import com.apkupdateross.util.filterVersionTag
import com.apkupdateross.util.versionCodeFromTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.net.URI

class CodebergRepository(
    private val service: CodebergService,
    private val prefs: Prefs
) {
    private companion object {
        const val MAX_GIT_CONCURRENCY = 6
    }

    private data class CodebergApp(
        val packageName: String,
        val user: String,
        val repo: String,
        val extra: Regex?
    )

    private fun authHeader(): String? = prefs.codebergToken.get()
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "token $it" }

    private fun downloadHeaders(url: String): Map<String, String> {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host != "codeberg.org" && !host.endsWith(".codeberg.org")) return emptyMap()
        return authHeader()?.let { mapOf("Authorization" to it) }.orEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = loadApps().mapNotNull { app ->
            apps.find { it.packageName == app.packageName }?.let { installed ->
                checkApp(apps, app, installed.version)
            }
        }
        if (checks.isEmpty()) {
            emit(emptyList())
        } else {
            val updates = mutableListOf<AppUpdate>()
            checks.asFlow().flattenMerge(MAX_GIT_CONCURRENCY).collect { updates.addAll(it) }
            emit(updates)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun search(text: String) = flow {
        val checks = loadApps()
            .filter { app ->
                app.repo.contains(text, ignoreCase = true) ||
                    app.user.contains(text, ignoreCase = true) ||
                    app.packageName.contains(text, ignoreCase = true)
            }
            .map { app -> checkApp(null, app, null) }

        if (checks.isEmpty()) {
            emit(Result.success(emptyList()))
        } else {
            checks.asFlow().flattenMerge(MAX_GIT_CONCURRENCY).collect { updates ->
                emit(Result.success(updates))
            }
        }
    }.catch { error ->
        emit(Result.failure(error))
        Log.e("CodebergRepository", "Error searching.", error)
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        app: CodebergApp,
        currentVersion: String?
    ): Flow<List<AppUpdate>> = flow {
        val candidate = service.getReleases(app.user, app.repo, authHeader())
            .asSequence()
            .filterNot { it.draft }
            .filter { shouldInclude(ReleaseType.from(it.tag_name, it.name, it.prerelease)) }
            .mapNotNull { release -> selectApk(release.assets, app.extra)?.let { release to it } }
            .firstOrNull()
            ?: return@flow emit(emptyList())

        val (release, apk) = candidate
        val rawVersion = release.tag_name.ifBlank { release.name }
        val normalizedVersion = filterVersionTag(rawVersion)
        if (currentVersion != null && Version(normalizedVersion) <= Version(currentVersion)) {
            return@flow emit(emptyList())
        }

        val installed = apps?.getApp(app.packageName)
        val sourceUrl = "https://codeberg.org/${app.user}/${app.repo}"
        emit(
            listOf(
                AppUpdate(
                    name = app.repo,
                    packageName = app.packageName,
                    version = rawVersion,
                    oldVersion = installed?.version ?: "?",
                    versionCode = rawVersion.versionCodeFromTag(),
                    oldVersionCode = installed?.versionCode ?: 0L,
                    source = CodebergSource,
                    iconUri = if (apps == null) Uri.parse(release.author.avatar_url) else Uri.EMPTY,
                    link = Link.Url(
                        link = apk.browser_download_url,
                        size = apk.size,
                        expectedPackageName = app.packageName,
                        requestHeaders = downloadHeaders(apk.browser_download_url)
                    ),
                    sourceUrl = sourceUrl,
                    releaseUrl = "$sourceUrl/releases/tag/${Uri.encode(release.tag_name)}",
                    whatsNew = release.body,
                    releaseType = ReleaseType.from(release.tag_name, release.name, release.prerelease)
                )
            )
        )
    }.catch { error ->
        if (error is HttpException && error.code() == 404) {
            Log.w("CodebergRepository", "Repo not found: ${app.user}/${app.repo}")
            emit(emptyList())
        } else {
            throw error
        }
    }

    private fun selectApk(assets: List<GitHubReleaseAsset>, extra: Regex?): GitHubReleaseAsset? {
        val apks = assets
            .filter { it.browser_download_url.isApkDownloadUrl() }
            .filter { extra == null || it.browser_download_url.matches(extra) }
        return AbiMatcher.selectCompatible(
            items = apks,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            nameSelector = GitHubReleaseAsset::browser_download_url,
            sizeSelector = GitHubReleaseAsset::size
        )
    }

    private fun shouldInclude(releaseType: ReleaseType): Boolean = when (releaseType) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private fun loadApps(): List<CodebergApp> = prefs.customGitRepos.get()
        .asSequence()
        .filter { it.platform == GitProvider.CODEBERG }
        .mapNotNull { it.toCodebergAppOrNull() }
        .toList()

    private fun CustomGitRepo.toCodebergAppOrNull(): CodebergApp? {
        val data = trimmed()
        if (data.user.isEmpty() || data.repo.isEmpty() || data.packageName.isEmpty()) return null
        val regex = data.extraRegex?.let { pattern ->
            runCatching { Regex(pattern) }.onFailure { error ->
                Log.w("CodebergRepository", "Invalid regex for ${data.repo}: $pattern", error)
            }.getOrNull()
        }
        return CodebergApp(data.packageName, data.user, data.repo, regex)
    }
}
