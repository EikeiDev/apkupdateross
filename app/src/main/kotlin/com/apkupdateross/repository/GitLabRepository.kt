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
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.GitLabService
import com.apkupdateross.util.combine
import com.apkupdateross.util.filterVersionTag
import com.apkupdateross.util.versionCodeFromTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow


class GitLabRepository(
    private val service: GitLabService,
    private val prefs: Prefs
) {

    private fun authHeader(): String? = prefs.gitlabToken.get().trim().takeIf { it.isNotEmpty() }

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
            checks.combine { all -> emit(all.flatMap { it }) }.collect()
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
            .filter { Version(filterVersionTag(it.tag_name)) > Version(currentVersion) }

        if (releases.isNotEmpty()) {
            val app = apps?.getApp(packageName)
            emit(listOf(
                AppUpdate(
                name = repo,
                packageName = packageName,
                version = releases[0].tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = releases[0].tag_name.versionCodeFromTag(),
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitLabSource,
                link = Link.Url(getApkUrl(packageName, releases[0])),
                whatsNew = releases[0].description,
                iconUri = if (apps == null) Uri.parse(releases[0].author.avatar_url) else Uri.EMPTY,
                sourceUrl = "https://gitlab.com/$user/$repo",
                releaseUrl = "https://gitlab.com/$user/$repo/-/releases/${releases[0].tag_name}"
            )))
        } else {
            emit(emptyList())
        }
    }.catch {
        emit(emptyList())
        Log.e("GitLabRepository", "Error fetching releases for $packageName.", it)
    }

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
            checks.combine { all ->
                val r = all.flatMap { it }
                emit(Result.success(r))
            }.collect()
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("GitLabRepository", "Error searching.", it)
    }

    private fun getApkUrl(
        packageName: String,
        release: GitLabRelease
    ): String {
        val apks = (release.assets.sources.map { it.url } + release.assets.links.map { it.url })
            .filter { it.endsWith(".apk", true) }

        if (apks.isEmpty()) return ""
        if (apks.size == 1) return apks.first()

        Build.SUPPORTED_ABIS.forEach { arch ->
            apks.find { it.contains(arch, true) }?.let { return it }
        }
        if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            apks.find { it.contains("arm64", true) }?.let { return it }
        }
        if (Build.SUPPORTED_ABIS.contains("x86_64")) {
            apks.find { it.contains("x64", true) }?.let { return it }
        }
        if (Build.SUPPORTED_ABIS.contains("armeabi-v7a")) {
            apks.find { it.contains("arm", true) }?.let { return it }
        }
        return apks.maxByOrNull { it.length } ?: apks.first()
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
