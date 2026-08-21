package com.apkupdateross.util

import com.apkupdateross.prefs.Prefs
import com.apkupdateross.ui.theme.AppThemeState
import com.apkupdateross.ui.theme.CustomThemeColors
import com.apkupdateross.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class Themer(private val prefs: Prefs) {

    private val theme = MutableStateFlow(readThemeState())

    fun flow() = theme

    fun refresh() {
        theme.value = readThemeState()
    }

    private fun readThemeState(): AppThemeState {
        val colors = CustomThemeColors(
            accentHex = prefs.customThemeAccent.get(),
            backgroundHex = prefs.customThemeBackground.get(),
            surfaceHex = prefs.customThemeSurface.get(),
            navigationHex = prefs.customThemeNavigation.get()
        )
        val mode = prefs.theme.get()
        return AppThemeState(
            mode = mode,
            darkTheme = isDarkTheme(mode, colors),
            customColors = colors
        )
    }

}
