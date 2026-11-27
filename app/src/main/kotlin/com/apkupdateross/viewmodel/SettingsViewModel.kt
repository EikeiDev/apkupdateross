package com.apkupdateross.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import com.apkupdateross.R
import com.apkupdateross.data.fourpda.FourPdaAuthChallenge
import com.apkupdateross.data.fourpda.FourPdaCrawlerTopic
import com.apkupdateross.data.fourpda.FourPdaLoginResult
import com.apkupdateross.data.fourpda.FourPdaTopicBinding
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.SwipeIgnoreDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.apkupdateross.data.ui.SettingsUiState
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.AppsRepository
import com.apkupdateross.repository.FourPdaAuthClient
import com.apkupdateross.repository.FourPdaRepository
import com.apkupdateross.repository.GitRepoDetectionResult
import com.apkupdateross.repository.GitRepoDetector
import com.apkupdateross.repository.PlayRepository
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_ACCENT
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_BACKGROUND
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_NAVIGATION
import com.apkupdateross.ui.theme.DEFAULT_CUSTOM_SURFACE
import com.apkupdateross.ui.theme.THEME_MODE_CUSTOM
import com.apkupdateross.ui.theme.normalizeHexColor
import com.apkupdateross.util.Clipboard
import com.apkupdateross.util.Downloader
import com.apkupdateross.util.FourPdaSession
import com.apkupdateross.util.LocaleHelper
import com.apkupdateross.util.LocalPackageInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.StoreNotFoundCache
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.Themer
import com.apkupdateross.util.UpdatesNotification
import com.apkupdateross.util.currentProxyConfig
import com.apkupdateross.util.syncGlobalSocksAuthenticator
import com.apkupdateross.worker.UpdatesWorker
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import java.util.Locale

data class InstallModeAvailability(
	val rootAvailable: Boolean = false,
	val shizukuRunning: Boolean = false,
	val shizukuPermissionGranted: Boolean = false
) {
	val enabledModes: List<Boolean>
		get() = listOf(true, rootAvailable, shizukuRunning)
}


