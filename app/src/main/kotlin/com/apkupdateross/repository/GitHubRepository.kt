package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.BuildConfig
import com.apkupdateross.data.github.GitHubApps
import com.apkupdateross.data.github.GitHubRelease
import com.apkupdateross.data.github.GitHubReleaseAsset
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.GitHubSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.getApp
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.GitHubService
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.combine
import com.apkupdateross.util.filterVersionTag
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
    private val snackBar: SnackBar
) {
    private val rateLimitShown = AtomicBoolean(false)

    suspend fun updates(apps: List<AppInstalled>) = flow {
        rateLimitShown.set(false)
        val checks = mutableListOf(selfCheck())

        GitHubApps.forEachIndexed { i, app ->
            if (i != 0) {
                apps.find { it.packageName == app.packageName }?.let {
                    checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, app.extra))
                }
            }
        }

        checks.combine { all ->
            emit(all.flatMap { it })
        }.collect()
    }.catch {
        emit(emptyList())
        Log.e("GitHubRepository", "Error fetching releases.", it)
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitHubApps.forEach { app ->
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
        val releases = service.getReleases().filter { filterPreRelease(it) }
        val release = releases.firstOrNull()
        val assetUrl = release?.let { findApkAsset(it.assets) }?.takeIf { it.isNotEmpty() }

        if (release != null && assetUrl != null) {
            val versionName = release.versionNameOrTag()
            val versionCode = release.versionCodeFromTag()
            val remoteVersion = Version(versionName)
            val localVersion = Version(BuildConfig.VERSION_NAME)
            val isNewer = when {
                remoteVersion > localVersion -> true
                versionCode > BuildConfig.VERSION_CODE -> true
                else -> false
            }
            if (isNewer) {
                emit(listOf(AppUpdate(
                    name = "APKUpdater",
                    packageName = BuildConfig.APPLICATION_ID,
                    version = versionName,
                    oldVersion = BuildConfig.VERSION_NAME,
                    versionCode = versionCode,
                    oldVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    source = GitHubSource,
                    link = Link.Url(assetUrl),
                    whatsNew = release.body
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
            snackBar.snackBar(message = TextSnack("GitHub: API rate limit exceeded. Try again later."))
        }
        emit(emptyList())
        Log.e("GitHubRepository", "Error checking self-update.", e)
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val r = service.getReleases(user, repo)
        val releases = if (packageName == "com.apkupdateross.ci") {
            // TODO: Find a better way to do this
            r.filter { it.name.contains("CI-Release-3.x")}
        } else {
            r.filter { filterPreRelease(it) }.filter { findApkAsset(it.assets).isNotEmpty() }
        }

        if (releases.isNotEmpty() && Version(filterVersionTag(releases[0].tag_name)) > Version(currentVersion)) {
            val app = apps?.getApp(packageName)
            emit(listOf(AppUpdate(
                name = repo,
                packageName = packageName,
                version = releases[0].tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = 0L,
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitHubSource,
                link = findApkAssetArch(releases[0].assets, extra).let { Link.Url(it.browser_download_url, it.size) },
                whatsNew = releases[0].body,
                iconUri = if (apps == null) Uri.parse(releases[0].author.avatar_url) else Uri.EMPTY
            )))
        } else {
            emit(emptyList())
        }
    }.catch { e ->
        if (e is HttpException && e.code() == 403 &&
            e.response()?.headers()?.get("X-RateLimit-Remaining") == "0" &&
            !rateLimitShown.getAndSet(true)
        ) {
            snackBar.snackBar(message = TextSnack("GitHub: API rate limit exceeded. Try again later."))
        }
        emit(emptyList())
        Log.e("GitHubRepository", "Error fetching releases for $packageName.", e)
    }

    private fun GitHubRelease.versionNameOrTag(): String {
        val raw = tag_name.takeIf { it.isNotBlank() } ?: name
        return raw.removePrefix("v").trim()
    }

    private fun GitHubRelease.versionCodeFromTag(): Long {
        val clean = versionNameOrTag()
        val parts = clean.split('.', '-', '_')
        val major = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val minor = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val patch = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val extra = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        return major * 1000000L + minor * 10000L + patch * 100L + extra
    }

    private fun filterPreRelease(release: GitHubRelease) = when {
        prefs.ignorePreRelease.get() && release.prerelease -> false
        else -> true
    }

    private fun findApkAsset(assets: List<GitHubReleaseAsset>) = assets
        .filter { it.browser_download_url.endsWith(".apk", true) }
        .maxByOrNull { it.size }
        ?.browser_download_url
        .orEmpty()

    private fun findApkAssetArch(
        assets: List<GitHubReleaseAsset>,
        extra: Regex?
    ): GitHubReleaseAsset {
        val apks = assets
            .filter { it.browser_download_url.endsWith(".apk", true) }
            .filter { filterExtra(it, extra) }

        when {
            apks.isEmpty() -> return GitHubReleaseAsset(0L, "")
            apks.size == 1 -> return apks.first()
            else -> {
                // Try to match exact arch
                Build.SUPPORTED_ABIS.forEach { arch ->
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains(arch, true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm64
                if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match x64
                if (Build.SUPPORTED_ABIS.contains("x86_64")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("x64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm
                if (Build.SUPPORTED_ABIS.contains("armeabi-v7a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm", true)) {
                            return apk
                        }
                    }
                }
                // If no match, return biggest apk in the hope it's universal
                return apks.maxByOrNull { it.size } ?: GitHubReleaseAsset(0L, "")
            }
        }
    }

    private fun filterExtra(asset: GitHubReleaseAsset, extra: Regex?) = when(extra) {
        null -> true
        else -> asset.browser_download_url.matches(extra)
    }

}
