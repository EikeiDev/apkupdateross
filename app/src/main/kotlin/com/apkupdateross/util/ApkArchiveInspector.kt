package com.apkupdateross.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

data class ApkArchiveMetadata(
    val packageName: String,
    val appName: String,
    val versionName: String?
)

class ApkArchiveInspector(private val context: Context) {

    fun inspect(file: File): ApkArchiveMetadata? {
        if (!file.isFile || file.length() == 0L) return null

        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
        } ?: return null

        val packageName = packageInfo.packageName?.takeIf { it.isNotBlank() } ?: return null
        val applicationInfo = packageInfo.applicationInfo
        applicationInfo?.sourceDir = file.absolutePath
        applicationInfo?.publicSourceDir = file.absolutePath
        val appName = runCatching {
            applicationInfo?.loadLabel(packageManager)?.toString()?.trim()
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: packageName

        return ApkArchiveMetadata(
            packageName = packageName,
            appName = appName,
            versionName = packageInfo.versionName
        )
    }
}
