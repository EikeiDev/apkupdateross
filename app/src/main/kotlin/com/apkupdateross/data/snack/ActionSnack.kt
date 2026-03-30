package com.apkupdateross.data.snack

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals

class ActionSnack(
    override val message: String,
    override val actionLabel: String,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = true,
    val action: suspend () -> Unit
): SnackbarVisuals
