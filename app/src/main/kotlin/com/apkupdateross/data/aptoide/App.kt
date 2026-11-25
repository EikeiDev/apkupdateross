package com.apkupdateross.data.aptoide

import android.net.Uri
import androidx.core.net.toUri
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.AptoideSource
import com.apkupdateross.data.ui.Link
import com.google.gson.annotations.SerializedName
import java.util.Locale

data class App(
	val name: String = "",
	@SerializedName("package") val packageName:String = "",
	val icon: String? = "",
	val file: File,
	val store: Store,
	val uname: String? = null
)

private fun currentAptoideLang(): String {
	val lang = Locale.getDefault().language
	return if (lang.isNullOrBlank()) "en" else lang.lowercase()
}

private fun buildAptoideUrl(store: Store, uname: String?, packageName: String): String {
	val hostBase = uname?.takeIf { it.isNotBlank() }
		?: store.name.takeIf { it.isNotBlank() }
		?: packageName
	val lang = currentAptoideLang()
	return "https://$hostBase.$lang.aptoide.com/app"
}

fun App.toAppUpdate(app: AppInstalled?) = AppUpdate(
	name = name,
	packageName = packageName,
	version = file.vername,
	oldVersion = app?.version ?: "?",
	versionCode = file.vercode.toLong(),
	oldVersionCode = app?.versionCode ?: 0L,
	source = AptoideSource,
	iconUri = icon?.toUri() ?: Uri.EMPTY,
	link = Link.Url(file.path),
	sourceUrl = buildAptoideUrl(store, uname, packageName),
	releaseUrl = buildAptoideUrl(store, uname, packageName)
)
