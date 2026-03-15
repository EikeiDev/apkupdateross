package com.apkupdateross.data.apkmirror

import com.apkupdateross.data.ui.ApkMirrorSource
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.google.gson.annotations.SerializedName

private const val APKMIRROR_BASE_URL = "https://www.apkmirror.com"

data class AppExistsResponseApk(
	@SerializedName("version_code") val versionCode: Long = 0,
	val link: String = "",
	@SerializedName("publish_date") val publishDate: String? = null,
	val arches: List<String> = emptyList(),
	val dpis: List<String>? = null,
	val minapi: String = "0",
	val description: String? = null,
	val capabilities: List<String>? = null,
	@SerializedName("signatures-sha1")
	val signaturesSha1: List<String>? = emptyList(),
	@SerializedName("signatures-sha256")
	val signaturesSha256: List<String>? = emptyList()
)

fun AppExistsResponseApk.toAppUpdate(
	app: AppInstalled,
	release: AppExistsResponseRelease,
	appLink: String? = null
) = AppUpdate(
	name = app.name,
	packageName = app.packageName,
	version = release.version,
	oldVersion = app.version,
	versionCode = versionCode,
	oldVersionCode = app.versionCode,
	source = ApkMirrorSource,
	iconUri = app.iconUri,
	link = Link.Url(link.ensureApkMirrorUrl()),
	sourceUrl = appLink.ensureApkMirrorUrl(),
	releaseUrl = release.link.ensureApkMirrorUrl(),
	whatsNew = release.whatsNew.orEmpty()
)

private fun String?.ensureApkMirrorUrl(): String {
	if (this.isNullOrBlank()) return ""
	return if (startsWith("http")) this else APKMIRROR_BASE_URL + this
}
