package com.apkupdateross.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apkupdateross.ui.screen.MainScreen
import com.apkupdateross.ui.component.TvModeProvider
import com.apkupdateross.util.ApkMirrorChallengeCoordinator
import org.koin.android.ext.android.inject


class MainActivity : ComponentActivity() {
	private val apkMirrorChallengeCoordinator: ApkMirrorChallengeCoordinator by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			TvModeProvider {
				MainScreen()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		apkMirrorChallengeCoordinator.attach(this)
	}

	override fun onPause() {
		apkMirrorChallengeCoordinator.detach(this)
		super.onPause()
	}
}
