package com.apkupdateross.repository

import android.os.SystemClock
import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.FdroidService
import com.apkupdateross.repository.FdroidRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.apkupdateross.data.ui.priority
import com.apkupdateross.data.ui.Source
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.GitHubSource
import com.apkupdateross.data.ui.AptoideSource
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.GitLabSource
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.RuStoreSource


class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val gitHubRepository: GitHubRepository,
    private val fdroidService: FdroidService,
    private val aptoideRepository: AptoideRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val ruStoreRepository: RuStoreRepository,
    private val prefs: Prefs
) {

    private val fdroidRepoCache = mutableMapOf<String, FdroidRepository>()

    private val _loadingSources = MutableStateFlow<Set<Source>>(emptySet())
    val loadingSources: StateFlow<Set<Source>> = _loadingSources.asStateFlow()

    private val _failedSources = MutableStateFlow<Set<Source>>(emptySet())
    val failedSources: StateFlow<Set<Source>> = _failedSources.asStateFlow()

    private fun Flow<List<AppUpdate>>.trackLoading(source: Source): Flow<List<AppUpdate>> = this
        .onStart { 
            _loadingSources.update { it + source } 
            _failedSources.update { it - source }
        }
        .catch { e ->
            Log.e("UpdatesRepository", "Source ${source.name} failed", e)
            _failedSources.update { it + source }
            emit(emptyList())
        }
        .onCompletion { _loadingSources.update { it - source } }

    private fun getFdroidRepo(repo: com.apkupdateross.data.fdroid.FdroidRepo): FdroidRepository {
        return fdroidRepoCache.getOrPut(repo.id) {
            FdroidRepository(fdroidService, repo.url, com.apkupdateross.data.ui.Source(repo.name, R.drawable.ic_fdroid), prefs)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun updates(force: Boolean = false) = flow<List<AppUpdate>> {
        if (force) {
            fdroidRepoCache.values.forEach { it.clearCache() }
        }
        appsRepository.getApps().collect { result ->
            result.onSuccess { apps ->
                val filtered = apps.filter { !it.ignored }
                val sources = mutableListOf<Flow<List<AppUpdate>>>()
                if (prefs.useApkMirror.get()) sources.add(apkMirrorRepository.updates(filtered).trackLoading(ApkMirrorSource))
                if (prefs.useGitHub.get()) sources.add(gitHubRepository.updates(filtered).trackLoading(GitHubSource))
                
                if (prefs.useFdroid.get()) {
                    prefs.fdroidRepos.get().filter { it.isEnabled }.forEach { repo ->
                        val source = Source(repo.name, R.drawable.ic_fdroid)
                        sources.add(getFdroidRepo(repo).updates(filtered).trackLoading(source))
                    }
                }
                
                if (prefs.useAptoide.get()) sources.add(aptoideRepository.updates(filtered).trackLoading(AptoideSource))
                if (prefs.useApkPure.get()) sources.add(apkPureRepository.updates(filtered).trackLoading(ApkPureSource))
                if (prefs.useGitLab.get()) sources.add(gitLabRepository.updates(filtered).trackLoading(GitLabSource))
                if (prefs.usePlay.get()) sources.add(playRepository.updates(filtered).trackLoading(PlaySource))
                if (prefs.useRuStore.get()) sources.add(ruStoreRepository.updates(filtered).trackLoading(RuStoreSource))

                val startElapsed = SystemClock.elapsedRealtime()
                val activeSources = sources.size

                if (sources.isNotEmpty()) {
                    val accumulatedList = mutableListOf<AppUpdate>()
                    
                    withTimeoutOrNull(prefs.globalTimeoutSec.get() * 1000L) {
                        sources.asFlow().flattenMerge(concurrency = sources.size).collect { newUpdates ->
                            if (newUpdates.isNotEmpty()) {
                                accumulatedList.addAll(newUpdates)
                                emit(accumulatedList.toList())
                            }
                        }
                    } ?: Log.w("UpdatesRepository", "Update check timed out after ${prefs.globalTimeoutSec.get()} seconds")
                    
                    emit(accumulatedList.toList())
                } else {
                    emit(emptyList())
                }

                val duration = SystemClock.elapsedRealtime() - startElapsed
                prefs.lastUpdateCheckDurationMs.put(duration)
                prefs.lastUpdateCheckTimestamp.put(System.currentTimeMillis())
                prefs.lastUpdateSourcesCount.put(activeSources)
            }.onFailure {
                Log.e("UpdatesRepository", "Error getting apps", it)
            }
        }
    }.catch {
        Log.e("UpdatesRepository", "Error getting updates", it)
    }
}
