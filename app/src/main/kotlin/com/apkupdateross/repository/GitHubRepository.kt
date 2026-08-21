package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.github.GitHubApp
import com.apkupdateross.data.github.GitHubApps
import com.apkupdateross.data.github.GitHubRelease
import com.apkupdateross.data.github.GitHubReleaseAsset
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GitHubSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.GitHubService
import com.apkupdateross.util.AbiMatcher
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.combine
import com.apkupdateross.util.filterVersionTag
import com.apkupdateross.util.versionCodeFromTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.util.Scanner
import java.util.concurrent.atomic.AtomicBoolean


class GitHubRepository(
    private val service: GitHubService,
    private val prefs: Prefs,
    private val snackBar: SnackBar,
    private val stringer: Stringer
) {
    private val rateLimitShown = AtomicBoolean(false)

    private fun authHeader(): String? = prefs.githubToken.get().trim().takeIf { it.isNotEmpty() }?.let { "token $it" }

    suspend fun updates(apps: List<AppInstalled>) = flow {
        rateLimitShown.set(false)
        val checks = mutableListOf(selfCheck())

        loadGitHubApps().forEachIndexed { i, app ->
            if (i != 0) {
                apps.find { it.packageName == app.packageName }?.let {
                    checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, app.extra))
                }
            }
        }

        checks.combine { all ->
            emit(all.flatMap { it })
        }.collect()
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        loadGitHubApps().forEach { app ->
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
        Log.e("GitHubRepository", "Error searching.", it)
    }

    private fun selfCheck() = flow {
        val releases = service.getReleases(authHeader = authHeader()).filter { filterPreRelease(it) }
        val release = releases.firstOrNull()
        val assetUrl = release?.let { findApkAsset(it.assets) }?.takeIf { it.isNotEmpty() }

        if (release != null && assetUrl != null) {
            val raw = release.tag_name.takeIf { it.isNotBlank() } ?: release.name
            val versionName = raw.removePrefix("v").trim()
            val versionCode = raw.versionCodeFromTag()
            val remoteVersion = Version(versionName)
            val localVersion = Version(BuildConfig.VERSION_NAME)
            val isNewer = remoteVersion > localVersion
            if (isNewer) {
                val author = release.author?.login ?: "EikeiDev"
                val sourceUrl = "https://github.com/$author/apkupdateross"
                emit(listOf(AppUpdate(
                    name = "APKUpdater",
                    packageName = BuildConfig.APPLICATION_ID,
                    version = versionName,
                    oldVersion = BuildConfig.VERSION_NAME,
                    versionCode = versionCode,
                    oldVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    source = GitHubSource,
                    link = Link.Url(assetUrl),
                    sourceUrl = sourceUrl,
                    releaseUrl = "$sourceUrl/releases/tag/${release.tag_name}",
                    whatsNew = release.body,
                    releaseType = ReleaseType.from(raw, release.name, release.prerelease)
                )))
            } else {
                emit(listOf())
            }
        } else {
            emit(listOf())
        }
    }.catch { e ->
        if (e is HttpException && e.code() == 403 &&
            e.response()?.headers()?.get("X-RateLimit-Remaining") == "0" &&
            !rateLimitShown.getAndSet(true)
        ) {
            snackBar.snackBar(message = TextSnack(stringer.get(R.string.github_rate_limit_exceeded)))
            throw e
        } else if (e is HttpException && e.code() == 404) {
            emit(emptyList())
            Log.e("GitHubRepository", "Self-update repo not found.", e)
        } else {
            throw e
        }
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val r = service.getReleases(user, repo, authHeader())
        val releases = if (packageName == "com.apkupdateross.ci") {
            // TODO: Find a better way to do this
            r.filter { it.name.contains("CI-Release-3.x")}
        } else {
            r.filter { filterPreRelease(it) }.filter { findApkAssetArch(it.assets, extra) != null }
        }

        if (releases.isNotEmpty() && Version(filterVersionTag(releases[0].tag_name)) > Version(currentVersion)) {
            val release = releases[0]
            val app = apps?.getApp(packageName)
            val apkAsset = findApkAssetArch(release.assets, extra) ?: return@flow emit(emptyList())
            emit(listOf(AppUpdate(
                name = repo,
                packageName = packageName,
                version = release.tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = release.tag_name.takeIf { it.isNotBlank() }?.versionCodeFromTag() ?: release.name.versionCodeFromTag(),
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitHubSource,
                link = Link.Url(apkAsset.browser_download_url, apkAsset.size),
                whatsNew = release.body,
                releaseType = ReleaseType.from(release.tag_name, release.name, release.prerelease),
                iconUri = if (apps == null) Uri.parse(release.author.avatar_url) else Uri.EMPTY,
                sourceUrl = "https://github.com/$user/$repo",
                releaseUrl = "https://github.com/$user/$repo/releases/tag/${release.tag_name}"
            )))
        } else {
            emit(emptyList())
        }
    }.catch { e ->
        if (e is HttpException && e.code() == 403 &&
            e.response()?.headers()?.get("X-RateLimit-Remaining") == "0" &&
            !rateLimitShown.getAndSet(true)
        ) {
            snackBar.snackBar(message = TextSnack(stringer.get(R.string.github_rate_limit_exceeded)))
            throw e
        } else if (e is HttpException && e.code() == 404) {
            emit(emptyList())
            Log.e("GitHubRepository", "Repo not found for $packageName.", e)
        } else {
            throw e
        }
    }

    private fun filterPreRelease(release: GitHubRelease) = when {
        prefs.ignorePreRelease.get() && release.prerelease -> false
        else -> true
    }

    private fun findApkAsset(assets: List<GitHubReleaseAsset>) = assets
        .filter { it.browser_download_url.endsWith(".apk", true) }
        .let {
            AbiMatcher.selectCompatible(
                items = it,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                nameSelector = GitHubReleaseAsset::browser_download_url,
                sizeSelector = GitHubReleaseAsset::size
            )
        }
        ?.browser_download_url
        .orEmpty()

    private fun findApkAssetArch(
        assets: List<GitHubReleaseAsset>,
        extra: Regex?
    ): GitHubReleaseAsset? {
        val apks = assets
            .filter { it.browser_download_url.endsWith(".apk", true) }
            .filter { filterExtra(it, extra) }

        return AbiMatcher.selectCompatible(
            items = apks,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            nameSelector = GitHubReleaseAsset::browser_download_url,
            sizeSelector = GitHubReleaseAsset::size
        )
    }

    private fun filterExtra(asset: GitHubReleaseAsset, extra: Regex?) = when(extra) {
        null -> true
        else -> asset.browser_download_url.matches(extra)
    }

    private fun loadGitHubApps(): List<GitHubApp> {
        val custom = prefs.customGitRepos.get()
            .filter { it.platform == GitProvider.GITHUB }
            .mapNotNull { it.toGitHubAppOrNull() }
        return GitHubApps + custom
    }

    private fun CustomGitRepo.toGitHubAppOrNull(): GitHubApp? {
        val data = trimmed()
        if (data.user.isEmpty() || data.repo.isEmpty() || data.packageName.isEmpty()) return null
        val regex = data.extraRegex?.let {
            runCatching { Regex(it) }.onFailure { err ->
                Log.w("GitHubRepository", "Invalid regex for ${data.repo}: $it", err)
            }.getOrNull()
        }
        return GitHubApp(
            packageName = data.packageName,
            user = data.user,
            repo = data.repo,
            extra = regex
        )
    }
}
