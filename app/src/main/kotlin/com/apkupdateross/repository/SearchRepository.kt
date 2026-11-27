package com.apkupdateross.repository

import android.util.Log
import com.apkupdateross.R
import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import com.apkupdateross.data.ui.Apk20Source
import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.ApkPureSource
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.AptoideSource
import com.apkupdateross.data.ui.CodebergSource
import com.apkupdateross.data.ui.FourPdaSource
import com.apkupdateross.data.ui.GitHubSource
import com.apkupdateross.data.ui.GitLabSource
import com.apkupdateross.data.ui.HuaweiSource
import com.apkupdateross.data.ui.PlaySource
import com.apkupdateross.data.ui.RuStoreSource
import com.apkupdateross.data.ui.SamsungGalaxyStoreSource
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.Source
import com.apkupdateross.data.ui.UptodownSource
import com.apkupdateross.prefs.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidRepoRegistry: FdroidRepoRegistry,
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
    private val fourPdaRepository: FourPdaRepository,
    private val prefs: Prefs
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

    fun clearStatus() {
        _loadingSources.value = emptySet()
        _failedSources.value = emptySet()
    }

    private fun Flow<Result<List<AppUpdate>>>.trackLoading(source: Source): Flow<Result<List<AppUpdate>>> = this
        .onStart {
            _loadingSources.update { it + source }
            _failedSources.update { it - source }
        }
        .onEach { result ->
            if (result.isFailure) {
                _failedSources.update { it + source }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            Log.e("SearchRepository", "Source ${source.name} failed", e)
            _failedSources.update { it + source }
            emit(Result.failure(e))
        }
        .onCompletion { _loadingSources.update { it - source } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(text: String, filters: Set<SearchSourceFilter>) = flow {
        _failedSources.value = emptySet()
        _loadingSources.value = emptySet()
        val sources = buildSources(text, filters)

        if (sources.isNotEmpty()) {
            val accumulated = mutableListOf<AppUpdate>()
            val timeoutSeconds = prefs.globalTimeoutSec.get()
                .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
            val concurrency = sources.size.coerceAtMost(MAX_SOURCE_CONCURRENCY)
            val completed = withTimeoutOrNull(timeoutSeconds * 1000L) {
                sources.asFlow().flattenMerge(concurrency).collect { result ->
                    result.onSuccess { apps ->
                        accumulated.addAll(apps)
                        emit(Result.success(accumulated.sortedForQuery(text)))
                    }.onFailure { error ->
                        Log.e("SearchRepository", "Search source failed", error)
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
                Log.w("SearchRepository", "Search timed out after $timeoutSeconds seconds (pending: ${timedOut.map { it.name }})")
            }
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
            sources += apkMirrorRepository.search(text).trackLoading(ApkMirrorSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.FDROID)) {
            prefs.fdroidRepos.get().filter { it.isEnabled }.forEach { repo ->
                val source = Source(repo.name, R.drawable.ic_fdroid)
                fdroidRepoRegistry.get(repo)?.let { sources += it.search(text).trackLoading(source) }
            }
        }
        if (filters.shouldInclude(SearchSourceFilter.APTOIDE)) {
            sources += aptoideRepository.search(text).trackLoading(AptoideSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.APKPURE)) {
            sources += apkPureRepository.search(text).trackLoading(ApkPureSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.APK20)) {
            sources += apk20Repository.search(text).trackLoading(Apk20Source)
        }
        if (filters.shouldInclude(SearchSourceFilter.UPTODOWN)) {
            sources += uptodownRepository.search(text).trackLoading(UptodownSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.GITHUB)) {
            sources += gitHubRepository.search(text).trackLoading(GitHubSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.CODEBERG)) {
            sources += codebergRepository.search(text).trackLoading(CodebergSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.GITLAB)) {
            sources += gitLabRepository.search(text).trackLoading(GitLabSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.PLAY)) {
            sources += playRepository.search(text).trackLoading(PlaySource)
        }
        if (filters.shouldInclude(SearchSourceFilter.RUSTORE)) {
            sources += ruStoreRepository.search(text).trackLoading(RuStoreSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.HUAWEI)) {
            sources += huaweiRepository.search(text).trackLoading(HuaweiSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.SAMSUNG)) {
            sources += samsungGalaxyStoreRepository.search(text).trackLoading(SamsungGalaxyStoreSource)
        }
        if (filters.shouldInclude(SearchSourceFilter.FOURPDA)) {
            sources += fourPdaRepository.search(text).trackLoading(FourPdaSource)
        }

        return sources
    }

    fun retrySource(text: String, source: Source): Flow<Result<List<AppUpdate>>> = flow {
        val sourceFlow = when (source.name) {
            ApkMirrorSource.name -> apkMirrorRepository.search(text).trackLoading(ApkMirrorSource)
            AptoideSource.name -> aptoideRepository.search(text).trackLoading(AptoideSource)
            ApkPureSource.name -> apkPureRepository.search(text).trackLoading(ApkPureSource)
            Apk20Source.name -> apk20Repository.search(text).trackLoading(Apk20Source)
            UptodownSource.name -> uptodownRepository.search(text).trackLoading(UptodownSource)
            GitHubSource.name -> gitHubRepository.search(text).trackLoading(GitHubSource)
            CodebergSource.name -> codebergRepository.search(text).trackLoading(CodebergSource)
            GitLabSource.name -> gitLabRepository.search(text).trackLoading(GitLabSource)
            PlaySource.name -> playRepository.search(text).trackLoading(PlaySource)
            RuStoreSource.name -> ruStoreRepository.search(text).trackLoading(RuStoreSource)
            HuaweiSource.name -> huaweiRepository.search(text).trackLoading(HuaweiSource)
            SamsungGalaxyStoreSource.name -> samsungGalaxyStoreRepository.search(text).trackLoading(SamsungGalaxyStoreSource)
            FourPdaSource.name -> fourPdaRepository.search(text).trackLoading(FourPdaSource)
            else -> prefs.fdroidRepos.get()
                .firstOrNull { it.isEnabled && it.name == source.name }
                ?.let { repo ->
                    fdroidRepoRegistry.get(repo)?.also { it.clearCache() }?.search(text)?.trackLoading(source)
                }
        } ?: return@flow

        val timeoutSeconds = prefs.globalTimeoutSec.get()
            .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        val accumulated = mutableListOf<AppUpdate>()
        withTimeoutOrNull(timeoutSeconds * 1000L) {
            sourceFlow.collect { result ->
                result.onSuccess { apps ->
                    accumulated.addAll(apps)
                    emit(Result.success(accumulated.sortedForQuery(text)))
                }
            }
        }
        emit(Result.success(accumulated.sortedForQuery(text)))
    }.catch {
        emit(Result.failure(it))
        Log.e("SearchRepository", "Error retrying source ${source.name}", it)
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
