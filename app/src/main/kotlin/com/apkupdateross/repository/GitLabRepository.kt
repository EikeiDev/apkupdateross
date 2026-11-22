package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.gitlab.GitLabApp
import com.apkupdateross.data.gitlab.GitLabApps
import com.apkupdateross.data.gitlab.GitLabRelease
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GitLabSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.GitLabService
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


class GitLabRepository(
    private val service: GitLabService,
    private val prefs: Prefs
) {

    private companion object {
        const val MAX_GIT_CONCURRENCY = 6
    }

    private fun authHeader(): String? = prefs.gitlabToken.get().trim().takeIf { it.isNotEmpty() }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()
        loadGitLabApps().forEach { app ->
            apps.find { it.packageName == app.packageName }?.let {
                checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, null))
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

    private suspend fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val releases = service.getReleases(user, repo, authHeader())
            .filter { shouldInclude(ReleaseType.from(it.tag_name)) }
            .filter { Version(filterVersionTag(it.tag_name)) > Version(currentVersion) }

        if (releases.isNotEmpty()) {
            val release = releases[0]
            val app = apps?.getApp(packageName)
            val apkUrl = getApkUrl(release) ?: return@flow emit(emptyList())
            emit(listOf(
                AppUpdate(
                name = repo,
                packageName = packageName,
                version = release.tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = release.tag_name.versionCodeFromTag(),
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitLabSource,
                link = Link.Url(apkUrl),
                whatsNew = release.description,
                releaseType = ReleaseType.from(release.tag_name),
                iconUri = if (apps == null) Uri.parse(release.author.avatar_url) else Uri.EMPTY,
                sourceUrl = "https://gitlab.com/$user/$repo",
                releaseUrl = "https://gitlab.com/$user/$repo/-/releases/${release.tag_name}"
            )))
        } else {
            emit(emptyList())
        }
    }.catch { e ->
        if (e is HttpException && e.code() == 404) {
            emit(emptyList())
            Log.e("GitLabRepository", "Repo not found for $packageName.", e)
        } else {
            throw e
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        loadGitLabApps().forEach { app ->
            if (app.repo.contains(text, true) || app.user.contains(text, true) || app.packageName.contains(text, true)) {
                checks.add(checkApp(null, app.user, app.repo, app.packageName, "?", null))
            }
        }

        if (checks.isEmpty()) {
            emit(Result.success(emptyList()))
        } else {
            checks.asFlow().flattenMerge(MAX_GIT_CONCURRENCY).collect {
                emit(Result.success(it))
            }
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("GitLabRepository", "Error searching.", it)
    }

    private fun getApkUrl(release: GitLabRelease): String? {
        val apks = (release.assets.sources.map { it.url } + release.assets.links.map { it.url })
            .filter { it.endsWith(".apk", true) }

        return AbiMatcher.selectCompatible(
            items = apks,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            nameSelector = { it },
            sizeSelector = { it.length.toLong() }
        )
    }

    private fun shouldInclude(releaseType: ReleaseType): Boolean = when (releaseType) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    private fun loadGitLabApps(): List<GitLabApp> {
        val custom = prefs.customGitRepos.get()
            .filter { it.platform == GitProvider.GITLAB }
            .mapNotNull { it.toGitLabAppOrNull() }
        return GitLabApps + custom
    }

    private fun CustomGitRepo.toGitLabAppOrNull(): GitLabApp? {
        val data = trimmed()
        if (data.user.isEmpty() || data.repo.isEmpty() || data.packageName.isEmpty()) return null
        return GitLabApp(
            packageName = data.packageName,
            user = data.user,
            repo = data.repo
        )
    }

}
