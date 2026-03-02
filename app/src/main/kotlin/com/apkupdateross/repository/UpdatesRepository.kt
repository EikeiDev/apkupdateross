package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.prefs.Prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow


class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val gitHubRepository: GitHubRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val ruStoreRepository: RuStoreRepository,
    private val prefs: Prefs
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun updates(force: Boolean = false) = flow<List<AppUpdate>> {
        if (force) {
            fdroidRepository.clearCache()
            izzyRepository.clearCache()
        }
        appsRepository.getApps().collect { result ->
            result.onSuccess { apps ->
                val filtered = apps.filter { !it.ignored }
                val sources = mutableListOf<Flow<List<AppUpdate>>>()
                if (prefs.useApkMirror.get()) sources.add(apkMirrorRepository.updates(filtered))
                if (prefs.useGitHub.get()) sources.add(gitHubRepository.updates(filtered))
                if (prefs.useFdroid.get()) sources.add(fdroidRepository.updates(filtered))
                if (prefs.useIzzy.get()) sources.add(izzyRepository.updates(filtered))
                if (prefs.useAptoide.get()) sources.add(aptoideRepository.updates(filtered))
                if (prefs.useApkPure.get()) sources.add(apkPureRepository.updates(filtered))
                if (prefs.useGitLab.get()) sources.add(gitLabRepository.updates(filtered))
                if (prefs.usePlay.get()) sources.add(playRepository.updates(filtered))
                if (prefs.useRuStore.get()) sources.add(ruStoreRepository.updates(filtered))

                if (sources.isNotEmpty()) {
                    val accumulated = mutableListOf<AppUpdate>()
                    sources.asFlow().flattenMerge(concurrency = sources.size).collect { newUpdates ->
                        if (newUpdates.isNotEmpty()) {
                            accumulated.addAll(newUpdates)
                            emit(accumulated.toList())
                        }
                    }
                    emit(accumulated.toList())
                } else {
                    emit(emptyList())
                }
            }.onFailure {
                Log.e("UpdatesRepository", "Error getting apps", it)
            }
        }
    }.catch {
        Log.e("UpdatesRepository", "Error getting updates", it)
    }

}
