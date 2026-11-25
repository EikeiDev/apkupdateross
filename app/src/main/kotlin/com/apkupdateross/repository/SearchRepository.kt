package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.FdroidRepository
import com.apkupdateross.service.FdroidService
import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidService: FdroidService,
    private val aptoideRepository: AptoideRepository,
    private val gitHubRepository: GitHubRepository,
    private val codebergRepository: CodebergRepository,
    private val apkPureRepository: ApkPureRepository,
    private val apk20Repository: Apk20Repository,
    private val uptodownRepository: UptodownRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val ruStoreRepository: RuStoreRepository,
    private val huaweiRepository: HuaweiRepository,
    private val samsungGalaxyStoreRepository: SamsungGalaxyStoreRepository,
    private val prefs: Prefs
) {

    private companion object {
        const val MAX_SOURCE_CONCURRENCY = 8
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300
    }

    private val fdroidRepoCache = mutableMapOf<String, FdroidRepository>()

    private fun getFdroidRepo(repo: com.apkupdateross.data.fdroid.FdroidRepo): FdroidRepository? {
        val normalizedUrl = normalizeFdroidRepoUrl(repo.url) ?: return null
        val cacheKey = "${repo.id}|$normalizedUrl|${repo.name}"
        return fdroidRepoCache.getOrPut(cacheKey) {
            FdroidRepository(fdroidService, normalizedUrl, com.apkupdateross.data.ui.Source(repo.name, com.apkupdateross.R.drawable.ic_fdroid), prefs)
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(text: String, filters: Set<SearchSourceFilter>) = flow {
        val sources = buildSources(text, filters)

        if (sources.isNotEmpty()) {
            val accumulated = mutableListOf<AppUpdate>()
            val timeoutSeconds = prefs.globalTimeoutSec.get()
                .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
            val concurrency = sources.size.coerceAtMost(MAX_SOURCE_CONCURRENCY)
            withTimeoutOrNull(timeoutSeconds * 1000L) {
                sources.asFlow().flattenMerge(concurrency).collect { result ->
                    result.onSuccess { apps ->
                        accumulated.addAll(apps)
                        emit(Result.success(accumulated.sortedForQuery(text)))
                    }.onFailure { error ->
                        Log.e("SearchRepository", "Search source failed", error)
                    }
                }
            } ?: Log.w("SearchRepository", "Search timed out after $timeoutSeconds seconds")
            if (accumulated.isEmpty()) emit(Result.success(emptyList()))
        } else {
            emit(Result.success(emptyList()))
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("SearchRepository", "Error searching.", it)
    }

    private suspend fun buildSources(text: String, filters: Set<SearchSourceFilter>): List<Flow<Result<List<AppUpdate>>>> {
        val sources = mutableListOf<Flow<Result<List<AppUpdate>>>>()

        fun Set<SearchSourceFilter>.shouldInclude(filter: SearchSourceFilter) = contains(filter)

        if (filters.shouldInclude(SearchSourceFilter.APKMIRROR)) {
            sources += apkMirrorRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.FDROID)) {
            prefs.fdroidRepos.get().filter { it.isEnabled }.forEach { repo ->
                getFdroidRepo(repo)?.let { sources += it.search(text) }
            }
        }
        if (filters.shouldInclude(SearchSourceFilter.APTOIDE)) {
            sources += aptoideRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.APKPURE)) {
            sources += apkPureRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.APK20) && prefs.useApk20.get()) {
            sources += apk20Repository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.UPTODOWN) && prefs.useUptodown.get()) {
            sources += uptodownRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.GITHUB)) {
            sources += gitHubRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.CODEBERG) && prefs.useCodeberg.get()) {
            sources += codebergRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.GITLAB)) {
            sources += gitLabRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.PLAY)) {
            sources += playRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.RUSTORE)) {
            sources += ruStoreRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.HUAWEI) && prefs.useHuawei.get()) {
            sources += huaweiRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.SAMSUNG) && prefs.useSamsungGalaxyStore.get()) {
            sources += samsungGalaxyStoreRepository.search(text)
        }

        return sources
    }

    private fun List<AppUpdate>.sortedForQuery(text: String): List<AppUpdate> = sortedWith(
        compareBy<AppUpdate> {
            !it.name.startsWith(text, ignoreCase = true)
        }.thenBy {
            !it.name.contains(text, ignoreCase = true)
        }.thenBy {
            it.name
        }
    )

}
