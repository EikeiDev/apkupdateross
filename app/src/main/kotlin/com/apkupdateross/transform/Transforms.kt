package com.apkupdateross.transform

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import com.apkupdateross.data.ui.AppInstalled
import com.apkupdateross.util.getSignatureSha1
import com.apkupdateross.util.getSignatureSha256
import com.apkupdateross.util.name


@Suppress("DEPRECATION")
fun PackageInfo.toAppInstalled(
	context: Context,
	ignored: List<String>,
	installerSource: String = ""
) = AppInstalled(
	name = name(context),
	packageName = packageName,
	version = versionName.orEmpty(),
	versionCode = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong(),
	iconUri = iconUri(packageName, applicationInfo?.icon ?: 0),
	ignored = ignored.contains(packageName),
	signature = getSignatureSha1(),
	signatureSha256 = getSignatureSha256(),
	installerSource = installerSource,
	lastUpdateTime = lastUpdateTime,
	sourceDir = applicationInfo?.sourceDir.orEmpty(),
	splitSourceDirs = applicationInfo?.splitSourceDirs?.toList().orEmpty()
)

fun iconUri(packageName: String, id: Int): Uri = Uri.parse("android.resource://$packageName/$id")
