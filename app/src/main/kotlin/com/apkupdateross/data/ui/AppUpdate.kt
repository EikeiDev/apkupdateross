package com.apkupdateross.data.ui

import android.net.Uri

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
