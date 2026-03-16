package com.apkupdateross.viewmodel

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import com.apkupdateross.R
import com.apkupdateross.data.git.CustomGitRepo
import com.apkupdateross.data.git.GitProvider
import com.apkupdateross.data.snack.TextSnack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import java.net.HttpURLConnection
import java.net.URL
import com.apkupdateross.data.ui.SettingsUiState
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.repository.AppsRepository
import com.apkupdateross.ui.theme.isDarkTheme
import com.apkupdateross.util.Clipboard
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.Themer
import com.apkupdateross.util.UpdatesNotification
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
import rikka.shizuku.Shizuku


class SettingsViewModel(
    private val prefs: Prefs,
    private val notification: UpdatesNotification,
    private val workManager: WorkManager,
	private val clipboard: Clipboard,
	private val appsRepository: AppsRepository,
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
	private val themer: Themer,
	private val snackBar: SnackBar,
	private val stringer: Stringer
) : ViewModel() {

	val state = MutableStateFlow<SettingsUiState>(SettingsUiState.Settings)
	private val _ruStore404Count = MutableStateFlow(prefs.ruStore404Packages.get().size)
	val ruStore404Count = _ruStore404Count.asStateFlow()
    private val _updateMetrics = MutableStateFlow(UpdateMetrics())
	val updateMetrics = _updateMetrics.asStateFlow()
    private val _customGitRepos = MutableStateFlow(prefs.customGitRepos.get())
    val customGitRepos = _customGitRepos.asStateFlow()
    private val _fdroidRepos = MutableStateFlow(prefs.fdroidRepos.get())
    val fdroidRepos = _fdroidRepos.asStateFlow()
    private var metricsJob: Job? = null

	// installModeAvailable[0]=Normal always true, [1]=Root, [2]=Shizuku
	val installModeAvailable = MutableStateFlow(listOf(true, false, false))

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
			val shizukuAvailable = runCatching {
				rikka.shizuku.Shizuku.pingBinder()
			}.getOrDefault(false)
			installModeAvailable.value = listOf(true, rootAvailable, shizukuAvailable)
		}
	}

	fun setPortraitColumns(n: Int) = prefs.portraitColumns.put(n)
	fun getPortraitColumns() = prefs.portraitColumns.get()
	fun setLandscapeColumns(n: Int) = prefs.landscapeColumns.put(n)
	fun getLandscapeColumns() = prefs.landscapeColumns.get()
	fun setPlayTextAnimations(b: Boolean) = prefs.playTextAnimations.put(b)
	fun getPlayTextAnimations() = prefs.playTextAnimations.get()
	fun setIgnoreAlpha(b: Boolean) = prefs.ignoreAlpha.put(b)
	fun getIgnoreAlpha() = prefs.ignoreAlpha.get()
	fun setIgnoreBeta(b: Boolean) = prefs.ignoreBeta.put(b)
	fun getIgnoreBeta() = prefs.ignoreBeta.get()
	fun setIgnorePreRelease(b: Boolean) = prefs.ignorePreRelease.put(b)
	fun getIgnorePreRelease() = prefs.ignorePreRelease.get()
	fun getUseApkMirror() = prefs.useApkMirror.get()
	fun setUseApkMirror(b: Boolean) = prefs.useApkMirror.put(b)
	fun getUseFdroid() = prefs.useFdroid.get()
	fun setUseFdroid(b: Boolean) = prefs.useFdroid.put(b)
	fun getUseGitHub() = prefs.useGitHub.get()
	fun setUseGitHub(b: Boolean) = prefs.useGitHub.put(b)
	fun getUseGitLab() = prefs.useGitLab.get()
	fun setUseGitLab(b: Boolean) = prefs.useGitLab.put(b)
	fun getUseAptoide() = prefs.useAptoide.get()
	fun setUseAptoide(b: Boolean) = prefs.useAptoide.put(b)
	fun getUseApkPure() = prefs.useApkPure.get()
	fun setUseApkPure(b: Boolean) = prefs.useApkPure.put(b)
	fun getUsePlay() = prefs.usePlay.get()
	fun setUsePlay(b: Boolean) = prefs.usePlay.put(b)
	fun getUseRuStore() = prefs.useRuStore.get()
	fun setUseRuStore(b: Boolean) = prefs.useRuStore.put(b)
	fun getAndroidTvUi() = prefs.androidTvUi.get()
	fun setAndroidTvUi(b: Boolean) = prefs.androidTvUi.put(b)
	fun getEnableAlarm() = prefs.enableAlarm.get()
	fun getGithubToken() = prefs.githubToken.get()
	fun setGithubToken(token: String) = prefs.githubToken.put(token.trim())
	fun getGitlabToken() = prefs.gitlabToken.get()
	fun setGitlabToken(token: String) = prefs.gitlabToken.put(token.trim())
	fun getRuStoreFilterThirdParty() = prefs.ruStoreFilterThirdParty.get()
	fun setRuStoreFilterThirdParty(b: Boolean) = prefs.ruStoreFilterThirdParty.put(b)
	fun getInstallMode() = prefs.installMode.get()
	fun getAlarmHour() = prefs.alarmHour.get()
	fun getAlarmFrequency() = prefs.alarmFrequency.get()
    fun getTheme() = prefs.theme.get()

    fun setTheme(theme: Int) {
        prefs.theme.put(theme)
        themer.setTheme(isDarkTheme(theme))
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
        prefs.customGitRepos.put(current)
        _customGitRepos.value = current
    }

    fun createEmptyCustomRepo(provider: GitProvider): CustomGitRepo = CustomGitRepo(platform = provider)

    fun removeCustomRepo(id: String) {
        val current = prefs.customGitRepos.get().toMutableList()
        val updated = current.filterNot { it.id == id }
        if (updated.size != current.size) {
            prefs.customGitRepos.put(updated)
            _customGitRepos.value = updated
        }
    }

    fun addOrUpdateFdroidRepo(repo: com.apkupdateross.data.fdroid.FdroidRepo) {
        val current = prefs.fdroidRepos.get().toMutableList()
        val index = current.indexOfFirst { it.id == repo.id }
        if (index >= 0) {
            current[index] = repo
        } else {
            current.add(repo)
        }
        prefs.fdroidRepos.put(current)
        _fdroidRepos.value = current
    }

    fun removeFdroidRepo(id: String) {
        val current = prefs.fdroidRepos.get()
        val repo = current.find { it.id == id }
        if (repo?.isDefault == true) {
            snackBar.snackBar(viewModelScope, TextSnack("Cannot remove default repository"))
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
				} else {
					prefs.installMode.put(0)
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
					refreshInstallModeAvailability()
				} else {
					Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
						override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
							if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
								prefs.installMode.put(if (grantResult == PackageManager.PERMISSION_GRANTED) 2 else 0)
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
			else -> prefs.installMode.put(0)
		}
	}

	companion object {
		private const val SHIZUKU_PERMISSION_REQUEST_CODE = 100
	}

	fun setAlarmFrequency(frequency: Int) {
		prefs.alarmFrequency.put(frequency)
		if (getEnableAlarm()) UpdatesWorker.launch(workManager) else UpdatesWorker.cancel(workManager)
	}

	fun setEnableAlarm(b: Boolean, launcher: ActivityResultLauncher<String>) {
		prefs.enableAlarm.put(b)
		if (b) {
			notification.checkNotificationPermission(launcher)
			UpdatesWorker.launch(workManager)
		} else {
			UpdatesWorker.cancel(workManager)
		}
	}

	fun areNotificationsEnabled(): Boolean = notification.areNotificationsEnabled()

	fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
		notification.checkNotificationPermission(launcher)
	}

	fun checkGithubToken() {
		viewModelScope.launch(Dispatchers.IO) {
			val token = prefs.githubToken.get().trim()
			if (token.isEmpty()) {
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.github_token_empty)))
				return@launch
			}
			val code = runCatching {
				val conn = (URL("https://api.github.com/rate_limit").openConnection() as HttpURLConnection).apply {
					requestMethod = "GET"
					connectTimeout = 8000
					readTimeout = 8000
					setRequestProperty("Authorization", "token $token")
				}
				conn.connect()
				val response = conn.responseCode
				conn.disconnect()
				response
			}.getOrElse { -1 }
			when (code) {
				in 200..299 -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.github_token_ok)))
				401 -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.github_token_invalid)))
				else -> snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.github_token_unknown, code)))
			}
		}
	}

	fun setAlarmHour(hour: Int) {
		prefs.alarmHour.put(hour)
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
			}
		}
	}

	fun copyAppLogs() = viewModelScope.launch(Dispatchers.IO) {
		val process = Runtime.getRuntime().exec("logcat -d")
		val data = process.inputStream.readBytes()
		clipboard.copy(data.decodeToString(), "App Logs")
	}

	fun clearRuStoreCache() = viewModelScope.launch(Dispatchers.IO) {
		prefs.ruStore404Packages.put(emptyList())
		refreshRuStore404Count()
		snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.clear_rustore_cache_success)))
	}

	private fun refreshRuStore404Count() {
		_ruStore404Count.value = prefs.ruStore404Packages.get().size
	}

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
