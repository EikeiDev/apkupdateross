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

fun MutableList<AppUpdate>.setIsInstalling(id: Int, b: Boolean): MutableList<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) {
		this[index] = this[index].copy(
			isInstalling = b,
			progress = if (b) 0L else this[index].progress,
			total = if (b) 0L else this[index].total
		)
	}
	return this
}

fun MutableList<AppUpdate>.setIsDownloading(id: Int, b: Boolean): MutableList<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) {
		this[index] = this[index].copy(
			isDownloading = b,
			progress = if (b) 0L else this[index].progress,
			total = if (b) 0L else this[index].total
		)
	}
	return this
}

fun MutableList<AppUpdate>.removeId(id: Int): MutableList<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) this.removeAt(index)
	return this
}

fun MutableList<AppUpdate>.removePackageName(packageName: String): MutableList<AppUpdate> {
	this.removeAll { it.packageName == packageName }
	return this
}

fun MutableList<AppUpdate>.setProgress(progress: AppInstallProgress): MutableList<AppUpdate> {
	val index = this.indexOf(progress.id)
	if (index != -1) {
		val current = this[index]
		this[index] = current.copy(
			progress = progress.progress ?: current.progress,
			total = progress.total ?: current.total
		)
	}
	return this
}
