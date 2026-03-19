package com.apkupdateross.prefs

import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.ui.Screen
import com.apkupdateross.data.ui.SearchSourceFilter
import com.aurora.gplayapi.data.models.AuthData
import com.kryptoprefs.context.KryptoContext
import kotlinx.coroutines.flow.asStateFlow
import com.kryptoprefs.gson.json
import com.kryptoprefs.preferences.KryptoPrefs


class Prefs(
	prefs: KryptoPrefs,
	isAndroidTv: Boolean
): KryptoContext(prefs) {
	val ignoredApps = json("ignoredApps", emptyList<String>(), true)
	val ignoredVersions = json("ignoredVersions", emptyList<Int>(), true)
	val excludeSystem = boolean("excludeSystem", defValue = true, backed = true)
	val excludeDisabled = boolean("excludeDisabled", defValue = true, backed = true)
	val excludeStore = boolean("excludeStore", defValue = false, backed = true)
	val portraitColumns = int("portraitColumns", 3, true)
	val landscapeColumns = int("landscapeColumns", 6, true)
	val playTextAnimations = boolean("playTextAnimations", defValue = true, backed = true)
	val ignoreAlpha = boolean("ignoreAlpha", defValue = true, backed = true)
	val ignoreBeta = boolean("ignoreBeta", defValue = true, backed = true)
	val ignorePreRelease = boolean("ignorePreRelease", defValue = true, backed = true)
	val useApkMirror = boolean("useApkMirror", defValue = false, backed = true)
	val useGitHub = boolean("useGitHub", defValue = true, backed = true)
	val useGitLab = boolean("useGitLab", defValue = true, backed = true)
	val useFdroid = boolean("useFdroid", defValue = true, backed = true)
	val useIzzy = boolean("useIzzy", defValue = true, backed = true)
	val useAptoide = boolean("useAptoide", defValue = true, backed = true)
	val useApkPure = boolean("useApkPure", defValue = true, backed = true)
	val usePlay = boolean("usePlay", defValue = true, backed = true)
	val useRuStore = boolean("useRuStore", defValue = true, backed = true)
	val githubToken = string("githubToken", defValue = "", backed = true)
	val gitlabToken = string("gitlabToken", defValue = "", backed = true)
	val fdroidRepos = json("fdroidRepos", listOf(
		com.apkupdateross.data.fdroid.FdroidRepo(name = "F-Droid", url = "https://f-droid.org/repo/", isDefault = true),
		com.apkupdateross.data.fdroid.FdroidRepo(name = "IzzyOnDroid", url = "https://apt.izzysoft.de/fdroid/repo/", isDefault = true)
	), true)
	val ruStoreFilterThirdParty = boolean("ruStoreFilterThirdParty", defValue = false, backed = true)
	val ruStore404Packages = json("ruStore404Packages", emptyList<RuStore404Entry>(), true)
	val searchFilters = json("searchFilters", SearchSourceFilter.defaultSelection.map { it.name }, true)
	val enableAlarm = boolean("enableAlarm", defValue = false, backed = true)
	val alarmHour = int("alarmHour", defValue = 12, backed = true)
	val alarmFrequency = int("alarmFrequency", 0, backed = true)
	val androidTvUi = boolean("androidTvUi", defValue = true, backed = true)
	val installMode = int("installMode", defValue = 0, backed = true)
	val theme = int("theme", defValue = 0, backed = true)
	val lastTab = string("lastTab", defValue = Screen.Updates.route, backed = true)
	val playAuthData = json("playAuthData", AuthData("", ""), true)
	val lastPlayCheck = long("lastPlayCheck", 0L, true)
	val lastUpdateCheckDurationMs = long("lastUpdateCheckDurationMs", 0L, true)
	val lastUpdateCheckTimestamp = long("lastUpdateCheckTimestamp", 0L, true)
	val lastUpdateSourcesCount = int("lastUpdateSourcesCount", 0, true)
	val lastSelfUpdateVersionCode = long("lastSelfUpdateVersionCode", 0L, true)
	val customGitRepos = json("customGitRepos", emptyList<CustomGitRepo>(), true)
	val useCompactView = boolean("useCompactView", defValue = false, backed = true)

	private val _ignoredVersionsFlow = kotlinx.coroutines.flow.MutableStateFlow(ignoredVersions.get())
	val ignoredVersionsFlow = _ignoredVersionsFlow.asStateFlow()

	private val _useCompactViewFlow = kotlinx.coroutines.flow.MutableStateFlow(useCompactView.get())
	val useCompactViewFlow = _useCompactViewFlow.asStateFlow()

	private val _portraitColumnsFlow = kotlinx.coroutines.flow.MutableStateFlow(portraitColumns.get())
	val portraitColumnsFlow = _portraitColumnsFlow.asStateFlow()

	private val _landscapeColumnsFlow = kotlinx.coroutines.flow.MutableStateFlow(landscapeColumns.get())
	val landscapeColumnsFlow = _landscapeColumnsFlow.asStateFlow()

	fun setUseCompactView(b: Boolean) {
		useCompactView.put(b)
		_useCompactViewFlow.value = b
	}

	fun setPortraitColumns(i: Int) {
		portraitColumns.put(i)
		_portraitColumnsFlow.value = i
	}

	fun setLandscapeColumns(i: Int) {
		landscapeColumns.put(i)
		_landscapeColumnsFlow.value = i
	}

	fun setIgnoredVersions(list: List<Int>) {
		ignoredVersions.put(list)
		_ignoredVersionsFlow.value = list
	}
}