class SettingsViewModel(
    private val prefs: Prefs,
    private val notification: UpdatesNotification,
    private val workManager: WorkManager,
	private val clipboard: Clipboard,
	private val appsRepository: AppsRepository,
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
	private val themer: Themer,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	private val gitRepoDetector: GitRepoDetector,
	private val downloader: Downloader,
	private val localPackageInstaller: LocalPackageInstaller,
	private val okHttpClient: OkHttpClient,
	private val storeNotFoundCache: StoreNotFoundCache,
	private val fourPdaSession: FourPdaSession,
	private val fourPdaAuthClient: FourPdaAuthClient,
	private val fourPdaRepository: FourPdaRepository,
	private val playRepository: PlayRepository
) : ViewModel() {

	private companion object {
		private const val SHIZUKU_PERMISSION_REQUEST_CODE = 100
		private const val HUAWEI_REGION_AUTO = "AUTO"
		private val HUAWEI_REGION_CODE = Regex("[A-Z]{2}")
		private const val SAMSUNG_REGION_GLOBAL = "GLOBAL"
		private const val SAMSUNG_REGION_CHINA = "CN"
		val NON_PORTABLE_PREF_KEYS = setOf(
			"githubToken",
			"gitlabToken",
			"codebergToken",
			"playAuthData",
			"downloadFolderUri",
			"huaweiCachedRegion",
			"ruStore404Packages",
			"storeNotFoundCache",
			"lastPlayCheck",
			"lastUpdateCheckDurationMs",
			"lastUpdateCheckTimestamp",
			"lastUpdateSourcesCount",
			"lastSelfUpdateVersionCode",
			"lastDebugReleaseWarningVersionCode",
			"installHistory",
			"cachedUpdates",
			"proxyUsername",
			"proxyPassword",
			"fourPdaCookies",
			"fourPdaUserId"
		)
	}

	val state = MutableStateFlow<SettingsUiState>(SettingsUiState.Settings)
	private val _ruStore404Count = MutableStateFlow(prefs.ruStore404Packages.get().size)
	val ruStore404Count = _ruStore404Count.asStateFlow()
	private val _storeNotFoundCacheCount = MutableStateFlow(computeStoreNotFoundCacheCount())
	val storeNotFoundCacheCount = _storeNotFoundCacheCount.asStateFlow()
    private val _updateMetrics = MutableStateFlow(UpdateMetrics())
	val updateMetrics = _updateMetrics.asStateFlow()
    private val _customGitRepos = MutableStateFlow(prefs.customGitRepos.get())
    val customGitRepos = _customGitRepos.asStateFlow()
    private val _fdroidRepos = MutableStateFlow(prefs.fdroidRepos.get())
    val fdroidRepos = _fdroidRepos.asStateFlow()
    val ignoredUpdateInfos = prefs.ignoredUpdateInfosFlow
	val installHistory = prefs.installHistoryFlow
	val sourcePriorityOrder = prefs.sourcePriorityOrderFlow
	val pinnedAppSources = prefs.pinnedAppSourcesFlow
	val useCompactView = prefs.useCompactViewFlow
	val swipeIgnoreEnabled = prefs.swipeIgnoreEnabledFlow
	val downloadFolderUri = prefs.downloadFolderUriFlow
	val fourPdaUserId = fourPdaSession.loggedInUserId
	val fourPdaTopicBindings = prefs.fourPdaTopicBindingsFlow
	private val _isTestingFourPdaServer = MutableStateFlow(false)
	val isTestingFourPdaServer = _isTestingFourPdaServer.asStateFlow()
	private val _downloadCacheSizeBytes = MutableStateFlow(downloader.cacheSizeBytes())
	val downloadCacheSizeBytes = _downloadCacheSizeBytes.asStateFlow()
	private val _isTestingProxy = MutableStateFlow(false)
	val isTestingProxy = _isTestingProxy.asStateFlow()
	
	private val _installModeFlow = MutableStateFlow(prefs.installMode.get())
	val installModeFlow = _installModeFlow.asStateFlow()
	
    private var metricsJob: Job? = null

	private val _installModeAvailability = MutableStateFlow(InstallModeAvailability())
	val installModeAvailability = _installModeAvailability.asStateFlow()

	init {
		refreshInstallModeAvailability()
		refreshUpdateMetrics()
		ensureDefaultRepos()
	}

    private fun ensureDefaultRepos() {
        val current = prefs.fdroidRepos.get()
        val hasFdroid = current.any { it.url == "https://f-droid.org/repo/" }
        val hasIzzy = current.any { it.url == "https://apt.izzysoft.de/fdroid/repo/" }
        
        if (!hasFdroid || !hasIzzy) {
            val updated = current.toMutableList()
            if (!hasFdroid) {
                updated.add(com.apkupdateross.data.fdroid.FdroidRepo(name = "F-Droid", url = "https://f-droid.org/repo/", isDefault = true))
            }
            if (!hasIzzy) {
                updated.add(com.apkupdateross.data.fdroid.FdroidRepo(name = "IzzyOnDroid", url = "https://apt.izzysoft.de/fdroid/repo/", isDefault = true))
            }
            // Ensure they are marked as default if they already existed but with wrong flag
            val final = updated.map { repo ->
                if (repo.url == "https://f-droid.org/repo/" || repo.url == "https://apt.izzysoft.de/fdroid/repo/") {
                    repo.copy(isDefault = true)
                } else repo
            }
            prefs.fdroidRepos.put(final)
            _fdroidRepos.value = final
        }
    }

	fun refreshInstallModeAvailability() {
		viewModelScope.launch(Dispatchers.IO) {
			val rootAvailable = runCatching { Shell.getShell(); Shell.isAppGrantedRoot() == true }.getOrDefault(false)
			val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
			val shizukuPermissionGranted = shizukuRunning && runCatching {
				Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
			}.getOrDefault(false)
			_installModeAvailability.value = InstallModeAvailability(
				rootAvailable = rootAvailable,
				shizukuRunning = shizukuRunning,
				shizukuPermissionGranted = shizukuPermissionGranted
			)
		}
	}

	fun setPortraitColumns(n: Int) = prefs.setPortraitColumns(n)
	fun getPortraitColumns() = prefs.portraitColumns.get().coerceIn(1, 6)
	fun setLandscapeColumns(n: Int) = prefs.setLandscapeColumns(n)
	fun getLandscapeColumns() = prefs.landscapeColumns.get().coerceIn(2, 8)
	fun setGlobalTimeoutSec(n: Int) = prefs.globalTimeoutSec.put(n.coerceIn(10, 120))
	fun getGlobalTimeoutSec() = prefs.globalTimeoutSec.get()
	fun setPlayTimeoutSec(n: Int) = prefs.playTimeoutSec.put(n.coerceIn(10, 120))
	fun getPlayTimeoutSec() = prefs.playTimeoutSec.get()
	fun getBackgroundCheckWifiOnly() = prefs.backgroundCheckWifiOnly.get()
	fun setBackgroundCheckWifiOnly(enabled: Boolean) {
		prefs.backgroundCheckWifiOnly.put(enabled)
		rescheduleBackgroundChecks()
	}
	fun getProxyEnabled() = prefs.proxyEnabled.get()
	fun setProxyEnabled(enabled: Boolean) {
		prefs.proxyEnabled.put(enabled)
		syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	}
	fun getProxyType() = prefs.proxyType.get().coerceIn(0, 1)
	fun setProxyType(type: Int) {
		prefs.proxyType.put(type.coerceIn(0, 1))
		syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	}
	fun getProxyHost() = prefs.proxyHost.get()
	fun setProxyHost(host: String) {
		prefs.proxyHost.put(host.trim())
		syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	}
	fun getProxyPort() = prefs.proxyPort.get()
	fun setProxyPort(portText: String) {
		val parsed = portText.trim().toIntOrNull()?.coerceIn(1, 65535) ?: return
		prefs.proxyPort.put(parsed)
		syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	}
	fun getProxyUsername() = prefs.proxyUsername.get()
	fun setProxyUsername(user: String) {
		prefs.proxyUsername.put(user.trim())
		syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	}
	fun getProxyPassword() = prefs.proxyPassword.get()
	fun setProxyPassword(password: String) {
		prefs.proxyPassword.put(password)
		syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	}

	fun testProxyConnection() {
		if (_isTestingProxy.value) return
		viewModelScope.launch(Dispatchers.IO) {
			_isTestingProxy.value = true
			val start = System.currentTimeMillis()
			try {
				val request = Request.Builder()
					.url("https://clients3.google.com/generate_204")
					.head()
					.build()
				okHttpClient.newCall(request).execute().use { response ->
					val elapsed = System.currentTimeMillis() - start
					if (response.isSuccessful || response.code == 204) {
						snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.proxy_test_success, elapsed)))
					} else {
						snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.proxy_test_failed, "HTTP ${response.code}")))
					}
				}
			} catch (e: Exception) {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.proxy_test_failed, e.message ?: e.javaClass.simpleName)))
			} finally {
				_isTestingProxy.value = false
			}
		}
	}

	fun installLocalPackage(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
		localPackageInstaller.installFromUri(uri)
	}
	fun setPlayTextAnimations(b: Boolean) = prefs.playTextAnimations.put(b)
	fun getPlayTextAnimations() = prefs.playTextAnimations.get()
	fun setIgnoreAlpha(b: Boolean) = prefs.ignoreAlpha.put(b)
	fun getIgnoreAlpha() = prefs.ignoreAlpha.get()
	fun setIgnoreBeta(b: Boolean) = prefs.ignoreBeta.put(b)
	fun getIgnoreBeta() = prefs.ignoreBeta.get()
	fun setIgnorePreRelease(b: Boolean) = prefs.ignorePreRelease.put(b)
	fun getIgnorePreRelease() = prefs.ignorePreRelease.get()
	fun setAutoUpdateBackground(b: Boolean) {
		prefs.autoUpdateBackground.put(b)
		rescheduleBackgroundChecks()
	}
	fun getAutoUpdateBackground() = prefs.autoUpdateBackground.get()
	fun setAutoInstallWifiOnly(b: Boolean) = prefs.autoInstallWifiOnly.put(b)
	fun getAutoInstallWifiOnly() = prefs.autoInstallWifiOnly.get()
	fun setNotifyAvailableUpdates(b: Boolean) = prefs.notifyAvailableUpdates.put(b)
	fun getNotifyAvailableUpdates() = prefs.notifyAvailableUpdates.get()
	fun setNotifyAutoInstallSuccess(b: Boolean) = prefs.notifyAutoInstallSuccess.put(b)
	fun getNotifyAutoInstallSuccess() = prefs.notifyAutoInstallSuccess.get()
	fun setNotifyBackgroundErrors(b: Boolean) = prefs.notifyBackgroundErrors.put(b)
	fun getNotifyBackgroundErrors() = prefs.notifyBackgroundErrors.get()
	fun setNotifyNoUpdates(b: Boolean) = prefs.notifyNoUpdates.put(b)
	fun getNotifyNoUpdates() = prefs.notifyNoUpdates.get()
	fun getUseApkMirror() = prefs.useApkMirror.get()
	fun setUseApkMirror(b: Boolean) = prefs.useApkMirror.put(b)
	fun getUseFdroid() = prefs.useFdroid.get()
	fun setUseFdroid(b: Boolean) = prefs.useFdroid.put(b)
	fun getUseGitHub() = prefs.useGitHub.get()
	fun setUseGitHub(b: Boolean) = prefs.useGitHub.put(b)
	fun getUseGitLab() = prefs.useGitLab.get()
	fun setUseGitLab(b: Boolean) = prefs.useGitLab.put(b)
	fun getUseCodeberg() = prefs.useCodeberg.get()
	fun setUseCodeberg(b: Boolean) = prefs.useCodeberg.put(b)
	fun getUseAptoide() = prefs.useAptoide.get()
	fun setUseAptoide(b: Boolean) = prefs.useAptoide.put(b)
	fun getUseApkPure() = prefs.useApkPure.get()
	fun setUseApkPure(b: Boolean) = prefs.useApkPure.put(b)
	fun getUseApk20() = prefs.useApk20.get()
	fun setUseApk20(b: Boolean) = prefs.useApk20.put(b)
	fun getUseUptodown() = prefs.useUptodown.get()
	fun setUseUptodown(b: Boolean) = prefs.useUptodown.put(b)
	fun getUsePlay() = prefs.usePlay.get()
	fun setUsePlay(b: Boolean) = prefs.usePlay.put(b)
	fun resetPlaySession() {
		playRepository.resetSession()
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.play_reset_session_success)))
	}
	fun getUseRuStore() = prefs.useRuStore.get()
	fun setUseRuStore(b: Boolean) = prefs.useRuStore.put(b)
	fun getUseHuawei() = prefs.useHuawei.get()
	fun setUseHuawei(b: Boolean) = prefs.useHuawei.put(b)
	fun getUseSamsungGalaxyStore() = prefs.useSamsungGalaxyStore.get()
	fun setUseSamsungGalaxyStore(b: Boolean) = prefs.useSamsungGalaxyStore.put(b)
	fun getUse4pda() = prefs.use4pda.get()
	fun setUse4pda(b: Boolean) = prefs.use4pda.put(b)
	fun getFourPdaBuildPreference() = prefs.fourPdaBuildPreference.get().coerceIn(0, 2)
	fun setFourPdaBuildPreference(index: Int) = prefs.fourPdaBuildPreference.put(index.coerceIn(0, 2))

	suspend fun loadFourPdaAuthChallenge(): Result<FourPdaAuthChallenge> =
		fourPdaAuthClient.loadChallenge()

	suspend fun loginFourPda(
		challenge: FourPdaAuthChallenge,
		login: String,
		password: String,
		captcha: String
	): FourPdaLoginResult {
		val result = fourPdaAuthClient.submitLogin(challenge, login, password, captcha)
		if (result is FourPdaLoginResult.Success) {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.fourpda_login_success)))
		}
		return result
	}

	fun logoutFourPda() {
		fourPdaSession.clear()
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.fourpda_logout_success)))
	}

	fun testFourPdaServer() {
		if (_isTestingFourPdaServer.value) return
		viewModelScope.launch(Dispatchers.IO) {
			_isTestingFourPdaServer.value = true
			try {
				fourPdaRepository.checkServerHealth()
					.onSuccess { health ->
						snackBar.snackBar(
							viewModelScope,
							TextSnack(
								stringer.get(
									R.string.fourpda_server_ok,
									health.index.activeTopics,
									health.index.indexedPackages
								)
							)
						)
					}
					.onFailure { error ->
						snackBar.snackBar(
							viewModelScope,
							TextSnack(stringer.get(R.string.fourpda_server_failed, error.message ?: error.javaClass.simpleName))
						)
					}
			} finally {
				_isTestingFourPdaServer.value = false
			}
		}
	}

	suspend fun detectFourPdaTopic(input: String): FourPdaCrawlerTopic? =
		fourPdaRepository.detectTopic(input)

	fun saveFourPdaTopicBinding(binding: FourPdaTopicBinding) {
		val clean = binding.normalized()
		if (clean.packageName.isBlank() || clean.topicId <= 0L) {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.fourpda_topic_invalid)))
			return
		}
		val current = prefs.fourPdaTopicBindings.get().toMutableMap()
		current[clean.packageName] = clean.copy(updatedAt = System.currentTimeMillis())
		prefs.setFourPdaTopicBindings(current)
	}

	fun removeFourPdaTopicBinding(packageName: String) {
		val current = prefs.fourPdaTopicBindings.get().toMutableMap()
		if (current.remove(packageName) != null) {
			prefs.setFourPdaTopicBindings(current)
		}
	}
	fun getSamsungGalaxyStoreRegion(): Int = when (prefs.samsungGalaxyStoreRegion.get().trim().uppercase(Locale.ROOT)) {
		SAMSUNG_REGION_CHINA -> 1
		else -> 0
	}
	fun setSamsungGalaxyStoreRegion(index: Int) = prefs.samsungGalaxyStoreRegion.put(
		if (index == 1) SAMSUNG_REGION_CHINA else SAMSUNG_REGION_GLOBAL
	)
	fun getHuaweiRegion(): String = prefs.huaweiRegion.get()
		.trim()
		.uppercase(Locale.ROOT)
		.takeIf { it == HUAWEI_REGION_AUTO || HUAWEI_REGION_CODE.matches(it) }
		?: HUAWEI_REGION_AUTO

	fun setHuaweiRegion(value: String) {
		val region = value
			.trim()
			.uppercase(Locale.ROOT)
			.takeIf { it == HUAWEI_REGION_AUTO || HUAWEI_REGION_CODE.matches(it) }
			?: HUAWEI_REGION_AUTO
		prefs.huaweiRegion.put(region)
		prefs.huaweiCachedRegion.put("")
	}
	fun getEnableAlarm() = prefs.enableAlarm.get()
	fun getGithubToken() = prefs.githubToken.get()
	fun setGithubToken(token: String) = prefs.githubToken.put(token.trim())
	fun getGitlabToken() = prefs.gitlabToken.get()
	fun setGitlabToken(token: String) = prefs.gitlabToken.put(token.trim())
	fun getCodebergToken() = prefs.codebergToken.get()
	fun setCodebergToken(token: String) = prefs.codebergToken.put(token.trim())

	fun getInstallMode() = prefs.installMode.get()
	fun getAlarmHour() = prefs.alarmHour.get()
	fun getAlarmFrequency() = prefs.alarmFrequency.get()
    fun getTheme() = prefs.theme.get().coerceIn(0, THEME_MODE_CUSTOM)
	fun getSwipeIgnoreEnabled() = prefs.swipeIgnoreEnabled.get()
	fun getSwipeIgnoreDirection() = SwipeIgnoreDirection.fromIndex(prefs.swipeIgnoreDirection.get()).ordinal
	fun getUseCompactView() = prefs.useCompactView.get()
	fun getDownloadFolderLabel(uriString: String = prefs.downloadFolderUri.get()): String {
		if (uriString.isBlank()) return stringer.get(R.string.download_folder_default)
		return runCatching {
			val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
			val path = documentId.substringAfter(':', "")
			val volume = documentId.substringBefore(':', "")
			when {
				path.isNotBlank() -> path
				volume.equals("primary", ignoreCase = true) -> stringer.get(R.string.download_folder_storage_root)
				volume.isNotBlank() -> volume
				else -> stringer.get(R.string.download_folder_custom)
			}
		}.getOrDefault(stringer.get(R.string.download_folder_custom))
	}

	fun setUseCompactView(b: Boolean) = prefs.setUseCompactView(b)

    fun setTheme(theme: Int) {
        prefs.theme.put(theme.coerceIn(0, THEME_MODE_CUSTOM))
        themer.refresh()
    }

	fun getUseDynamicColor() = prefs.useDynamicColor.get()
	fun setUseDynamicColor(enabled: Boolean) {
		prefs.useDynamicColor.put(enabled)
		themer.refresh()
	}

	fun getAutoCheckOnStart() = prefs.autoCheckOnStart.get()
	fun setAutoCheckOnStart(enabled: Boolean) = prefs.autoCheckOnStart.put(enabled)

	fun getAppLanguageIndex(): Int = LocaleHelper.indexForTag(prefs.appLanguage.get())
	fun setAppLanguage(context: Context, index: Int) {
		val tag = LocaleHelper.tagForIndex(index)
		prefs.appLanguage.put(tag)
		LocaleHelper.applyLanguage(context, tag)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			(context as? Activity)?.recreate()
		}
	}

	fun setSwipeIgnoreEnabled(enabled: Boolean) {
		prefs.setSwipeIgnoreEnabled(enabled)
	}

	fun setSwipeIgnoreDirection(index: Int) {
		prefs.setSwipeIgnoreDirection(SwipeIgnoreDirection.fromIndex(index))
	}

	fun setDownloadFolderUri(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
		runCatching {
			val previous = prefs.downloadFolderUri.get()
			val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			runCatching {
				context.contentResolver.takePersistableUriPermission(uri, flags)
			}.recoverCatching {
				context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
			}.getOrThrow()
			prefs.setDownloadFolderUri(uri.toString())
			if (previous.isNotBlank() && previous != uri.toString()) {
				releaseDownloadFolderPermission(context, previous)
			}
		}.onSuccess {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_folder_selected)))
		}.onFailure {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_folder_select_failed)))
		}
	}

	fun resetDownloadFolder(context: Context) {
		val current = prefs.downloadFolderUri.get()
		if (current.isNotBlank()) releaseDownloadFolderPermission(context, current)
		prefs.setDownloadFolderUri("")
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_folder_reset_done)))
	}

	private fun releaseDownloadFolderPermission(context: Context, uriString: String) {
		val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
		runCatching {
			context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
		}.recoverCatching {
			context.contentResolver.releasePersistableUriPermission(
				Uri.parse(uriString),
				Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			)
		}
	}

	fun getCustomThemeAccent() = prefs.customThemeAccent.get()
	fun getCustomThemeBackground() = prefs.customThemeBackground.get()
	fun getCustomThemeSurface() = prefs.customThemeSurface.get()
	fun getCustomThemeNavigation() = prefs.customThemeNavigation.get()

	fun setCustomThemeAccent(value: String) = setCustomThemeColor(value) { prefs.customThemeAccent.put(it) }
	fun setCustomThemeBackground(value: String) = setCustomThemeColor(value) { prefs.customThemeBackground.put(it) }
	fun setCustomThemeSurface(value: String) = setCustomThemeColor(value) { prefs.customThemeSurface.put(it) }
	fun setCustomThemeNavigation(value: String) = setCustomThemeColor(value) { prefs.customThemeNavigation.put(it) }

	fun resetCustomTheme() {
		prefs.customThemeAccent.put(DEFAULT_CUSTOM_ACCENT)
		prefs.customThemeBackground.put(DEFAULT_CUSTOM_BACKGROUND)
		prefs.customThemeSurface.put(DEFAULT_CUSTOM_SURFACE)
		prefs.customThemeNavigation.put(DEFAULT_CUSTOM_NAVIGATION)
		themer.refresh()
	}

	private fun setCustomThemeColor(value: String, put: (String) -> Unit) {
		val normalized = normalizeHexColor(value) ?: return
		put(normalized)
		themer.refresh()
	}

	fun removeIgnoredUpdateInfo(packageName: String) {
		val infos = prefs.ignoredUpdateInfos.get().toMutableList()
		val ids = prefs.ignoredVersions.get().toMutableList()
		infos.removeAll { it.packageName == packageName }
		ids.removeAll { id -> prefs.ignoredUpdateInfos.get().filter { it.packageName == packageName }.map { it.id }.contains(id) }
		prefs.setIgnoredVersions(ids.distinct(), infos.distinctBy { it.id })
	}

	fun clearAllIgnoredUpdates() {
		prefs.setIgnoredVersions(emptyList(), emptyList())
	}

	fun clearInstallHistory() {
		prefs.clearInstallHistory()
	}

	fun moveSourcePriority(index: Int, delta: Int) {
		val current = prefs.sourcePriorityOrderFlow.value.toMutableList()
		val target = index + delta
		if (index !in current.indices || target !in current.indices) return
		val item = current.removeAt(index)
		current.add(target, item)
		prefs.setSourcePriorityOrder(current)
	}

	fun resetSourcePriorityOrder() {
		prefs.resetSourcePriorityOrder()
	}

	fun clearPinnedAppSource(packageName: String) {
		prefs.setPinnedAppSource(packageName, null)
	}

	fun refreshDownloadCacheSize() {
		_downloadCacheSizeBytes.value = downloader.cacheSizeBytes()
	}

	fun clearDownloadCache() = viewModelScope.launch(Dispatchers.IO) {
		downloader.cleanup()
		_downloadCacheSizeBytes.value = downloader.cacheSizeBytes()
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.clear_download_cache_success)))
	}

    fun addOrUpdateCustomRepo(repo: CustomGitRepo) {
        val trimmed = repo.trimmed()
        if (trimmed.user.isEmpty() || trimmed.repo.isEmpty() || trimmed.packageName.isEmpty()) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.settings_custom_repo_error_required)))
            return
        }
        val current = prefs.customGitRepos.get().toMutableList()
        val index = current.indexOfFirst { it.id == trimmed.id }
        if (index >= 0) {
            current[index] = trimmed
        } else {
            current.add(trimmed)
        }
        when (trimmed.platform) {
            GitProvider.CODEBERG -> prefs.useCodeberg.put(true)
            GitProvider.GITHUB -> prefs.useGitHub.put(true)
            GitProvider.GITLAB -> prefs.useGitLab.put(true)
        }
        prefs.customGitRepos.put(current)
        _customGitRepos.value = current
    }

    fun createEmptyCustomRepo(): CustomGitRepo = CustomGitRepo()

	suspend fun detectCustomGitRepo(url: String): GitRepoDetectionResult = gitRepoDetector.detect(url)

    fun removeCustomRepo(id: String) {
        val current = prefs.customGitRepos.get().toMutableList()
        val updated = current.filterNot { it.id == id }
        if (updated.size != current.size) {
            prefs.customGitRepos.put(updated)
            _customGitRepos.value = updated
        }
    }

    fun addOrUpdateFdroidRepo(repo: com.apkupdateross.data.fdroid.FdroidRepo) {
        val normalizedUrl = normalizeFdroidRepoUrl(repo.url)
        if (normalizedUrl == null) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.settings_fdroid_repo_error_invalid_url)))
            return
        }
        val normalizedRepo = repo.copy(name = repo.name.trim(), url = normalizedUrl)
        val current = prefs.fdroidRepos.get().toMutableList()
        val index = current.indexOfFirst { it.id == normalizedRepo.id }
        if (index >= 0) {
            current[index] = normalizedRepo
        } else {
            current.add(normalizedRepo)
        }
        prefs.fdroidRepos.put(current)
        _fdroidRepos.value = current
    }

    fun removeFdroidRepo(id: String) {
        val current = prefs.fdroidRepos.get()
        val repo = current.find { it.id == id }
        if (repo?.isDefault == true) {
            snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.settings_fdroid_repo_default_remove_error)))
            return
        }
        val updated = current.filterNot { it.id == id }
        prefs.fdroidRepos.put(updated)
        _fdroidRepos.value = updated
    }

    fun toggleFdroidRepo(id: String, enabled: Boolean) {
        val current = prefs.fdroidRepos.get().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(isEnabled = enabled)
            prefs.fdroidRepos.put(current)
            _fdroidRepos.value = current
        }
    }

	fun setInstallMode(mode: Int) {
		when (mode) {
			1 -> viewModelScope.launch(Dispatchers.IO) {
				runCatching { Shell.getShell() }
				if (Shell.isAppGrantedRoot() == true) {
					prefs.installMode.put(1)
					_installModeFlow.value = 1
				} else {
					prefs.installMode.put(0)
					_installModeFlow.value = 0
				}
				refreshInstallModeAvailability()
			}
			2 -> runCatching {
				if (!Shizuku.pingBinder()) {
					snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.shizuku_not_running)))
					return
				}
				if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
					prefs.installMode.put(2)
					_installModeFlow.value = 2
					refreshInstallModeAvailability()
				} else {
					Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
						override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
							if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
								val mode = if (grantResult == PackageManager.PERMISSION_GRANTED) 2 else 0
								prefs.installMode.put(mode)
								_installModeFlow.value = mode
								refreshInstallModeAvailability()
								Shizuku.removeRequestPermissionResultListener(this)
							}
						}
					})
					Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
				}
			}.getOrElse {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.shizuku_not_running)))
			}
			else -> {
				prefs.installMode.put(0)
				_installModeFlow.value = 0
			}
		}
	}

	fun setAlarmFrequency(frequency: Int) {
		prefs.alarmFrequency.put(frequency)
		rescheduleBackgroundChecks()
	}

	fun setEnableAlarm(b: Boolean) {
		prefs.enableAlarm.put(b)
		rescheduleBackgroundChecks()
	}

	fun areNotificationsEnabled(): Boolean = notification.areNotificationsEnabled()

	fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
		notification.checkNotificationPermission(launcher)
	}

	fun notificationSettingsIntent(): Intent = notification.settingsIntent()

	fun setAlarmHour(hour: Int) {
		prefs.alarmHour.put(hour)
		rescheduleBackgroundChecks()
	}

	private fun rescheduleBackgroundChecks() {
		if (getEnableAlarm()) UpdatesWorker.launch(workManager) else UpdatesWorker.cancel(workManager)
	}

	fun setAbout() {
		state.value = SettingsUiState.About
	}

	fun setSettings() {
		state.value = SettingsUiState.Settings
	}

	fun copyAppList() = viewModelScope.launch(Dispatchers.IO) {
		appsRepository.getApps().collectLatest { apps ->
			apps.onSuccess {
				clipboard.copy(gson.toJson(it), "App List")
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.copy_app_list_success)))
			}
		}
	}

	fun copyAppLogs() = viewModelScope.launch(Dispatchers.IO) {
		runCatching {
			val process = ProcessBuilder("logcat", "-d")
				.redirectErrorStream(true)
				.start()
			try {
				val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
				process.waitFor()
				text
			} finally {
				process.destroy()
			}
		}.onSuccess { logs ->
			if (logs.isBlank()) {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.copy_logs_empty)))
			} else {
				clipboard.copy(logs, "App Logs")
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.copy_logs_success)))
			}
		}.onFailure {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.copy_logs_failed)))
		}
	}

	fun exportSettings(uri: android.net.Uri, context: android.content.Context) = viewModelScope.launch(Dispatchers.IO) {
		runCatching {
			val prefs = context.getSharedPreferences(context.getString(R.string.app_name), android.content.Context.MODE_PRIVATE)
			val jsonObject = org.json.JSONObject()
			for ((key, value) in prefs.all) {
				if (key in NON_PORTABLE_PREF_KEYS) continue
				jsonObject.put(key, value)
			}
			val jsonBytes = jsonObject.toString(4).toByteArray()
			val output = context.contentResolver.openOutputStream(uri)
				?: throw java.io.IOException("Could not open settings export destination")
			output.use {
				it.write(jsonBytes)
			}
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.export_success)))
		}.onFailure {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.export_failed)))
		}
	}

	fun importSettings(uri: android.net.Uri, context: android.content.Context) = viewModelScope.launch(Dispatchers.IO) {
		runCatching {
			val input = context.contentResolver.openInputStream(uri)
				?: throw java.io.IOException("Could not open settings import file")
			input.use { stream ->
				val json = stream.bufferedReader().readText()
				val jsonObject = org.json.JSONObject(json)
				val prefs = context.getSharedPreferences(context.getString(R.string.app_name), android.content.Context.MODE_PRIVATE)
				val editor = prefs.edit()
				val keys = jsonObject.keys()
				while (keys.hasNext()) {
					val key = keys.next()
					if (key in NON_PORTABLE_PREF_KEYS) continue
					val value = jsonObject.get(key)
					when (value) {
						is Boolean -> editor.putBoolean(key, value)
						is Int -> editor.putInt(key, value)
						is Long -> editor.putLong(key, value)
						is Float -> editor.putFloat(key, value)
						is String -> editor.putString(key, value)
					}
				}
				if (!editor.commit()) throw java.io.IOException("Could not persist imported settings")
				
				kotlinx.coroutines.withContext(Dispatchers.Main) {
					snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.import_success)))
					delay(1000)
					val pm = context.packageManager
					val intent = pm.getLaunchIntentForPackage(context.packageName)
					val componentName = intent?.component
					val restartIntent = android.content.Intent.makeRestartActivityTask(componentName)
					context.startActivity(restartIntent)
					Runtime.getRuntime().exit(0)
				}
			}
		}.onFailure {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.import_failed)))
		}
	}

	fun clearRuStoreCache() = viewModelScope.launch(Dispatchers.IO) {
		prefs.ruStore404Packages.put(emptyList())
		refreshRuStore404Count()
		refreshStoreNotFoundCacheCount()
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.clear_rustore_cache_success)))
	}

	fun clearStoreNotFoundCache() = viewModelScope.launch(Dispatchers.IO) {
		storeNotFoundCache.clearAll()
		prefs.ruStore404Packages.put(emptyList())
		refreshRuStore404Count()
		refreshStoreNotFoundCacheCount()
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.clear_store_cache_success)))
	}

	fun refreshRuStore404Count() {
		_ruStore404Count.value = prefs.ruStore404Packages.get().size
	}

	fun refreshStoreNotFoundCacheCount() {
		_storeNotFoundCacheCount.value = computeStoreNotFoundCacheCount()
	}

	private fun computeStoreNotFoundCacheCount(): Int =
		storeNotFoundCache.totalCount() + prefs.ruStore404Packages.get().size

	fun refreshUpdateMetrics() {
		_updateMetrics.value = UpdateMetrics(
			prefs.lastUpdateCheckDurationMs.get().takeIf { it > 0 },
			prefs.lastUpdateCheckTimestamp.get().takeIf { it > 0 },
			prefs.lastUpdateSourcesCount.get().takeIf { it > 0 }
		)
	}

    fun startMetricsAutoRefresh() {
        if (metricsJob?.isActive == true) return
        metricsJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshUpdateMetrics()
                delay(5_000)
            }
        }
    }

    fun stopMetricsAutoRefresh() {
        metricsJob?.cancel()
        metricsJob = null
    }

}

data class UpdateMetrics(
	val durationMs: Long? = null,
	val timestamp: Long? = null,
	val sources: Int? = null
)
