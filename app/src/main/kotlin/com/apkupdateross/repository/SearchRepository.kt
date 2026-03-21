package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.FdroidRepository
import com.apkupdateross.service.FdroidService
import com.apkupdateross.util.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import com.apkupdateross.data.ui.priority

class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidService: FdroidService,
    private val aptoideRepository: AptoideRepository,
    private val gitHubRepository: GitHubRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val ruStoreRepository: RuStoreRepository,
    private val prefs: Prefs
) {

    private val fdroidRepoCache = mutableMapOf<String, FdroidRepository>()

    private fun getFdroidRepo(repo: com.apkupdateross.data.fdroid.FdroidRepo): FdroidRepository {
        return fdroidRepoCache.getOrPut(repo.id) {
            FdroidRepository(fdroidService, repo.url, com.apkupdateross.data.ui.Source(repo.name, com.apkupdateross.R.drawable.ic_fdroid), prefs)
        }
    }


    fun search(text: String, filters: Set<SearchSourceFilter>) = flow {
        val sources = buildSources(text, filters)

        if (sources.isNotEmpty()) {
            sources.combine { results ->
                val apps = results.filter { it.isSuccess }.mapNotNull { it.getOrNull() }.flatten()
                
                val sortedResult = apps.sortedWith(
                    compareBy<AppUpdate> {
                        !it.name.startsWith(text, ignoreCase = true)
                    }.thenBy {
                        !it.name.contains(text, ignoreCase = true)
                    }.thenBy {
                        it.name
                    }
                )
                emit(Result.success(sortedResult))
            }.collect()
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
                sources += getFdroidRepo(repo).search(text)
            }
        }
        if (filters.shouldInclude(SearchSourceFilter.APTOIDE)) {
            sources += aptoideRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.APKPURE)) {
            sources += apkPureRepository.search(text)
        }
        if (filters.shouldInclude(SearchSourceFilter.GITHUB)) {
            sources += gitHubRepository.search(text)
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

        return sources
    }

}
