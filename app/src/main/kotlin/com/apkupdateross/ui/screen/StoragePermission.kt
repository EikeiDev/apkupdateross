package com.apkupdateross.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun rememberStoragePermissionAction(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentAction by rememberUpdatedState(action)
    var runAfterPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && runAfterPermission) currentAction()
        runAfterPermission = false
    }

    return remember(context, launcher) {
        {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                runAfterPermission = true
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                currentAction()
            }
        }
    }
}
