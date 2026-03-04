package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val gitHubRepository: GitHubRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val ruStoreRepository: RuStoreRepository,
    private val prefs: Prefs
) {

    fun search(text: String, filters: Set<SearchSourceFilter>) = flow {
        val sources = buildSources(text, filters)

        if (sources.isNotEmpty()) {
            sources.combine { updates ->
                val result = updates.filter { it.isSuccess }.mapNotNull { it.getOrNull() }
                emit(Result.success(result.flatten().sortedWith(
                    compareBy<AppUpdate> {
                        !it.name.startsWith(text, ignoreCase = true)
                    }.thenBy {
                        !it.name.contains(text, ignoreCase = true)
                    }.thenBy {
                        it.name
                    }
                )))
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

        suspend fun MutableList<Flow<Result<List<AppUpdate>>>>.addIf(enabled: Boolean, block: suspend () -> Flow<Result<List<AppUpdate>>>) {
            if (enabled) add(block())
        }

        fun Set<SearchSourceFilter>.shouldInclude(filter: SearchSourceFilter) = contains(filter)

        if (filters.shouldInclude(SearchSourceFilter.APKMIRROR)) {
            sources.addIf(prefs.useApkMirror.get()) { apkMirrorRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.FDROID_MAIN)) {
            sources.addIf(prefs.useFdroid.get()) { fdroidRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.FDROID_IZZY)) {
            sources.addIf(prefs.useIzzy.get()) { izzyRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.APTOIDE)) {
            sources.addIf(prefs.useAptoide.get()) { aptoideRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.APKPURE)) {
            sources.addIf(prefs.useApkPure.get()) { apkPureRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.GITHUB)) {
            sources.addIf(prefs.useGitHub.get()) { gitHubRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.GITLAB)) {
            sources.addIf(prefs.useGitLab.get()) { gitLabRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.PLAY)) {
            sources.addIf(prefs.usePlay.get()) { playRepository.search(text) }
        }
        if (filters.shouldInclude(SearchSourceFilter.RUSTORE)) {
            sources.addIf(prefs.useRuStore.get()) { ruStoreRepository.search(text) }
        }

        return sources
    }

}
