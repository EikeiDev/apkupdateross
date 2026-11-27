package com.apkupdateross.prefs

import com.apkupdateross.data.fourpda.FourPdaTopicBinding
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.repository.FourPdaRepository
import com.apkupdateross.data.ui.CachedUpdateEntry
import com.apkupdateross.data.ui.InstallHistoryEntry
import com.apkupdateross.data.ui.Screen
import com.apkupdateross.data.ui.defaultSourcePriorityOrder
import com.apkupdateross.data.ui.SearchSourceFilter
import com.apkupdateross.data.ui.SwipeIgnoreDirection
import com.kryptoprefs.context.KryptoContext
import kotlinx.coroutines.flow.asStateFlow
import com.kryptoprefs.gson.json
import com.kryptoprefs.preferences.KryptoPrefs


class Prefs(
	prefs: KryptoPrefs
): KryptoContext(prefs) {
	val ignoredApps = json("ignoredApps", emptyList<String>(), true)
	val ignoredVersions = json("ignoredVersions", emptyList<Int>(), true)
	val ignoredUpdateInfos = json("ignoredUpdateInfos", emptyList<com.apkupdateross.data.ui.IgnoredUpdateInfo>(), true)
	val excludeSystem = boolean("excludeSystem", defValue = true, backed = true)
	val excludeDisabled = boolean("excludeDisabled", defValue = true, backed = true)
	val excludeStore = boolean("excludeStore", defValue = false, backed = true)
	val portraitColumns = int("portraitColumns", 3, true)
	val landscapeColumns = int("landscapeColumns", 6, true)
	val playTextAnimations = boolean("playTextAnimations", defValue = true, backed = true)
	val ignoreAlpha = boolean("ignoreAlpha", defValue = true, backed = true)
	val ignoreBeta = boolean("ignoreBeta", defValue = true, backed = true)
	val ignorePreRelease = boolean("ignorePreRelease", defValue = true, backed = true)
	val autoUpdateBackground = boolean("autoUpdateBackground", defValue = false, backed = true)
	val autoInstallWifiOnly = boolean("autoInstallWifiOnly", defValue = false, backed = true)
	val notifyAvailableUpdates = boolean("notifyAvailableUpdates", defValue = true, backed = true)
	val notifyAutoInstallSuccess = boolean("notifyAutoInstallSuccess", defValue = true, backed = true)
	val notifyBackgroundErrors = boolean("notifyBackgroundErrors", defValue = true, backed = true)
	val notifyNoUpdates = boolean("notifyNoUpdates", defValue = false, backed = true)
	val useApkMirror = boolean("useApkMirror", defValue = false, backed = true)
	val useGitHub = boolean("useGitHub", defValue = true, backed = true)
	val useGitLab = boolean("useGitLab", defValue = true, backed = true)
	val useCodeberg = boolean("useCodeberg", defValue = true, backed = true)
	val useFdroid = boolean("useFdroid", defValue = true, backed = true)
	val useAptoide = boolean("useAptoide", defValue = true, backed = true)
	val useApkPure = boolean("useApkPure", defValue = true, backed = true)
	val useApk20 = boolean("useApk20", defValue = false, backed = true)
	val useUptodown = boolean("useUptodown", defValue = false, backed = true)
	val uptodownDeviceIdentifier = string("uptodownDeviceIdentifier", defValue = "", backed = true)
	val usePlay = boolean("usePlay", defValue = true, backed = true)
	val useRuStore = boolean("useRuStore", defValue = true, backed = true)
	val useHuawei = boolean("useHuawei", defValue = false, backed = true)
	val useSamsungGalaxyStore = boolean("useSamsungGalaxyStore", defValue = false, backed = true)
	val use4pda = boolean("use4pda", defValue = false, backed = true)
	val fourPdaServerUrl = string("fourPdaServerUrl", defValue = FourPdaRepository.DEFAULT_SERVER_URL, backed = true)
	val fourPdaBuildPreference = int("fourPdaBuildPreference", defValue = 0, backed = true)
	val fourPdaCookies = string("fourPdaCookies", defValue = "", backed = true)
	val fourPdaUserId = string("fourPdaUserId", defValue = "", backed = true)
	val fourPdaTopicBindings = json("fourPdaTopicBindings", emptyMap<String, FourPdaTopicBinding>(), true)
	val samsungGalaxyStoreRegion = string("samsungGalaxyStoreRegion", defValue = "GLOBAL", backed = true)
	val huaweiRegion = string("huaweiRegion", defValue = "AUTO", backed = true)
	val huaweiCachedRegion = string("huaweiCachedRegion", defValue = "", backed = true)
	val githubToken = string("githubToken", defValue = "", backed = true)
	val gitlabToken = string("gitlabToken", defValue = "", backed = true)
	val codebergToken = string("codebergToken", defValue = "", backed = true)
	val fdroidRepos = json("fdroidRepos", listOf(
		com.apkupdateross.data.fdroid.FdroidRepo(name = "F-Droid", url = "https://f-droid.org/repo/", isDefault = true),
		com.apkupdateross.data.fdroid.FdroidRepo(name = "IzzyOnDroid", url = "https://apt.izzysoft.de/fdroid/repo/", isDefault = true)
	), true)

	val ruStore404Packages = json("ruStore404Packages", emptyList<RuStore404Entry>(), true)
	val storeNotFoundCache = json("storeNotFoundCache", emptyMap<String, Map<String, Long>>(), true)
	val searchFilters = json("searchFilters", SearchSourceFilter.defaultSelection.map { it.name }, true)
	val searchFiltersHuaweiMigrated = boolean("searchFiltersHuaweiMigrated", defValue = false, backed = true)
	val searchFiltersUptodownMigrated = boolean("searchFiltersUptodownMigrated", defValue = false, backed = true)
	val searchFiltersApk20Migrated = boolean("searchFiltersApk20Migrated", defValue = false, backed = true)
	val searchFiltersSamsungMigrated = boolean("searchFiltersSamsungMigrated", defValue = false, backed = true)
	val searchFiltersCodebergMigrated = boolean("searchFiltersCodebergMigrated", defValue = false, backed = true)
	val searchFiltersFourPdaMigrated = boolean("searchFiltersFourPdaMigrated", defValue = false, backed = true)
	val enableAlarm = boolean("enableAlarm", defValue = false, backed = true)
	val alarmHour = int("alarmHour", defValue = 12, backed = true)
	val alarmFrequency = int("alarmFrequency", 0, backed = true)
	val installMode = int("installMode", defValue = 0, backed = true)
	val theme = int("theme", defValue = 0, backed = true)
	val useDynamicColor = boolean("useDynamicColor", defValue = false, backed = true)
	val appLanguage = string("appLanguage", defValue = "", backed = true)
	val autoCheckOnStart = boolean("autoCheckOnStart", defValue = true, backed = true)
	val swipeIgnoreEnabled = boolean("swipeIgnoreEnabled", defValue = true, backed = true)
	val swipeIgnoreDirection = int("swipeIgnoreDirection", defValue = SwipeIgnoreDirection.Default.ordinal, backed = true)
	val customThemeAccent = string("customThemeAccent", defValue = "#74D7B2", backed = true)
	val customThemeBackground = string("customThemeBackground", defValue = "#101312", backed = true)
	val customThemeSurface = string("customThemeSurface", defValue = "#151917", backed = true)
	val customThemeNavigation = string("customThemeNavigation", defValue = "#15231D", backed = true)
	val downloadFolderUri = string("downloadFolderUri", defValue = "", backed = true)
	val lastTab = string("lastTab", defValue = Screen.Updates.route, backed = true)
	val lastUpdateCheckDurationMs = long("lastUpdateCheckDurationMs", 0L, true)
	val lastUpdateCheckTimestamp = long("lastUpdateCheckTimestamp", 0L, true)
	val lastUpdateSourcesCount = int("lastUpdateSourcesCount", 0, true)
	val lastSelfUpdateVersionCode = long("lastSelfUpdateVersionCode", 0L, true)
	val lastDebugReleaseWarningVersionCode = long("lastDebugReleaseWarningVersionCode", 0L, true)
	val customGitRepos = json("customGitRepos", emptyList<CustomGitRepo>(), true)
	val installHistory = json("installHistory", emptyList<InstallHistoryEntry>(), true)
	val cachedUpdates = json("cachedUpdates", emptyList<CachedUpdateEntry>(), true)
	val sourcePriorityOrder = json("sourcePriorityOrder", defaultSourcePriorityOrder, true)
	val pinnedAppSources = json("pinnedAppSources", emptyMap<String, String>(), true)
	val useCompactView = boolean("useCompactView", defValue = false, backed = true)
	val globalTimeoutSec = int("globalTimeoutSec", defValue = 30, backed = true)
	val playTimeoutSec = int("playTimeoutSec", defValue = 20, backed = true)
	val backgroundCheckWifiOnly = boolean("backgroundCheckWifiOnly", defValue = false, backed = true)
	val proxyEnabled = boolean("proxyEnabled", defValue = false, backed = true)
	val proxyType = int("proxyType", defValue = 0, backed = true) // 0 = HTTP, 1 = SOCKS5
	val proxyHost = string("proxyHost", defValue = "", backed = true)
	val proxyPort = int("proxyPort", defValue = 8080, backed = true)
	val proxyUsername = string("proxyUsername", defValue = "", backed = true)
	val proxyPassword = string("proxyPassword", defValue = "", backed = true)

	private val _ignoredVersionsFlow = kotlinx.coroutines.flow.MutableStateFlow(ignoredVersions.get())
	val ignoredVersionsFlow = _ignoredVersionsFlow.asStateFlow()

	private val _ignoredUpdateInfosFlow = kotlinx.coroutines.flow.MutableStateFlow(ignoredUpdateInfos.get())
	val ignoredUpdateInfosFlow = _ignoredUpdateInfosFlow.asStateFlow()

	private val _useCompactViewFlow = kotlinx.coroutines.flow.MutableStateFlow(useCompactView.get())
	val useCompactViewFlow = _useCompactViewFlow.asStateFlow()

	private val _portraitColumnsFlow = kotlinx.coroutines.flow.MutableStateFlow(portraitColumns.get().coerceIn(1, 6))
	val portraitColumnsFlow = _portraitColumnsFlow.asStateFlow()

	private val _landscapeColumnsFlow = kotlinx.coroutines.flow.MutableStateFlow(landscapeColumns.get().coerceIn(2, 8))
	val landscapeColumnsFlow = _landscapeColumnsFlow.asStateFlow()

	private val _swipeIgnoreEnabledFlow = kotlinx.coroutines.flow.MutableStateFlow(swipeIgnoreEnabled.get())
	val swipeIgnoreEnabledFlow = _swipeIgnoreEnabledFlow.asStateFlow()

	private val _swipeIgnoreDirectionFlow = kotlinx.coroutines.flow.MutableStateFlow(SwipeIgnoreDirection.fromIndex(swipeIgnoreDirection.get()))
	val swipeIgnoreDirectionFlow = _swipeIgnoreDirectionFlow.asStateFlow()

	private val _downloadFolderUriFlow = kotlinx.coroutines.flow.MutableStateFlow(downloadFolderUri.get())
	val downloadFolderUriFlow = _downloadFolderUriFlow.asStateFlow()

	private val _installHistoryFlow = kotlinx.coroutines.flow.MutableStateFlow(installHistory.get())
	val installHistoryFlow = _installHistoryFlow.asStateFlow()

	private val _sourcePriorityOrderFlow = kotlinx.coroutines.flow.MutableStateFlow(
		normalizeSourcePriorityOrder(sourcePriorityOrder.get())
	)
	val sourcePriorityOrderFlow = _sourcePriorityOrderFlow.asStateFlow()

	private val _pinnedAppSourcesFlow = kotlinx.coroutines.flow.MutableStateFlow(pinnedAppSources.get())
	val pinnedAppSourcesFlow = _pinnedAppSourcesFlow.asStateFlow()

	private val _fourPdaTopicBindingsFlow = kotlinx.coroutines.flow.MutableStateFlow(fourPdaTopicBindings.get())
	val fourPdaTopicBindingsFlow = _fourPdaTopicBindingsFlow.asStateFlow()

	private val historyLock = Any()

	fun setUseCompactView(b: Boolean) {
		useCompactView.put(b)
		_useCompactViewFlow.value = b
	}

	fun setPortraitColumns(i: Int) {
		val value = i.coerceIn(1, 6)
		portraitColumns.put(value)
		_portraitColumnsFlow.value = value
	}

	fun setLandscapeColumns(i: Int) {
		val value = i.coerceIn(2, 8)
		landscapeColumns.put(value)
		_landscapeColumnsFlow.value = value
	}

	fun setSwipeIgnoreEnabled(enabled: Boolean) {
		swipeIgnoreEnabled.put(enabled)
		_swipeIgnoreEnabledFlow.value = enabled
	}

	fun setSwipeIgnoreDirection(direction: SwipeIgnoreDirection) {
		swipeIgnoreDirection.put(direction.ordinal)
		_swipeIgnoreDirectionFlow.value = direction
	}

	fun setDownloadFolderUri(uri: String) {
		downloadFolderUri.put(uri)
		_downloadFolderUriFlow.value = uri
	}

	fun setIgnoredVersions(list: List<Int>, infos: List<com.apkupdateross.data.ui.IgnoredUpdateInfo>) {
		ignoredVersions.put(list)
		_ignoredVersionsFlow.value = list
		ignoredUpdateInfos.put(infos)
		_ignoredUpdateInfosFlow.value = infos
	}

	fun appendInstallHistory(entry: InstallHistoryEntry, maxEntries: Int = 100) {
		synchronized(historyLock) {
			val updated = (listOf(entry) + installHistory.get()).take(maxEntries)
			installHistory.put(updated)
			_installHistoryFlow.value = updated
		}
	}

	fun clearInstallHistory() {
		synchronized(historyLock) {
			installHistory.put(emptyList())
			_installHistoryFlow.value = emptyList()
		}
	}

	fun setSourcePriorityOrder(order: List<String>) {
		val normalized = normalizeSourcePriorityOrder(order)
		sourcePriorityOrder.put(normalized)
		_sourcePriorityOrderFlow.value = normalized
	}

	fun resetSourcePriorityOrder() {
		setSourcePriorityOrder(defaultSourcePriorityOrder)
	}

	fun setPinnedAppSource(packageName: String, sourceName: String?) {
		val current = pinnedAppSources.get().toMutableMap()
		if (sourceName.isNullOrBlank()) {
			current.remove(packageName)
		} else {
			current[packageName] = sourceName
		}
		pinnedAppSources.put(current)
		_pinnedAppSourcesFlow.value = current
	}

	fun setFourPdaTopicBindings(bindings: Map<String, FourPdaTopicBinding>) {
		fourPdaTopicBindings.put(bindings)
		_fourPdaTopicBindingsFlow.value = bindings
	}

	private fun normalizeSourcePriorityOrder(order: List<String>): List<String> {
		val known = defaultSourcePriorityOrder.toSet()
		val cleaned = order.filter { it in known }.distinct()
		return cleaned + defaultSourcePriorityOrder.filterNot { it in cleaned }
	}

}
