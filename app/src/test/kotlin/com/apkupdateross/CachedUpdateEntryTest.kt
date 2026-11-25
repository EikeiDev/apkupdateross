package com.apkupdateross

import com.apkupdateross.data.ui.CachedUpdateEntry
import com.apkupdateross.data.ui.GitHubSource
import com.apkupdateross.data.ui.Link
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.data.ui.restoreLink
import com.apkupdateross.data.ui.restoreSource
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedUpdateEntryTest {

	@Test
	fun roundTripsLinksAndMetadata() {
		val gson = Gson()
		val urlEntry = CachedUpdateEntry(
			name = "Example",
			packageName = "com.example.app",
			version = "2.0.0-beta1",
			oldVersion = "1.0.0",
			versionCode = 200L,
			oldVersionCode = 100L,
			sourceName = GitHubSource.name,
			linkType = "URL",
			linkUrl = "https://example.com/app.apk",
			linkSize = 123456L,
			expectedPackageName = "com.example.app",
			sha256 = "abc",
			whatsNew = "Bug fixes",
			releaseTypeName = ReleaseType.Beta.name
		)
		val restoredEntry = gson.fromJson(gson.toJson(urlEntry), CachedUpdateEntry::class.java)
		assertEquals(GitHubSource, restoredEntry.restoreSource())
		assertEquals(
			Link.Url("https://example.com/app.apk", size = 123456L, expectedPackageName = "com.example.app", sha256 = "abc"),
			restoredEntry.restoreLink()
		)

		val xapkEntry = urlEntry.copy(linkType = "XAPK", linkUrl = "https://example.com/app.xapk", isXapk = true)
		assertEquals(Link.Xapk("https://example.com/app.xapk"), xapkEntry.restoreLink())

		val emptyEntry = urlEntry.copy(linkType = "EMPTY", linkUrl = "")
		assertEquals(Link.Empty, emptyEntry.restoreLink())
	}
}
