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
import com.apkupdateross.data.ui.priority


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
                if (prefs.useApkMirror.get()) sources.add(apkMirrorRepository.updates(filtered))
                if (prefs.useGitHub.get()) sources.add(gitHubRepository.updates(filtered))
                
                if (prefs.useFdroid.get()) {
                    prefs.fdroidRepos.get().filter { it.isEnabled }.forEach { repo ->
                        sources.add(getFdroidRepo(repo).updates(filtered))
                    }
                }
                
                if (prefs.useAptoide.get()) sources.add(aptoideRepository.updates(filtered))
                if (prefs.useApkPure.get()) sources.add(apkPureRepository.updates(filtered))
                if (prefs.useGitLab.get()) sources.add(gitLabRepository.updates(filtered))
                if (prefs.usePlay.get()) sources.add(playRepository.updates(filtered))
                if (prefs.useRuStore.get()) sources.add(ruStoreRepository.updates(filtered))

                val startElapsed = SystemClock.elapsedRealtime()
                val activeSources = sources.size

                if (sources.isNotEmpty()) {
                    val accumulatedMap = mutableMapOf<String, AppUpdate>()
                    val accumulatedList = mutableListOf<AppUpdate>()
                    val filterAndDeduplicate = prefs.ruStoreFilterThirdParty.get()
                    
                    sources.asFlow().flattenMerge(concurrency = sources.size).collect { newUpdates ->
                        if (newUpdates.isNotEmpty()) {
                            if (filterAndDeduplicate) {
                                newUpdates.forEach { update ->
                                    val existing = accumulatedMap[update.packageName]
                                    if (existing == null || update.source.priority(true) > existing.source.priority(true)) {
                                        accumulatedMap[update.packageName] = update
                                    }
                                }
                                emit(accumulatedMap.values.toList())
                            } else {
                                accumulatedList.addAll(newUpdates)
                                emit(accumulatedList.toList())
                            }
                        }
                    }
                    if (filterAndDeduplicate) emit(accumulatedMap.values.toList()) else emit(accumulatedList.toList())
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
