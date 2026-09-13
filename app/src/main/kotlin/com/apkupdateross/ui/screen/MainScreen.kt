package com.apkupdateross.ui.screen

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.data.ui.Screen
import com.apkupdateross.ui.component.BadgeText
import com.apkupdateross.ui.theme.ApkTheme
import com.apkupdateross.ui.theme.AppTheme
import com.apkupdateross.ui.theme.LocalAppExtraColors
import com.apkupdateross.util.Badger
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SnackBar
import com.apkupdateross.util.Themer
import com.apkupdateross.viewmodel.AppsViewModel
import com.apkupdateross.viewmodel.MainViewModel
import com.apkupdateross.viewmodel.SearchViewModel
import com.apkupdateross.viewmodel.SettingsViewModel
import com.apkupdateross.viewmodel.UpdatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import kotlin.coroutines.CoroutineContext


@Composable
fun MainScreen(mainViewModel: MainViewModel = koinViewModel()) {
	// ViewModels
	val appsViewModel: AppsViewModel = koinViewModel()
	val updatesViewModel: UpdatesViewModel = koinViewModel()
	val searchViewModel: SearchViewModel = koinViewModel()
	val settingsViewModel: SettingsViewModel = koinViewModel()

	// Navigation
	val navController = rememberNavController()

	// Load apps immediately on start for badge numbers
	LaunchedEffect(Unit) {
		appsViewModel.refresh()
	}

	// Used to launch the install intent and get dismissal result
	val installLog = koinInject<InstallLog>()
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		if (it.resultCode == RESULT_CANCELED) {
			installLog.cancelCurrentInstall()
		}
	}

	// Check intent when cold starting from notification
	CheckNotificationIntent(mainViewModel, updatesViewModel, navController, launcher)

	// Check notification intent when hot starting
	IntentListener(mainViewModel, updatesViewModel, navController, launcher)

	// Theme
	val theme = koinInject<Themer>().flow().collectAsStateWithLifecycle().value

	// SnackBar
	val snackBarHostState = handleSnackBar()

	AppTheme(theme) {
		ApplySystemBarColors()
		DebugReleaseMigrationDialog(mainViewModel)
		Scaffold(
			snackbarHost = { SnackbarHost(snackBarHostState) },
			bottomBar = { BottomBar(mainViewModel, navController) },
			containerColor = ApkTheme.colors.background,
			contentWindowInsets = WindowInsets(0)
		) { padding ->
			NavHost(navController, padding, mainViewModel, appsViewModel, updatesViewModel, searchViewModel, settingsViewModel)
		}
	}
}

@Composable
private fun DebugReleaseMigrationDialog(mainViewModel: MainViewModel) {
	var visible by rememberSaveable { mutableStateOf(mainViewModel.shouldShowDebugReleaseWarning()) }
	if (!visible) return

	val uriHandler = LocalUriHandler.current
	val releasesUrl = "${stringResource(R.string.github_url)}/releases/latest"
	fun dismiss() {
		mainViewModel.dismissDebugReleaseWarning()
		visible = false
	}

	AlertDialog(
		onDismissRequest = { dismiss() },
		title = { Text(stringResource(R.string.debug_release_warning_title)) },
		text = {
			Text(
				stringResource(
					R.string.debug_release_warning_message,
					BuildConfig.VERSION_NAME,
					BuildConfig.VERSION_CODE
				)
			)
		},
		confirmButton = {
			Button(
				onClick = {
					dismiss()
					uriHandler.openUri(releasesUrl)
				}
			) {
				Text(stringResource(R.string.debug_release_warning_open_release))
			}
		},
		dismissButton = {
			TextButton(onClick = { dismiss() }) {
				Text(stringResource(R.string.debug_release_warning_understood))
			}
		}
	)
}

@Suppress("DEPRECATION")
@Composable
private fun ApplySystemBarColors() {
	val activity = LocalContext.current as? ComponentActivity ?: return
	val statusBarColor = ApkTheme.colors.background
	val navigationBarColor = LocalAppExtraColors.current.navigationBar ?: ApkTheme.colors.background

	SideEffect {
		activity.window.statusBarColor = Color.Transparent.toArgb()
		activity.window.navigationBarColor = navigationBarColor.toArgb()
		WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
			isAppearanceLightStatusBars = statusBarColor.luminance() > 0.5f
			isAppearanceLightNavigationBars = navigationBarColor.luminance() > 0.5f
		}
	}
}

@Composable
fun handleSnackBar(): SnackbarHostState {
	val snackBarHostState = remember { SnackbarHostState() }
	koinInject<SnackBar>().flow().CollectAsEffect(Dispatchers.IO) { snack ->
		val result = snackBarHostState.showSnackbar(
			message = snack.message,
			actionLabel = snack.actionLabel,
			duration = snack.duration,
			withDismissAction = snack.withDismissAction
		)
		if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
			if (snack is com.apkupdateross.data.snack.ActionSnack) {
				snack.action()
			}
		}
	}
	return snackBarHostState
}

