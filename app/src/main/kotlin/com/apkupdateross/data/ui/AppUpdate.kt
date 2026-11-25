package com.apkupdateross.data.ui

import android.net.Uri
import io.github.g00fy2.versioncompare.Version

data class AppUpdate(
	val name: String,
	val packageName: String,
	val version: String,
	val oldVersion: String,
	val versionCode: Long,
	val oldVersionCode: Long,
	val source: Source,
	val iconUri: Uri = Uri.EMPTY,
	val link: Link = Link.Empty,
	val sourceUrl: String = "",
	val releaseUrl: String = "",
	val whatsNew: String = "",
	val releaseType: ReleaseType = ReleaseType.from(version),
	val isPaid: Boolean = false,
	val isInstalling: Boolean = false,
	val isDownloading: Boolean = false,
	val total: Long = 0L,
	val progress: Long = 0L,
	val id: Int = "${source.name}.$packageName.$versionCode.$version".hashCode()
)

fun List<AppUpdate>.indexOf(id: Int) = indexOfFirst { it.id == id }

fun List<AppUpdate>.setIsInstalling(id: Int, b: Boolean): List<AppUpdate> = map {
	if (it.id == id) it.copy(isInstalling = b, progress = if (b) 0L else it.progress, total = if (b) 0L else it.total) else it
}

fun List<AppUpdate>.setIsDownloading(id: Int, b: Boolean): List<AppUpdate> = map {
	if (it.id == id) it.copy(isDownloading = b, progress = if (b) 0L else it.progress, total = if (b) 0L else it.total) else it
}

fun List<AppUpdate>.removeId(id: Int): List<AppUpdate> = filter { it.id != id }

fun List<AppUpdate>.removePackageName(packageName: String): List<AppUpdate> = filter { it.packageName != packageName }

fun List<AppUpdate>.setProgress(progress: AppInstallProgress): List<AppUpdate> = map {
	if (it.id == progress.id) it.copy(progress = progress.progress ?: it.progress, total = progress.total ?: it.total) else it
}

val preferredUpdateComparator: Comparator<AppUpdate> = updateComparator()

fun updateComparator(
	sourcePriorityOrder: List<String> = emptyList(),
	pinnedSourceName: String? = null
): Comparator<AppUpdate> = Comparator { first, second ->
	compareUpdatePreference(
		firstVersion = first.version,
		firstVersionCode = first.versionCode,
		firstSource = first.source,
		secondVersion = second.version,
		secondVersionCode = second.versionCode,
		secondSource = second.source,
		sourcePriorityOrder = sourcePriorityOrder,
		pinnedSourceName = pinnedSourceName
	)
}

internal fun compareUpdatePreference(
	firstVersion: String,
	firstVersionCode: Long,
	firstSource: Source,
	secondVersion: String,
	secondVersionCode: Long,
	secondSource: Source,
	sourcePriorityOrder: List<String> = emptyList(),
	pinnedSourceName: String? = null
): Int {
	if (!pinnedSourceName.isNullOrBlank()) {
		val firstPinned = firstSource.name == pinnedSourceName
		val secondPinned = secondSource.name == pinnedSourceName
		if (firstPinned != secondPinned) {
			return if (firstPinned) -1 else 1
		}
	}
	val versionComparison = runCatching {
		Version(secondVersion).compareTo(Version(firstVersion))
	}.getOrNull()
	return if (versionComparison != null && versionComparison != 0) {
		versionComparison
	} else {
		val sourceComparison = secondSource.priority(customOrder = sourcePriorityOrder)
			.compareTo(firstSource.priority(customOrder = sourcePriorityOrder))
		if (sourceComparison != 0) sourceComparison
		else secondVersionCode.compareTo(firstVersionCode)
	}
}
