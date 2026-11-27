package com.apkupdateross.data.fourpda

data class FourPdaTopicBinding(
    val packageName: String = "",
    val topicId: Long = 0L,
    val topicTitle: String = "",
    val fileFilter: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun normalized(): FourPdaTopicBinding = copy(
        packageName = packageName.trim(),
        topicTitle = topicTitle.trim(),
        fileFilter = fileFilter.trim()
    )
}

enum class FourPdaBuildPreference {
    ASK,
    ORIGINALS,
    MODS;

    companion object {
        fun fromIndex(index: Int): FourPdaBuildPreference =
            entries.getOrElse(index) { ASK }
    }
}

data class FourPdaCrawlerAttachment(
    val attachId: Long = 0L,
    val fileName: String = "",
    val downloadUrl: String = "",
    val sizeBytes: Long = 0L,
    val uploadedAt: String = "",
    val postId: Long = 0L,
    val version: String = "",
    val isMod: Boolean = false,
    val abi: String = ""
)

data class FourPdaCrawlerReleaseLink(
    val postId: Long = 0L,
    val version: String = "",
    val label: String = "",
    val url: String = "",
    val isMod: Boolean = false
)

data class FourPdaCrawlerTopic(
    val topicId: Long = 0L,
    val forumId: Long = 0L,
    val title: String = "",
    val description: String = "",
    val headerVersion: String = "",
    val latestVersion: String = "",
    val iconUrl: String = "",
    val topicUrl: String = "",
    val packageNames: List<String> = emptyList(),
    val releaseLinks: List<FourPdaCrawlerReleaseLink> = emptyList(),
    val attachments: List<FourPdaCrawlerAttachment> = emptyList(),
    val status: String = "",
    val lastCheckedAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FourPdaUpdatesRequest(
    val packages: List<String>
)

data class FourPdaUpdatesResponse(
    val matches: Map<String, List<FourPdaCrawlerTopic>> = emptyMap(),
    val matchedCount: Int = 0
)

data class FourPdaSearchResponse(
    val query: String = "",
    val items: List<FourPdaCrawlerTopic> = emptyList(),
    val total: Int = 0
)

data class FourPdaPostResponse(
    val postId: Long = 0L,
    val version: String = "",
    val whatsNew: String = "",
    val attachments: List<FourPdaCrawlerAttachment> = emptyList(),
    val fetchedAt: Long = 0L
)

data class FourPdaHealthResponse(
    val status: String = "",
    val version: String = "",
    val index: FourPdaHealthIndex = FourPdaHealthIndex()
)

data class FourPdaHealthIndex(
    val activeTopics: Int = 0,
    val pendingTopics: Int = 0,
    val indexedPackages: Int = 0,
    val trackedForums: Int = 0
)

data class FourPdaAuthChallenge(
    val captchaTime: String,
    val captchaSig: String,
    val captchaImageUrl: String,
    val captchaImageBytes: ByteArray = ByteArray(0),
    val sessionCookies: String = ""
)

sealed interface FourPdaLoginResult {
    data class Success(val memberId: String) : FourPdaLoginResult
    data class Failure(
        val message: String,
        val refreshedChallenge: FourPdaAuthChallenge? = null
    ) : FourPdaLoginResult
}