@Composable
fun <T> Flow<T>.CollectAsEffect(
	context: CoroutineContext = Dispatchers.IO,
	block: suspend (T) -> Unit
) = LaunchedEffect(Unit) {
	onEach(block).flowOn(context).launchIn(this)
}

@Composable
fun IntentListener(
	mainViewModel: MainViewModel,
	updatesViewModel: UpdatesViewModel,
	navController: NavController,
	launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
	val activity = LocalContext.current as? ComponentActivity ?: return
	DisposableEffect(Unit) {
		val listener = Consumer<Intent> {
			mainViewModel.processIntent(it, launcher, updatesViewModel, navController)
		}
		activity.addOnNewIntentListener(listener)
		onDispose { activity.removeOnNewIntentListener(listener) }
	}
}

@Composable
fun CheckNotificationIntent(
	mainViewModel: MainViewModel,
	updatesViewModel: UpdatesViewModel,
	navController: NavController,
	launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
	val activity = LocalContext.current as? ComponentActivity ?: return
	LaunchedEffect(activity.intent) {
		mainViewModel.processIntent(activity.intent, launcher, updatesViewModel, navController)
	}
}

@Composable
fun BottomBar(mainViewModel: MainViewModel, navController: NavController) = Box(
	modifier = Modifier
		.fillMaxWidth()
		.navigationBarsPadding()
		.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
) {
	val badges = koinInject<Badger>().flow().collectAsStateWithLifecycle().value
	val extraColors = LocalAppExtraColors.current
	val navigationBarColor = extraColors.navigationBar ?: ApkTheme.colors.surfaceElevated
	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = ApkTheme.shapes.lg,
		color = navigationBarColor,
		tonalElevation = 0.dp,
		shadowElevation = 6.dp,
		border = BorderStroke(1.dp, ApkTheme.colors.divider)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 72.dp)
				.padding(horizontal = 6.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			mainViewModel.screens.forEach { screen ->
				val state = navController.currentBackStackEntryAsState().value
				val selected = state?.destination?.route  == screen.route
				BottomBarItem(
					mainViewModel = mainViewModel,
					navController = navController,
					screen = screen,
					selected = selected,
					badge = badges[screen.route].orEmpty(),
					modifier = Modifier.weight(1f)
				)
			}
		}
	}
}

@Composable
fun BottomBarItem(
	mainViewModel: MainViewModel,
    navController: NavController,
    screen: Screen,
    selected: Boolean,
    badge: String,
	modifier: Modifier = Modifier
) {
	val extraColors = LocalAppExtraColors.current
	val navigationContentColor = extraColors.onNavigationBar ?: ApkTheme.colors.textSecondary
	val selectedContainer = extraColors.navigationIndicator ?: ApkTheme.colors.surfaceHighlight
	val selectedIconColor = extraColors.onNavigationIndicator ?: ApkTheme.colors.accent
	val selectedTextColor = extraColors.selectedNavigationText ?: ApkTheme.colors.accent
	val itemColor = if (selected) selectedTextColor else navigationContentColor.copy(alpha = 0.78f)
	Column(
		modifier = modifier
			.height(60.dp)
			.clip(ApkTheme.shapes.md)
			.clickable(
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = { mainViewModel.navigateTo(navController, screen.route) }
			)
			.padding(horizontal = 2.dp, vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Box(
			modifier = Modifier
				.size(width = 54.dp, height = 30.dp)
				.clip(ApkTheme.shapes.lg)
				.background(if (selected) selectedContainer else Color.Transparent),
			contentAlignment = Alignment.Center
		) {
			BadgedBox({ BadgeText(badge) }) {
				Icon(
					imageVector = if (selected) screen.iconSelected else screen.icon,
					contentDescription = null,
					modifier = Modifier.size(21.dp),
					tint = if (selected) selectedIconColor else itemColor
				)
			}
		}
		Text(
			stringResource(screen.resourceId),
			color = itemColor,
			style = MaterialTheme.typography.labelSmall,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

@Composable
fun NavHost(
	navController: NavHostController,
	padding: PaddingValues,
	mainViewModel: MainViewModel,
	appsViewModel: AppsViewModel,
	updatesViewModel: UpdatesViewModel,
	searchViewModel: SearchViewModel,
	settingsViewModel: SettingsViewModel
) = NavHost(
	navController = navController,
	startDestination = mainViewModel.getLastRoute(),
	modifier = Modifier.padding(padding)
) {
	composable(Screen.Apps.route) { AppsScreen(appsViewModel) }
	composable(Screen.Search.route) { SearchScreen(searchViewModel) }
	composable(Screen.Updates.route) { UpdatesScreen(updatesViewModel) }
	composable(Screen.Settings.route) { SettingsScreen(settingsViewModel) }
}
