package com.apkupdateross.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkupdateross.data.fourpda.FourPdaBuildPreference
import com.apkupdateross.data.fourpda.FourPdaCrawlerAttachment
import com.apkupdateross.data.fourpda.FourPdaCrawlerReleaseLink
import com.apkupdateross.data.fourpda.FourPdaCrawlerTopic
import com.apkupdateross.data.fourpda.FourPdaHealthResponse
import com.apkupdateross.data.fourpda.FourPdaPostResponse
import com.apkupdateross.data.fourpda.FourPdaSearchResponse
import com.apkupdateross.data.fourpda.FourPdaTopicBinding
import com.apkupdateross.data.fourpda.FourPdaUpdatesRequest
import com.apkupdateross.data.fourpda.FourPdaUpdatesResponse
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.DownloadChoice
import com.apkupdateross.data.ui.FourPdaSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.AbiMatcher
import com.apkupdateross.util.FourPdaSession
import com.apkupdateross.util.filterVersionTag
import com.google.gson.Gson
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class FourPdaRepository(
    client: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs,
    private val session: FourPdaSession
) {
    private val apiClient = client.newBuilder()
        .cache(null)
        .build()

    private val postNotesCache = ConcurrentHashMap<Long, String>()

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val baseUrl = DEFAULT_SERVER_URL
        if (apps.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val manualBindings = prefs.fourPdaTopicBindings.get()
        val preference = FourPdaBuildPreference.fromIndex(prefs.fourPdaBuildPreference.get())
        val loggedIn = session.isLoggedIn()

        val bulkMatches: Map<String, List<FourPdaCrawlerTopic>> = withContext(Dispatchers.IO) {
            fetchBulkUpdates(baseUrl, apps.map { it.packageName })
        }

        // Fetch any manually bound topics that weren't already present in bulkMatches
        val manualTopicsByPkg = mutableMapOf<String, FourPdaCrawlerTopic>()
        val manualEntries = apps.mapNotNull { app ->
            val binding = manualBindings[app.packageName]
            if (binding != null && binding.topicId > 0L) app to binding else null
        }
        if (manualEntries.isNotEmpty()) {
            val limiter = Semaphore(4)
            coroutineScope {
                manualEntries.map { (app, binding) ->
                    async(Dispatchers.IO) {
                        val fromBulk = bulkMatches[app.packageName.lowercase(Locale.ROOT)]
                            ?.firstOrNull { it.topicId == binding.topicId }
                        if (fromBulk != null) {
                            app.packageName to fromBulk
                        } else {
                            limiter.withPermit {
                                val fetched = runCatching { fetchTopic(baseUrl, binding.topicId, refresh = false) }.getOrNull()
                                if (fetched != null) app.packageName to fetched else null
                            }
                        }
                    }
                }.awaitAll().filterNotNull().forEach { (pkg, topic) ->
                    manualTopicsByPkg[pkg] = topic
                }
            }
        }

        val abis = Build.SUPPORTED_ABIS.toList()
        val candidates = mutableListOf<Triple<AppUpdate, Long, FourPdaCrawlerReleaseLink?>>()

        for (app in apps) {
            val binding = manualBindings[app.packageName]
            val topic = manualTopicsByPkg[app.packageName]
                ?: bulkMatches[app.packageName.lowercase(Locale.ROOT)]?.firstOrNull()
                ?: continue

            val fileFilter = binding?.fileFilter.orEmpty()
            val resolvedAttachments = resolveAttachmentMetadata(topic.attachments, topic.releaseLinks, postNotesCache)
            val attachment = selectFourPdaCrawlerAttachment(
                attachments = resolvedAttachments,
                supportedAbis = abis,
                preference = preference,
                fileFilter = fileFilter
            ) ?: continue

            val candidateVersion = sequenceOf(
                attachment.version,
                topic.latestVersion,
                topic.headerVersion
            ).firstOrNull { it.isNotBlank() }.orEmpty()

            if (candidateVersion.isBlank() || !isFourPdaUpdate(candidateVersion, app.version)) {
                continue
            }

            val matchingLink = if (attachment.postId > 0L) {
                topic.releaseLinks.firstOrNull { it.postId == attachment.postId }
            } else null

            val initialReleaseType = detectFourPdaReleaseType(
                version = candidateVersion,
                fileName = attachment.fileName,
                releaseLabel = matchingLink?.label.orEmpty(),
                releaseVersion = matchingLink?.version.orEmpty()
            )
            if (!shouldInclude(initialReleaseType)) continue

            val link: Link = if (loggedIn && attachment.downloadUrl.startsWith("https://", ignoreCase = true)) {
                Link.Url(
                    link = attachment.downloadUrl,
                    size = attachment.sizeBytes,
                    expectedPackageName = app.packageName
                )
            } else {
                Link.Empty
            }

            val releaseUrl = if (attachment.postId > 0L) {
                "https://4pda.to/forum/index.php?showtopic=${topic.topicId}&view=findpost&p=${attachment.postId}"
            } else {
                topic.topicUrl.ifBlank { "https://4pda.to/forum/index.php?showtopic=${topic.topicId}" }
            }

            val choices = if (loggedIn && preference == FourPdaBuildPreference.ASK) {
                buildFourPdaDownloadChoices(
                    attachments = resolvedAttachments,
                    supportedAbis = abis,
                    expectedPackageName = app.packageName,
                    topicId = topic.topicId,
                    fileFilter = fileFilter
                )
            } else {
                emptyList()
            }

            val update = AppUpdate(
                name = app.name.ifBlank { topic.title },
                packageName = app.packageName,
                version = candidateVersion,
                oldVersion = app.version,
                versionCode = 0L,
                oldVersionCode = app.versionCode,
                source = FourPdaSource,
                iconUri = topic.iconUrl.takeIf(String::isNotBlank)?.let(Uri::parse) ?: app.iconUri,
                link = link,
                sourceUrl = topic.topicUrl.ifBlank { "https://4pda.to/forum/index.php?showtopic=${topic.topicId}" },
                releaseUrl = releaseUrl,
                whatsNew = "",
                total = attachment.sizeBytes,
                releaseType = initialReleaseType,
                downloadChoices = choices
            )
            candidates.add(Triple(update, attachment.postId, matchingLink))
        }

        // Check post 'Тип:' for matched updates so Beta/Alpha posts show the right badge on the card
        // without putting raw post notes onto the card itself.
        val postLimiter = Semaphore(4)
        val refinedUpdates = coroutineScope {
            candidates.map { (update, postId, matchingLink) ->
                async(Dispatchers.IO) {
                    if (postId <= 0L) return@async update
                    postLimiter.withPermit {
                        val notes = loadPostNotes(postId).orEmpty()
                        val refinedType = detectFourPdaReleaseType(
                            version = update.version,
                            fileName = update.downloadChoices.firstOrNull { it.postId == postId }?.label.orEmpty(),
                            releaseLabel = matchingLink?.label.orEmpty(),
                            releaseVersion = matchingLink?.version.orEmpty(),
                            postNotes = notes
                        )
                        if (!shouldInclude(refinedType)) {
                            null
                        } else if (refinedType != update.releaseType) {
                            update.copy(releaseType = refinedType)
                        } else {
                            update
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        emit(refinedUpdates)
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e(TAG, "Error checking 4PDA updates", error)
        throw error
    }

    suspend fun search(text: String) = flow {
        val query = text.trim()
        val baseUrl = DEFAULT_SERVER_URL
        if (query.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val preference = FourPdaBuildPreference.fromIndex(prefs.fourPdaBuildPreference.get())
        val loggedIn = session.isLoggedIn()
        val abis = Build.SUPPORTED_ABIS.toList()

        val topics = withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val body = getJson("$baseUrl/api/v1/search?q=$encoded&limit=$MAX_SEARCH_RESULTS")
            gson.fromJson(body, FourPdaSearchResponse::class.java)?.items.orEmpty()
        }

        val results = topics.mapNotNull { topic ->
            val resolvedAttachments = resolveAttachmentMetadata(topic.attachments, topic.releaseLinks, postNotesCache)
            val attachment = selectFourPdaCrawlerAttachment(
                attachments = resolvedAttachments,
                supportedAbis = abis,
                preference = preference
            )
            val version = sequenceOf(
                attachment?.version.orEmpty(),
                topic.latestVersion,
                topic.headerVersion
            ).firstOrNull { it.isNotBlank() }.orEmpty()

            val matchingLink = if (attachment != null && attachment.postId > 0L) {
                topic.releaseLinks.firstOrNull { it.postId == attachment.postId }
            } else null
            val releaseType = detectFourPdaReleaseType(
                version = version,
                fileName = attachment?.fileName.orEmpty(),
                releaseLabel = matchingLink?.label.orEmpty(),
                releaseVersion = matchingLink?.version.orEmpty()
            )
            if (!shouldInclude(releaseType)) return@mapNotNull null

            val packageName = topic.packageNames.firstOrNull() ?: "fourpda.topic.${topic.topicId}"
            val link: Link = if (loggedIn && attachment != null && attachment.downloadUrl.startsWith("https://", ignoreCase = true)) {
                Link.Url(
                    link = attachment.downloadUrl,
                    size = attachment.sizeBytes,
                    expectedPackageName = topic.packageNames.firstOrNull()
                )
            } else {
                Link.Empty
            }

            val topicUrl = topic.topicUrl.ifBlank { "https://4pda.to/forum/index.php?showtopic=${topic.topicId}" }
            val releaseUrl = if (attachment != null && attachment.postId > 0L) {
                "https://4pda.to/forum/index.php?showtopic=${topic.topicId}&view=findpost&p=${attachment.postId}"
            } else {
                topicUrl
            }

            val choices = if (loggedIn && preference == FourPdaBuildPreference.ASK) {
                buildFourPdaDownloadChoices(
                    attachments = resolvedAttachments,
                    supportedAbis = abis,
                    expectedPackageName = topic.packageNames.firstOrNull(),
                    topicId = topic.topicId
                )
            } else {
                emptyList()
            }

            AppUpdate(
                name = topic.title.ifBlank { "4PDA #${topic.topicId}" },
                packageName = packageName,
                version = version.ifBlank { "4PDA" },
                oldVersion = "?",
                versionCode = 0L,
                oldVersionCode = 0L,
                source = FourPdaSource,
                iconUri = topic.iconUrl.takeIf(String::isNotBlank)?.let(Uri::parse) ?: Uri.EMPTY,
                link = link,
                sourceUrl = topicUrl,
                releaseUrl = releaseUrl,
                whatsNew = "",
                total = attachment?.sizeBytes ?: 0L,
                releaseType = releaseType,
                downloadChoices = choices
            )
        }

        emit(Result.success(results.distinctBy { it.packageName.lowercase(Locale.ROOT) }))
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e(TAG, "Error searching 4PDA crawler", error)
        emit(Result.failure(error))
    }

    suspend fun loadPostNotes(postId: Long): String? = withContext(Dispatchers.IO) {
        if (postId <= 0L) return@withContext null
        postNotesCache[postId]?.let { cached ->
            return@withContext cached.takeIf { it.isNotBlank() }
        }
        val fromServer = runCatching { fetchPost(DEFAULT_SERVER_URL, postId)?.whatsNew.orEmpty() }.getOrDefault("")
        val cleaned = if (fromServer.isNotBlank() && !isFourPdaTopicHeader(fromServer)) {
            cleanFourPdaPostNotes(fromServer)
        } else {
            fetchPostDirectlyFromFourPda(postId).orEmpty()
        }
        if (cleaned.isNotBlank()) {
            postNotesCache[postId] = cleaned
            cleaned
        } else {
            null
        }
    }

    private fun fetchPostDirectlyFromFourPda(postId: Long): String? {
        fun fetchHtml(url: String): Pair<String, String>? {
            val cookie = session.cookieHeaderFor(url).orEmpty()
            val req = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
                .header("Referer", "https://4pda.to/forum/")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .get()
                .build()
            return runCatching {
                apiClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val html = String(bytes, FOURPDA_CHARSET)
                    html to resp.request.url.toString()
                }
            }.getOrNull()
        }

        val (html, finalUrl) = fetchHtml("https://4pda.to/forum/index.php?act=findpost&pid=$postId")
            ?: return null
        parseFourPdaPostHtml(html, postId)?.let { return it }

        if (finalUrl.contains("showtopic=", ignoreCase = true)) {
            val base = finalUrl.substringBefore('#')
            val sep = if ('?' in base) '&' else '?'
            val (retryHtml, _) = fetchHtml("$base${sep}_=$postId") ?: return null
            return parseFourPdaPostHtml(retryHtml, postId)
        }
        return null
    }

    suspend fun detectTopic(input: String): FourPdaCrawlerTopic? = withContext(Dispatchers.IO) {
        val topicId = parseFourPdaTopicId(input) ?: return@withContext null
        runCatching { fetchTopic(DEFAULT_SERVER_URL, topicId, refresh = false) }.getOrNull()
    }

    suspend fun checkServerHealth(): Result<FourPdaHealthResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = getJson("$DEFAULT_SERVER_URL/health")
                gson.fromJson(body, FourPdaHealthResponse::class.java)
                    ?: throw IOException("Empty response")
            }
        }

    private fun fetchBulkUpdates(baseUrl: String, packages: List<String>): Map<String, List<FourPdaCrawlerTopic>> {
        val result = LinkedHashMap<String, List<FourPdaCrawlerTopic>>()
        packages.distinct().chunked(400).forEach { chunk ->
            val payload = gson.toJson(FourPdaUpdatesRequest(packages = chunk))
            val request = Request.Builder()
                .url("$baseUrl/api/v1/updates")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("4PDA crawler returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = gson.fromJson(body, FourPdaUpdatesResponse::class.java)
                parsed?.matches?.forEach { (pkg, topics) ->
                    result[pkg.lowercase(Locale.ROOT)] = topics
                }
            }
        }
        return result
    }

    private fun fetchTopic(baseUrl: String, topicId: Long, refresh: Boolean): FourPdaCrawlerTopic? {
        val url = "$baseUrl/api/v1/topics/$topicId" + if (refresh) "?refresh=true" else ""
        val body = getJson(url)
        return gson.fromJson(body, FourPdaCrawlerTopic::class.java)
    }

    private fun fetchPost(baseUrl: String, postId: Long): FourPdaPostResponse? {
        val body = getJson("$baseUrl/api/v1/posts/$postId")
        return gson.fromJson(body, FourPdaPostResponse::class.java)
    }

    private fun getJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        apiClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun shouldInclude(type: ReleaseType): Boolean = when (type) {
        ReleaseType.Alpha -> !prefs.ignoreAlpha.get()
        ReleaseType.Beta -> !prefs.ignoreBeta.get()
        ReleaseType.PreRelease -> !prefs.ignorePreRelease.get()
        ReleaseType.Stable -> true
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://4pda-api2.notdev.top"
        private const val TAG = "FourPdaRepository"
        private const val MAX_SEARCH_RESULTS = 20
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FOURPDA_CHARSET: Charset = Charset.forName("windows-1251")
    }
}

private val TOPIC_ID_URL_REGEX = Regex("""showtopic=(\d+)""", RegexOption.IGNORE_CASE)

internal fun parseFourPdaTopicId(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
    return TOPIC_ID_URL_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
}

internal fun normalizeCrawlerBaseUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val parsed = withScheme.toHttpUrlOrNull() ?: return null
    if (parsed.scheme != "http" && parsed.scheme != "https") return null
    return parsed.toString().trimEnd('/')
}

internal fun isFourPdaUpdate(remoteVersion: String, installedVersion: String): Boolean {
    val remoteClean = filterVersionTag(remoteVersion.trim())
    val installedClean = filterVersionTag(installedVersion.trim())
    if (remoteClean.isBlank() || installedClean.isBlank()) return false
    return runCatching { Version(remoteClean) > Version(installedClean) }.getOrDefault(false)
}

internal fun selectFourPdaCrawlerAttachment(
    attachments: List<FourPdaCrawlerAttachment>,
    supportedAbis: List<String>,
    preference: FourPdaBuildPreference = FourPdaBuildPreference.ASK,
    fileFilter: String = ""
): FourPdaCrawlerAttachment? {
    val installables = filterInstallableAttachments(attachments, fileFilter)
    if (installables.isEmpty()) return null

    val afterPreference = if (fileFilter.trim().isNotEmpty()) {
        installables
    } else {
        when (preference) {
            FourPdaBuildPreference.ORIGINALS, FourPdaBuildPreference.ASK ->
                installables.filter { !it.isMod }.ifEmpty { installables }
            FourPdaBuildPreference.MODS ->
                installables.filter { it.isMod }.ifEmpty { installables }
        }
    }

    // Keep only attachments from the newest release group (same version or newest attachId window)
    val newestVersion = afterPreference.firstNotNullOfOrNull { filterVersionTag(it.version).takeIf(String::isNotBlank) }
    val latestPool = if (!newestVersion.isNullOrBlank()) {
        afterPreference.filter { item ->
            val v = filterVersionTag(item.version)
            v.isBlank() || v.equals(newestVersion, ignoreCase = true)
        }.ifEmpty { afterPreference }
    } else {
        afterPreference.take(6)
    }

    return AbiMatcher.selectCompatible(
        items = latestPool,
        supportedAbis = supportedAbis,
        nameSelector = FourPdaCrawlerAttachment::fileName,
        sizeSelector = FourPdaCrawlerAttachment::sizeBytes
    ) ?: latestPool.firstOrNull()
}

internal fun buildFourPdaDownloadChoices(
    attachments: List<FourPdaCrawlerAttachment>,
    supportedAbis: List<String>,
    expectedPackageName: String?,
    topicId: Long = 0L,
    fileFilter: String = "",
    maxChoices: Int = 30
): List<DownloadChoice> {
    val installables = filterInstallableAttachments(attachments, fileFilter)
        .filter { it.downloadUrl.startsWith("https://", ignoreCase = true) }
    if (installables.size < 2) return emptyList()

    // Strictly newest-to-oldest (4PDA attachId is monotonically increasing with upload time)
    val selected = installables
        .distinctBy { it.downloadUrl }
        .sortedByDescending { it.attachId }
        .take(maxChoices)

    if (selected.size < 2) return emptyList()

    return selected.map { item ->
        val postUrl = if (item.postId > 0L && topicId > 0L) {
            "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=${item.postId}"
        } else {
            ""
        }
        DownloadChoice(
            label = item.fileName,
            link = Link.Url(
                link = item.downloadUrl,
                size = item.sizeBytes,
                expectedPackageName = expectedPackageName
            ),
            version = item.version,
            sizeBytes = item.sizeBytes,
            uploadedAt = item.uploadedAt,
            abi = item.abi,
            isMod = item.isMod,
            postId = item.postId,
            postUrl = postUrl
        )
    }
}

private val FOURPDA_FIELD_LABEL_REGEX = Regex(
    """^(?:тип|версия|краткое\s+описание|дата(?:\s+выхода)?|автор(?:\s+мода)?|мод\s+от|разработчик|язык|интерфейс|русский\s+интерфейс|требуется\s+android|android)\s*:$""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_SIZE_ONLY_REGEX = Regex(
    """^\(?\s*[\d.,]+\s*(?:Б|КБ|МБ|ГБ|B|KB|MB|GB)\s*\)?$""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_TRAILING_SIZE_REGEX = Regex(
    """\s*\(\s*[\d.,]+\s*(?:Б|КБ|МБ|ГБ|B|KB|MB|GB)\s*\)\s*$""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_ARCH_ONLY_REGEX = Regex(
    """^(?:universal|noarch|arm64(?:-v8a)?|armeabi(?:-v7a)?|x86(?:_64)?|v7a|v8a|apk|apks|xapk|split|orig|original|оригинал|мод|mod)\s*:$""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_NOTE_HEADER_REGEX = Regex(
    """^(?:примечание|важно|установка|инструкция\s+по\s+установке)\s*:?$""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_SECTION_HEADER_REGEX = Regex(
    """^(?:список\s+изменений|что\s+нового|изменения(?:\s+в\s+(?:версии|моде))?|описание\s+мода|особенности\s+мода|информация\s+о\s+моде|модификация|особенности|что\s+изменено|changelog|what'?s\s+new|mod\s+(?:info|features)|features|release\s+notes)\s*:?$""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_BOILERPLATE_HEADERS = setOf(
    "прикрепленные файлы",
    "прикреплённые файлы",
    "скачать",
    "download"
)
private val FOURPDA_PUNCT_START_REGEX = Regex("""^[,.;:!?\)]""")
private val FOURPDA_SENTENCE_END_REGEX = Regex("""[.!?;:]$""")
private val FOURPDA_BULLET_START_REGEX = Regex("""^[•\-*+✓✔\d]""")

private val FOURPDA_POST_TYPE_LINE_REGEX = Regex(
    """^\s*тип\s*:\s*(.+?)\s*$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
)
private val FOURPDA_SHORT_DESC_LINE_REGEX = Regex(
    """^\s*краткое\s+описание\s*:\s*(.+?)\s*$""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
)
private val FOURPDA_ORIGINAL_TYPE_REGEX = Regex(
    """^(?:новая\s+версия|оригинал|официальная|релиз|обновление|new\s+version|original|official|beta|бета(?:\s*-?\s*версия)?|alpha|альфа(?:\s*-?\s*версия)?|тестовая(?:\s+версия)?|test|pre[\s-]?release|prerelease|rc|canary|nightly)(?=$|[\s.,;:/(\-])""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_POST_BODY_MOD_REGEX = Regex(
    """(?:(?<![A-Za-z])unlock(?:ed)?(?![A-Za-z])|(?<![A-Za-z])patched(?![A-Za-z])|(?<![A-Za-z])revanced(?![A-Za-z])|(?<![A-Za-z])adfree(?![A-Za-z])|(?<![A-Za-z])noads(?![A-Za-z])|(?<![A-Za-z])mod\s+by\b|автор\s+мода|мод\s+от|особенности\s+мода|описание\s+мода|информация\s+о\s+моде|разблокирован\w*|удалена\s+реклама|вырезана\s+реклама|без\s+рекламы)""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_ALPHA_RU_REGEX = Regex(
    """(?:^|[^\p{L}0-9])(?:альфа|alpha)(?=$|[^\p{L}]|\d)""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_BETA_RU_REGEX = Regex(
    """(?:^|[^\p{L}0-9])(?:бета|beta|тестовая)(?=$|[^\p{L}]|\d)""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_PRERELEASE_RU_REGEX = Regex(
    """(?:^|[^\p{L}0-9])(?:pre[\s_-]?release|prerelease|preview|rc\d*|canary|nightly)(?=$|[^\p{L}0-9])""",
    RegexOption.IGNORE_CASE
)
private val FOURPDA_LABEL_MOD_HINT_REGEX = Regex(
    """(?:(?<![A-Za-z])mod(?![A-Za-z])|мод|модификация|patch|патч|patched|crack|premium|unlock(?:ed)?|(?<![A-Za-z])lite(?![A-Za-z])|clone|клон|revanced|adfree|noads|repack|репак)""",
    RegexOption.IGNORE_CASE
)

private val FOURPDA_FILE_EXT_REGEX = Regex("""\.(?:apk|apks|xapk|apkm|zip)$""", RegexOption.IGNORE_CASE)
private val FOURPDA_FILE_VERSION_REGEX = Regex(
    """(?<![A-Za-z0-9])(?:v\.?\s*)?(\d+(?:\.\d+){1,4}(?:[._\-]?(?:alpha|beta|rc|prod)\d*)?)(?![0-9])""",
    RegexOption.IGNORE_CASE
)

internal fun extractFourPdaFileVersion(fileName: String): String {
    if (fileName.isBlank()) return ""
    val stripped = FOURPDA_FILE_EXT_REGEX.replace(fileName.substringBefore('?'), "")
    return FOURPDA_FILE_VERSION_REGEX.find(stripped)?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

internal fun detectFourPdaModFromPostNotes(notes: String): Boolean? {
    if (notes.isBlank()) return null
    if (FOURPDA_POST_BODY_MOD_REGEX.containsMatchIn(notes)) return true
    val shortDesc = FOURPDA_SHORT_DESC_LINE_REGEX.find(notes)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (shortDesc.isNotEmpty() && FOURPDA_LABEL_MOD_HINT_REGEX.containsMatchIn(shortDesc)) {
        return true
    }
    val typeValue = FOURPDA_POST_TYPE_LINE_REGEX.find(notes)?.groupValues?.getOrNull(1)?.trim()
    if (typeValue.isNullOrEmpty()) return null
    return !FOURPDA_ORIGINAL_TYPE_REGEX.containsMatchIn(typeValue)
}

internal fun detectFourPdaReleaseType(
    version: String = "",
    fileName: String = "",
    releaseLabel: String = "",
    releaseVersion: String = "",
    postNotes: String = ""
): ReleaseType {
    val typeLine = FOURPDA_POST_TYPE_LINE_REGEX.find(postNotes)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (typeLine.isNotEmpty()) {
        when {
            FOURPDA_ALPHA_RU_REGEX.containsMatchIn(typeLine) -> return ReleaseType.Alpha
            FOURPDA_BETA_RU_REGEX.containsMatchIn(typeLine) -> return ReleaseType.Beta
            FOURPDA_PRERELEASE_RU_REGEX.containsMatchIn(typeLine) -> return ReleaseType.PreRelease
        }
    }
    val combined = listOf(version, fileName, releaseLabel, releaseVersion)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (combined.isBlank()) return ReleaseType.Stable
    val fromBase = ReleaseType.from(version, "$fileName $releaseLabel $releaseVersion")
    if (fromBase != ReleaseType.Stable) return fromBase
    return when {
        FOURPDA_ALPHA_RU_REGEX.containsMatchIn(combined) -> ReleaseType.Alpha
        FOURPDA_BETA_RU_REGEX.containsMatchIn(combined) -> ReleaseType.Beta
        FOURPDA_PRERELEASE_RU_REGEX.containsMatchIn(combined) -> ReleaseType.PreRelease
        else -> ReleaseType.Stable
    }
}

internal fun resolveAttachmentMetadata(
    attachments: List<FourPdaCrawlerAttachment>,
    releaseLinks: List<FourPdaCrawlerReleaseLink>,
    postNotesCache: Map<Long, String> = emptyMap()
): List<FourPdaCrawlerAttachment> {
    if (attachments.isEmpty()) return attachments
    val modByPostId = HashMap<Long, Boolean>()
    val versionByPostId = HashMap<Long, String>()
    releaseLinks.forEach { link ->
        if (link.postId > 0L) {
            val linkIsMod = link.isMod ||
                FOURPDA_LABEL_MOD_HINT_REGEX.containsMatchIn(link.label) ||
                FOURPDA_LABEL_MOD_HINT_REGEX.containsMatchIn(link.version)
            if (linkIsMod) modByPostId[link.postId] = true
            val cleanLinkVer = extractFourPdaFileVersion(link.version)
            if (cleanLinkVer.isNotBlank() && !versionByPostId.containsKey(link.postId)) {
                versionByPostId[link.postId] = cleanLinkVer
            }
        }
    }
    return attachments.map { item ->
        val fromPost = if (item.postId > 0L) postNotesCache[item.postId]?.let(::detectFourPdaModFromPostNotes) else null
        val resolvedMod = when {
            fromPost != null -> fromPost
            item.isMod -> true
            FOURPDA_LABEL_MOD_HINT_REGEX.containsMatchIn(item.fileName) -> true
            item.postId > 0L && modByPostId[item.postId] == true -> true
            else -> false
        }
        val fileVer = extractFourPdaFileVersion(item.fileName)
        val resolvedVersion = when {
            fileVer.isNotBlank() -> fileVer
            item.postId > 0L && !versionByPostId[item.postId].isNullOrBlank() -> versionByPostId[item.postId]!!
            else -> item.version
        }
        if (resolvedMod == item.isMod && resolvedVersion == item.version) {
            item
        } else {
            item.copy(isMod = resolvedMod, version = resolvedVersion)
        }
    }
}

internal fun isFourPdaTopicHeader(text: String): Boolean {
    val low = text.lowercase(Locale.ROOT)
    return "последнее обновление программы в шапке" in low ||
        ("скриншоты" in low && "описание:" in low && "краткое описание:" in low)
}

internal fun parseFourPdaPostHtml(html: String, postId: Long): String? {
    val doc = Jsoup.parse(html)
    val postBody = doc.getElementById("post-main-$postId")
        ?: doc.selectFirst("div[data-post=$postId] div.post_body")
        ?: return null
    val clone = postBody.clone()
    clone.select("a.attach-file, a[href*=/dl/post/], span.edit, div.post-block.quote, img").remove()
    clone.select("br").forEach { it.before("\n"); it.remove() }
    clone.select("li").forEach { li ->
        val txt = li.text().trim()
        if (txt.isNotEmpty() && !txt.startsWith("•") && !txt.startsWith("-") && !txt.startsWith("*")) {
            li.prependText("• ")
        }
        li.before("\n")
        li.after("\n")
    }
    clone.select("p, div, tr, h1, h2, h3, h4, h5, h6, hr").forEach { block ->
        block.before("\n")
        block.after("\n")
    }
    val cleaned = cleanFourPdaPostNotes(clone.wholeText())
    return cleaned.takeIf { it.isNotBlank() }
}

internal fun cleanFourPdaPostNotes(raw: String): String {
    if (raw.isBlank()) return ""
    val rawLines = raw.replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { it.replace('\u00A0', ' ').replace(Regex("""[ \t]+"""), " ").trim() }
        .filter { it.isNotEmpty() }
    if (rawLines.isEmpty()) return ""

    // Step 1: Repair legacy crawler output where inline HTML tags were split onto separate lines
    val merged = mutableListOf<String>()
    var i = 0
    while (i < rawLines.size) {
        val current = rawLines[i]
        val next = rawLines.getOrNull(i + 1)
        if (FOURPDA_FIELD_LABEL_REGEX.matches(current) &&
            next != null &&
            !next.endsWith(":") &&
            !FOURPDA_FIELD_LABEL_REGEX.matches(next) &&
            !FOURPDA_NOTE_HEADER_REGEX.matches(next)
        ) {
            merged.add("$current $next")
            i += 2
            continue
        }
        if (merged.isNotEmpty()) {
            val prev = merged.last()
            val startsWithPunct = FOURPDA_PUNCT_START_REGEX.containsMatchIn(current)
            val isShortInlineContinuation =
                !FOURPDA_FIELD_LABEL_REGEX.matches(current) &&
                    !FOURPDA_SECTION_HEADER_REGEX.matches(current) &&
                    !FOURPDA_NOTE_HEADER_REGEX.matches(current) &&
                    !FOURPDA_SIZE_ONLY_REGEX.matches(current) &&
                    !FOURPDA_ARCH_ONLY_REGEX.matches(current) &&
                    !FOURPDA_BULLET_START_REGEX.containsMatchIn(current) &&
                    current.first().isLowerCase() &&
                    !FOURPDA_SENTENCE_END_REGEX.containsMatchIn(prev) &&
                    !FOURPDA_FIELD_LABEL_REGEX.matches(prev) &&
                    !FOURPDA_SECTION_HEADER_REGEX.matches(prev)
            if (startsWithPunct) {
                merged[merged.lastIndex] = "$prev$current"
                i++
                continue
            } else if (isShortInlineContinuation) {
                merged[merged.lastIndex] = "$prev $current"
                i++
                continue
            }
        }
        merged.add(current)
        i++
    }

    // Step 2: Strip only file attachment sizes, bare arch headings, and empty bullets; keep the rest of the post intact
    val cleaned = mutableListOf<String>()
    for (rawLine in merged) {
        val normalized = rawLine
            .replace(Regex("""\s+([,.;:!?)])"""), "$1")
            .replace(Regex("""\(\s+"""), "(")
            .replace(FOURPDA_TRAILING_SIZE_REGEX, "")
            .trim()
        if (normalized.isEmpty()) continue
        val lowHeader = normalized.trimEnd(':').trim().lowercase(Locale.ROOT)
        if (lowHeader in FOURPDA_BOILERPLATE_HEADERS) continue
        if (FOURPDA_SIZE_ONLY_REGEX.matches(normalized) || FOURPDA_ARCH_ONLY_REGEX.matches(normalized)) continue
        if (normalized in setOf("•", "-", "*", "®", "©")) continue
        cleaned.add(normalized)
    }

    return cleaned.joinToString("\n").trim()
}

private fun filterInstallableAttachments(
    attachments: List<FourPdaCrawlerAttachment>,
    fileFilter: String
): List<FourPdaCrawlerAttachment> {
    val installables = attachments.filter { item ->
        val clean = item.fileName.substringBefore('?').lowercase(Locale.ROOT)
        clean.endsWith(".apk") || clean.endsWith(".apks") || clean.endsWith(".xapk") || clean.endsWith(".apkm")
    }
    val trimmedFilter = fileFilter.trim()
    if (trimmedFilter.isEmpty()) return installables
    val regex = runCatching { Regex(trimmedFilter, RegexOption.IGNORE_CASE) }.getOrNull()
    return installables.filter { item ->
        item.fileName.contains(trimmedFilter, ignoreCase = true) ||
            (regex != null && regex.containsMatchIn(item.fileName))
    }.ifEmpty { installables }
}
