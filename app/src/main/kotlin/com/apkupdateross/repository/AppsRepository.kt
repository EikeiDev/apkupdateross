package com.apkupdateross.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.transform.toAppInstalled
import com.apkupdateross.util.orFalse
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class AppsRepository(
	private val context: Context,
	private val prefs: Prefs
) {

	suspend fun getApps() = flow {
		val apps = context.packageManager
			.getInstalledPackages(PackageManager.MATCH_ALL + getSignatureFlag())
			.asSequence()
			.filter { !excludeSystem() || it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0 }
			.filter { !excludeSystem() || it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 }
			.filter { !excludeDisabled() || it.applicationInfo?.enabled != false }
			.map { it to getInstallerPackageName(it.packageName) }
			.filter { (_, installer) -> !excludeStore() || !isAppStore(installer) }
			.filter { (pkg, _) -> pkg.packageName != com.apkupdateross.BuildConfig.APPLICATION_ID }
			.map { (pkg, installer) -> pkg.toAppInstalled(context, ignoredApps(), installerLabel(installer)) }
			.sortedBy { it.name }
			.sortedBy { it.ignored }
			.toList()
		emit(Result.success(apps))
	}.catch {
		Log.e("AppsRepository", "Error getting apps.", it)
		emit(Result.failure(it))
	}

	private fun excludeSystem() = prefs.excludeSystem.get()
	private fun excludeDisabled() = prefs.excludeDisabled.get()
	private fun excludeStore() = prefs.excludeStore.get()
	private fun ignoredApps() = prefs.ignoredApps.get()

	@Suppress("DEPRECATION")
	private fun getSignatureFlag(): Int {
		return if (Build.VERSION.SDK_INT >= 28) {
			PackageManager.GET_SIGNING_CERTIFICATES
		} else {
			PackageManager.GET_SIGNATURES
		}
	}

	@Suppress("DEPRECATION")
	private fun getInstallerPackageName(packageName: String): String {
		return if (Build.VERSION.SDK_INT < 30) {
			context.packageManager.getInstallerPackageName(packageName).orEmpty()
		} else {
			context.packageManager.getInstallSourceInfo(packageName).installingPackageName.orEmpty()
		}
	}

	private fun isAppStore(name: String?) = name?.let { n ->
		n.contains("com.android.vending") ||
		n.contains("com.amazon.venezia") ||
		n.contains("com.huawei.appmarket") ||
		n.contains("com.xiaomi.mipicks") ||
		n.contains("com.sec.android.app.samsungapps") ||
		n.contains("ru.vk.store")
	}.orFalse()

	private fun installerLabel(installerPackage: String): String = when {
		installerPackage.isBlank() -> ""
		installerPackage.contains("com.android.vending") -> "Google Play"
		installerPackage.contains("ru.vk.store") -> "RuStore"
		installerPackage.contains("org.fdroid.fdroid") || installerPackage.contains("com.looker.droidify") || installerPackage.contains("com.machiav3lli.fdroid") -> "F-Droid"
		installerPackage.contains("com.huawei.appmarket") -> "AppGallery"
		installerPackage.contains("com.sec.android.app.samsungapps") -> "Galaxy Store"
		installerPackage.contains("com.amazon.venezia") -> "Amazon"
		installerPackage.contains("com.xiaomi.mipicks") -> "GetApps"
		installerPackage.contains("com.apkupdateross") -> "APKUpdater OSS"
		else -> ""
	}

}
