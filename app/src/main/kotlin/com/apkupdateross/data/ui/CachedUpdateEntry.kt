package com.apkupdateross.data.ui

import com.apkupdateross.R

data class CachedUpdateEntry(
	val name: String = "",
	val packageName: String = "",
	val version: String = "",
	val oldVersion: String = "",
	val versionCode: Long = 0L,
	val oldVersionCode: Long = 0L,
	val sourceName: String = "",
	val sourceUrl: String = "",
	val releaseUrl: String = "",
	val whatsNew: String = "",
	val releaseTypeName: String = ReleaseType.Stable.name,
	val isPaid: Boolean = false,
	val linkType: String = "EMPTY",
	val linkUrl: String = "",
	val linkSize: Long = 0L,
	val expectedPackageName: String? = null,
	val sha256: String? = null,
	val isXapk: Boolean = false
)

fun AppUpdate.toCachedEntry(): CachedUpdateEntry {
	val (linkType, linkUrl, linkSize, expectedPkg, sha, isXapk) = when (val l = link) {
		is Link.Url -> if (l.resolveUrl == null && l.requestHeaders.isEmpty()) {
			LinkSnapshot("URL", l.link, l.size, l.expectedPackageName, l.sha256, false)
		} else {
			LinkSnapshot("EMPTY", "", l.size, null, null, false)
		}
		is Link.Xapk -> LinkSnapshot("XAPK", l.link, 0L, null, null, true)
		is Link.BrowserDownload -> LinkSnapshot("BROWSER", l.pageUrl, 0L, l.expectedPackageName, l.sha256, l.isXapk)
		else -> LinkSnapshot("EMPTY", "", 0L, null, null, false)
	}
	return CachedUpdateEntry(
		name = name,
		packageName = packageName,
		version = version,
		oldVersion = oldVersion,
		versionCode = versionCode,
		oldVersionCode = oldVersionCode,
		sourceName = source.name,
		sourceUrl = sourceUrl,
		releaseUrl = releaseUrl,
		whatsNew = whatsNew,
		releaseTypeName = releaseType.name,
		isPaid = isPaid,
		linkType = linkType,
		linkUrl = linkUrl,
		linkSize = linkSize,
		expectedPackageName = expectedPkg,
		sha256 = sha,
		isXapk = isXapk
	)
}

fun CachedUpdateEntry.toAppUpdate(): AppUpdate {
	val source = restoreSource()
	val releaseType = runCatching { ReleaseType.valueOf(releaseTypeName) }.getOrDefault(ReleaseType.Stable)
	val restoredLink: Link = restoreLink()
	return AppUpdate(
		name = name,
		packageName = packageName,
		version = version,
		oldVersion = oldVersion,
		versionCode = versionCode,
		oldVersionCode = oldVersionCode,
		source = source,
		link = restoredLink,
		sourceUrl = sourceUrl,
		releaseUrl = releaseUrl,
		whatsNew = whatsNew,
		releaseType = releaseType,
		isPaid = isPaid
	)
}

internal fun CachedUpdateEntry.restoreLink(): Link = when (linkType) {
	"URL" -> if (linkUrl.isNotBlank()) Link.Url(linkUrl, linkSize, expectedPackageName, sha256) else Link.Empty
	"XAPK" -> if (linkUrl.isNotBlank()) Link.Xapk(linkUrl) else Link.Empty
	"BROWSER" -> if (linkUrl.isNotBlank()) Link.BrowserDownload(linkUrl, expectedPackageName, sha256, null, isXapk) else Link.Empty
	else -> Link.Empty
}

internal fun CachedUpdateEntry.restoreSource(): Source = sourceForName(sourceName)

private fun sourceForName(name: String): Source = when (name) {
	ApkMirrorSource.name -> ApkMirrorSource
	GitHubSource.name -> GitHubSource
	CodebergSource.name -> CodebergSource
	FdroidSource.name -> FdroidSource
	IzzySource.name -> IzzySource
	AptoideSource.name -> AptoideSource
	ApkPureSource.name -> ApkPureSource
	Apk20Source.name -> Apk20Source
	UptodownSource.name -> UptodownSource
	GitLabSource.name -> GitLabSource
	PlaySource.name -> PlaySource
	RuStoreSource.name -> RuStoreSource
	HuaweiSource.name -> HuaweiSource
	SamsungGalaxyStoreSource.name -> SamsungGalaxyStoreSource
	else -> Source(name, R.drawable.ic_fdroid)
}

private data class LinkSnapshot(
	val type: String,
	val url: String,
	val size: Long,
	val expectedPackageName: String?,
	val sha256: String?,
	val isXapk: Boolean
)
