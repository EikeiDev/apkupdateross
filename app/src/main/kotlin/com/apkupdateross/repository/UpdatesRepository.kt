package com.apkupdateross.repository

import android.os.SystemClock
import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.StoreNotFoundCache
import kotlinx.coroutines.CancellationException
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
import com.apkupdateross.data.ui.CodebergSource
import com.apkupdateross.data.ui.AptoideSource
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.Apk20Source
import com.apkupdateross.data.ui.GitLabSource
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.RuStoreSource
import com.apkupdateross.data.ui.HuaweiSource
import com.apkupdateross.data.ui.UptodownSource
import com.apkupdateross.data.ui.SamsungGalaxyStoreSource


class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val gitHubRepository: GitHubRepository,
    private val codebergRepository: CodebergRepository,
    private val fdroidRepoRegistry: FdroidRepoRegistry,
    private val aptoideRepository: AptoideRepository,
    private val apkPureRepository: ApkPureRepository,
    private val apk20Repository: Apk20Repository,
    private val uptodownRepository: UptodownRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val ruStoreRepository: RuStoreRepository,
    private val huaweiRepository: HuaweiRepository,
    private val samsungGalaxyStoreRepository: SamsungGalaxyStoreRepository,
    private val prefs: Prefs,
    private val storeNotFoundCache: StoreNotFoundCache
) {

    private companion object {
        const val MAX_SOURCE_CONCURRENCY = 8
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300
    }

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
            if (e is CancellationException) throw e
            Log.e("UpdatesRepository", "Source ${source.name} failed", e)
            _failedSources.update { it + source }
            emit(emptyList())
        }
        .onCompletion { _loadingSources.update { it - source } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun updates(force: Boolean = false, targetPackageName: String? = null) = flow<List<AppUpdate>> {
        if (force) {
            fdroidRepoRegistry.clearAllCaches()
        }
        if (targetPackageName != null) {
            storeNotFoundCache.clearPackage(targetPackageName)
        } else {
            _failedSources.value = emptySet()
            _loadingSources.value = emptySet()
        }
        appsRepository.getApps().collect { result ->
            result.onSuccess { apps ->
                val filtered = if (targetPackageName != null) {
                    apps.filter { it.packageName == targetPackageName }
                } else {
                    apps.filter { !it.ignored }
                }
                val sources = buildSourceFlows(filtered)

                val startElapsed = SystemClock.elapsedRealtime()
                val activeSources = sources.size

                if (sources.isNotEmpty()) {
                    val accumulatedList = mutableListOf<AppUpdate>()
                    val timeoutSeconds = prefs.globalTimeoutSec.get()
                        .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
                    val concurrency = sources.size.coerceAtMost(MAX_SOURCE_CONCURRENCY)

                    val completed = withTimeoutOrNull(timeoutSeconds * 1000L) {
                        sources.asFlow().flattenMerge(concurrency = concurrency).collect { newUpdates ->
                            if (newUpdates.isNotEmpty()) {
                                accumulatedList.addAll(newUpdates)
                                emit(accumulatedList.toList())
                            }
                        }
                        true
                    }
                    if (completed == null) {
                        val timedOut = _loadingSources.value
                        if (timedOut.isNotEmpty()) {
                            _failedSources.update { it + timedOut }
                            _loadingSources.value = emptySet()
                        }
                        Log.w("UpdatesRepository", "Update check timed out after $timeoutSeconds seconds (pending: ${timedOut.map { it.name }})")
                    }

                    emit(accumulatedList.toList())
                } else {
                    emit(emptyList())
                }

                if (targetPackageName == null) {
                    val duration = SystemClock.elapsedRealtime() - startElapsed
                    prefs.lastUpdateCheckDurationMs.put(duration)
                    prefs.lastUpdateCheckTimestamp.put(System.currentTimeMillis())
                    prefs.lastUpdateSourcesCount.put(activeSources)
                }
            }.onFailure {
                Log.e("UpdatesRepository", "Error getting apps", it)
            }
        }
    }.catch {
        Log.e("UpdatesRepository", "Error getting updates", it)
    }

    fun retrySource(source: Source): Flow<List<AppUpdate>> = flow {
        appsRepository.getApps().collect { result ->
            result.onSuccess { apps ->
                val filtered = apps.filter { !it.ignored }
                val sourceFlow = buildSingleSourceFlow(source, filtered) ?: return@onSuccess
                val timeoutSeconds = prefs.globalTimeoutSec.get()
                    .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
                val accumulated = mutableListOf<AppUpdate>()
                val completed = withTimeoutOrNull(timeoutSeconds * 1000L) {
                    sourceFlow.collect { newUpdates ->
                        if (newUpdates.isNotEmpty()) {
                            accumulated.addAll(newUpdates)
                            emit(accumulated.toList())
                        }
                    }
                    true
                }
                if (completed == null) {
                    _failedSources.update { it + source }
                    _loadingSources.update { it - source }
                }
                emit(accumulated.toList())
            }
        }
    }.catch {
        Log.e("UpdatesRepository", "Error retrying source ${source.name}", it)
    }

    private suspend fun buildSourceFlows(filtered: List<com.apkupdateross.data.ui.AppInstalled>): List<Flow<List<AppUpdate>>> {
        val sources = mutableListOf<Flow<List<AppUpdate>>>()
        if (prefs.useApkMirror.get()) sources.add(apkMirrorRepository.updates(filtered).trackLoading(ApkMirrorSource))
        if (prefs.useGitHub.get()) sources.add(gitHubRepository.updates(filtered).trackLoading(GitHubSource))
        if (prefs.useCodeberg.get()) sources.add(codebergRepository.updates(filtered).trackLoading(CodebergSource))

        if (prefs.useFdroid.get()) {
            prefs.fdroidRepos.get().filter { it.isEnabled }.forEach { repo ->
                val source = Source(repo.name, R.drawable.ic_fdroid)
                fdroidRepoRegistry.get(repo)?.let {
                    sources.add(it.updates(filtered).trackLoading(source))
                }
            }
        }

        if (prefs.useAptoide.get()) sources.add(aptoideRepository.updates(filtered).trackLoading(AptoideSource))
        if (prefs.useApkPure.get()) sources.add(apkPureRepository.updates(filtered).trackLoading(ApkPureSource))
        if (prefs.useApk20.get()) sources.add(apk20Repository.updates(filtered).trackLoading(Apk20Source))
        if (prefs.useUptodown.get()) sources.add(uptodownRepository.updates(filtered).trackLoading(UptodownSource))
        if (prefs.useGitLab.get()) sources.add(gitLabRepository.updates(filtered).trackLoading(GitLabSource))
        if (prefs.usePlay.get()) sources.add(playRepository.updates(filtered).trackLoading(PlaySource))
        if (prefs.useRuStore.get()) sources.add(ruStoreRepository.updates(filtered).trackLoading(RuStoreSource))
        if (prefs.useHuawei.get()) sources.add(huaweiRepository.updates(filtered).trackLoading(HuaweiSource))
        if (prefs.useSamsungGalaxyStore.get()) {
            sources.add(samsungGalaxyStoreRepository.updates(filtered).trackLoading(SamsungGalaxyStoreSource))
        }
        return sources
    }

    private suspend fun buildSingleSourceFlow(
        source: Source,
        filtered: List<com.apkupdateross.data.ui.AppInstalled>
    ): Flow<List<AppUpdate>>? = when (source.name) {
        ApkMirrorSource.name -> apkMirrorRepository.updates(filtered).trackLoading(ApkMirrorSource)
        GitHubSource.name -> gitHubRepository.updates(filtered).trackLoading(GitHubSource)
        CodebergSource.name -> codebergRepository.updates(filtered).trackLoading(CodebergSource)
        AptoideSource.name -> aptoideRepository.updates(filtered).trackLoading(AptoideSource)
        ApkPureSource.name -> apkPureRepository.updates(filtered).trackLoading(ApkPureSource)
        Apk20Source.name -> {
            storeNotFoundCache.clearStore(StoreNotFoundCache.STORE_APK20)
            apk20Repository.updates(filtered).trackLoading(Apk20Source)
        }
        UptodownSource.name -> {
            storeNotFoundCache.clearStore(StoreNotFoundCache.STORE_UPTODOWN)
            uptodownRepository.updates(filtered).trackLoading(UptodownSource)
        }
        GitLabSource.name -> gitLabRepository.updates(filtered).trackLoading(GitLabSource)
        PlaySource.name -> playRepository.updates(filtered).trackLoading(PlaySource)
        RuStoreSource.name -> ruStoreRepository.updates(filtered).trackLoading(RuStoreSource)
        HuaweiSource.name -> {
            storeNotFoundCache.clearStore(StoreNotFoundCache.STORE_HUAWEI)
            huaweiRepository.updates(filtered).trackLoading(HuaweiSource)
        }
        SamsungGalaxyStoreSource.name -> {
            storeNotFoundCache.clearStore(StoreNotFoundCache.STORE_SAMSUNG)
            samsungGalaxyStoreRepository.updates(filtered).trackLoading(SamsungGalaxyStoreSource)
        }
        else -> prefs.fdroidRepos.get()
            .firstOrNull { it.isEnabled && it.name == source.name }
            ?.let { repo ->
                fdroidRepoRegistry.get(repo)?.also { it.clearCache() }?.updates(filtered)?.trackLoading(source)
            }
    }
}
