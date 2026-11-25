package com.apkupdateross.viewmodel

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdateross.BuildConfig
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.apkupdateross.R
import com.apkupdateross.data.git.extractSharedRepoUrl
import com.apkupdateross.data.snack.TextSnack
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.data.ui.Screen
import com.apkupdateross.data.ui.classifyInstallFailure
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.LocalPackageInstaller
import com.apkupdateross.util.SessionInstaller
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Stringer
import com.apkupdateross.util.UpdatesNotification
import com.apkupdateross.util.getAppId
import com.apkupdateross.util.getIntentExtra
import com.apkupdateross.util.orFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MainViewModel(
	private val prefs: Prefs,
	private val installLog: InstallLog,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	private val localPackageInstaller: LocalPackageInstaller
) : ViewModel() {

	val screens = listOf(Screen.Apps, Screen.Search, Screen.Updates, Screen.Settings)

	val isRefreshing = MutableStateFlow(false)
	private val _sharedRepoUrl = MutableStateFlow<String?>(null)
	val sharedRepoUrl = _sharedRepoUrl.asStateFlow()
	private val _pendingLocalPackageUri = MutableStateFlow<Uri?>(null)
	val pendingLocalPackageUri = _pendingLocalPackageUri.asStateFlow()

	fun consumeSharedRepoUrl() {
		_sharedRepoUrl.value = null
	}

	fun requestLocalPackageInstall(uri: Uri) {
		_pendingLocalPackageUri.value = uri
	}

	fun confirmLocalPackageInstall(uri: Uri) {
		_pendingLocalPackageUri.value = null
		viewModelScope.launch(Dispatchers.IO) {
			localPackageInstaller.installFromUri(uri)
		}
	}

	fun dismissLocalPackageInstall() {
		_pendingLocalPackageUri.value = null
	}

	fun refresh(
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) = viewModelScope.launch {
		isRefreshing.value = true
		appsViewModel.refresh(false)
		updatesViewModel.refresh(false)?.invokeOnCompletion {
			isRefreshing.value = false
		}
	}

	fun processIntent(
		intent: Intent,
		launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
		updatesViewModel: UpdatesViewModel,
		navController: NavController
	) {
		when {
			intent.action == UpdatesNotification.UpdateAction -> processUpdateIntent(navController, updatesViewModel)
			intent.action == Intent.ACTION_SEND -> processSharedTextIntent(intent, navController)
			intent.action == Intent.ACTION_VIEW && intent.data != null -> {
				_pendingLocalPackageUri.value = intent.data
			}
			intent.action?.contains(SessionInstaller.INSTALL_ACTION).orFalse() -> processInstallIntent(intent, launcher)
			else -> {}
		}
	}

	private fun processSharedTextIntent(
		intent: Intent,
		navController: NavController
	) {
		val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
		val repoUrl = extractSharedRepoUrl(sharedText)
		if (repoUrl != null) {
			_sharedRepoUrl.value = repoUrl
			navigateTo(navController, Screen.Settings.route)
		} else {
			snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.shared_repo_invalid_url)))
		}
	}

	fun navigateTo(navController: NavController, route: String) = navController.navigate(route) {
		popUpTo(navController.graph.findStartDestination().id) { saveState = true }
		launchSingleTop = true
		restoreState = true
		prefs.lastTab.put(route)
	}

	fun getLastRoute(): String {
		val saved = prefs.lastTab.get()
		return saved.takeIf { route -> screens.any { it.route == route } } ?: Screen.Updates.route
	}

	fun shouldShowDebugReleaseWarning(): Boolean =
		BuildConfig.DEBUG && prefs.lastDebugReleaseWarningVersionCode.get() < BuildConfig.VERSION_CODE

	fun dismissDebugReleaseWarning() {
		if (BuildConfig.DEBUG) {
			prefs.lastDebugReleaseWarningVersionCode.put(BuildConfig.VERSION_CODE.toLong())
		}
	}

	private fun processInstallIntent(
		intent: Intent,
		launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
	) = viewModelScope.launch(Dispatchers.Main) {
		when (intent.extras?.getInt(PackageInstaller.EXTRA_STATUS)) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				intent.getIntentExtra()?.let {
					it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
					launcher.launch(it)
				}
			}
			PackageInstaller.STATUS_SUCCESS -> {
				intent.getAppId()?.let {
					installLog.emitStatus(AppInstallStatus(true, it))
				}
			}
			else -> {
				val status = intent.extras?.getInt(PackageInstaller.EXTRA_STATUS) ?: PackageInstaller.STATUS_FAILURE
				val message = intent.extras?.getString(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
				val reason = classifyInstallFailure(status, message)
				intent.getAppId()?.let {
					installLog.emitStatus(
						AppInstallStatus(
							success = false,
							id = it,
							snack = reason != InstallFailureReason.CANCELLED,
							reason = reason,
							message = message
						)
					)
				}
				Log.e("MainViewModel", "Failed to install app ($reason): $message $intent")
			}
		}
	}

	private fun processUpdateIntent(
		navController: NavController,
		updatesViewModel: UpdatesViewModel
	) {
		navigateTo(navController, Screen.Updates.route)
		updatesViewModel.refresh()
	}

}
