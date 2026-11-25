package com.apkupdateross.util

import androidx.compose.material3.SnackbarVisuals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class SnackBar {

    private val snackBars = MutableSharedFlow<SnackbarVisuals>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun flow(): SharedFlow<SnackbarVisuals> = snackBars

    fun snackBar(
        scope: CoroutineScope = appScope,
        message: SnackbarVisuals
    ) = scope.launch { snackBars.emit(message) }

}
